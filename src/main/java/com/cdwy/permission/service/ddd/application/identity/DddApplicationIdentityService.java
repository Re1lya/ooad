package com.cdwy.permission.service.ddd.application.identity;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository.Link;
import com.cdwy.permission.service.port.auth.CredentialGateway;
import com.cdwy.permission.service.port.data.IdentityData;
import com.cdwy.permission.service.port.data.PermissionData;
import com.cdwy.permission.service.port.data.UserData;
import com.cdwy.permission.service.port.error.ResourceNotFoundException;
import com.cdwy.permission.service.port.identity.ApplicationIdentityUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DDD 应用服务：只负责加载聚合与编排，规则由领域对象承担。
 *
 * <ul>
 *   <li>{@link AuthorizationSnapshot}：展开角色继承、计算有效角色与应用内有效权限（信息专家）；
 *   <li>{@link ManagedSystem}：自身是否启用；{@link Account}：账号是否可用；
 *   <li>{@link ProtectedResource}：资源自己判断能否提供后端权限码、能否被调用；
 *   <li>目录查询经 {@link DepartmentDirectory}、{@link AccountRepository} 端口在数据库分页。
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddApplicationIdentityService implements ApplicationIdentityUseCase {
  private static final Logger log = LoggerFactory.getLogger(DddApplicationIdentityService.class);

  private final AccountRepository accounts;
  private final RoleCatalog roles;
  private final PermissionCatalog permissions;
  private final SystemCatalog systems;
  private final ResourceCatalog resources;
  private final AssignmentRepository assignments;
  private final DepartmentDirectory departments;
  private final CredentialGateway credentials;

  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  /** 加载快照后交给领域对象计算；应用停用或账号失效时不返回任何角色与权限。 */
  @Override
  public IdentityData.Current current(Long userId, String systemCode) {
    ManagedSystem app = system(systemCode);
    Account account = account(userId);
    if (!usable(app, account))
      return new IdentityData.Current(
          account.getUsername(),
          List.of(),
          List.of(),
          account.getDisplayName(),
          account.getUsername());

    AuthorizationSnapshot snapshot = snapshot();
    List<Long> assigned = assignments.rolesOf(userId);
    List<String> roleCodes =
        snapshot.effectiveRoles(assigned).stream().map(AccessRole::getCode).toList();
    List<PermissionData.View> granted = snapshot.grantedIn(app.getId(), assigned);
    Set<Long> grantedIds = new HashSet<>();
    Set<String> codes = new LinkedHashSet<>();
    for (PermissionData.View view : granted) {
      grantedIds.add(view.id());
      codes.add(view.code());
    }
    List<Link> bindings = assignments.bindings();
    for (ProtectedResource resource : resources.all())
      if (resource.getSystemId().equals(app.getId())
          && bindings.stream()
              .anyMatch(b -> b.right().equals(resource.getId()) && grantedIds.contains(b.left())))
        resource.authorityCode().ifPresent(codes::add);
    log.info(
        "接入应用 {} 查询身份：用户 {} 角色 {} 个、权限码 {} 个",
        systemCode,
        account.getUsername(),
        roleCodes.size(),
        codes.size());
    return new IdentityData.Current(
        account.getUsername(),
        roleCodes,
        List.copyOf(codes),
        account.getDisplayName(),
        account.getUsername());
  }

  /** 按登录名查询人员，工号沿用登录名。 */
  @Override
  public IdentityData.User lookup(String systemCode, String userId) {
    system(systemCode);
    Account account =
        accounts
            .username(userId)
            .orElseThrow(
                () -> new ResourceNotFoundException("IDENTITY_USER_NOT_FOUND", "统一身份认证系统中没有找到该用户"));
    return new IdentityData.User(
        account.getUsername(), account.getUsername(), account.getDisplayName());
  }

  /** 由快照给出应用内有效前端权限，再看是否包含该权限码。 */
  @Override
  public boolean permitted(Long userId, String systemCode, String permission) {
    ManagedSystem app = system(systemCode);
    Account account = account(userId);
    return usable(app, account)
        && snapshot().grantedIn(app.getId(), assignments.rolesOf(userId)).stream()
            .anyMatch(view -> view.code().equals(permission));
  }

  /** 资源由 operationId 定位，是否可调用交给 {@link ProtectedResource#callableBy}。 */
  @Override
  public IdentityData.ApiCheck apiPermission(Long userId, String systemCode, String operationId) {
    ManagedSystem app = system(systemCode);
    ProtectedResource resource =
        resources.all().stream()
            .filter(r -> r.getSystemId().equals(app.getId()))
            .filter(r -> operationId.equals(r.getOperationId()))
            .findFirst()
            .orElseThrow(
                () -> new ResourceNotFoundException("API_OPERATION_NOT_FOUND", "没有找到对应的 API 操作"));
    Account account = account(userId);
    if (!usable(app, account)) return new IdentityData.ApiCheck(operationId, false);
    Set<Long> granted = new HashSet<>();
    snapshot()
        .grantedIn(app.getId(), assignments.rolesOf(userId))
        .forEach(view -> granted.add(view.id()));
    List<Long> bound =
        assignments.bindings().stream()
            .filter(b -> b.right().equals(resource.getId()))
            .map(Link::left)
            .toList();
    return new IdentityData.ApiCheck(operationId, resource.callableBy(granted, bound));
  }

  /** 一层部门经端口分页读取，再批量判断是否有子部门。 */
  @Override
  public IdentityData.Page<IdentityData.Department> departments(
      String systemCode, Long parentId, IdentityData.Query query) {
    system(systemCode);
    if (parentId != null && departments.find(parentId).isEmpty()) throw missingDepartment();
    UserData.Page<DepartmentNode> rows =
        departments.level(parentId, query.keyword(), query.page() - 1, query.pageSize());
    Set<Long> parents =
        departments.withChildren(rows.items().stream().map(DepartmentNode::getId).toList());
    return new IdentityData.Page<>(
        query.page(),
        query.pageSize(),
        rows.total(),
        rows.items().stream()
            .map(
                n ->
                    new IdentityData.Department(
                        n.getId(),
                        n.getCode(),
                        n.getName(),
                        n.getParentId(),
                        parents.contains(n.getId())))
            .toList());
  }

  /** 部门存在时经端口分页读取成员。 */
  @Override
  public IdentityData.Page<IdentityData.DepartmentUser> departmentUsers(
      String systemCode, Long departmentId, IdentityData.Query query) {
    system(systemCode);
    DepartmentNode department = departments.find(departmentId).orElseThrow(this::missingDepartment);
    UserData.Page<Account> rows =
        accounts.members(departmentId, query.keyword(), query.page() - 1, query.pageSize());
    return new IdentityData.Page<>(
        query.page(),
        query.pageSize(),
        rows.total(),
        rows.items().stream()
            .map(
                a ->
                    new IdentityData.DepartmentUser(
                        a.getUsername(),
                        a.getUsername(),
                        a.getDisplayName(),
                        department.getId(),
                        department.getName()))
            .toList());
  }

  /** 角色、权限、应用与关联的当前快照；角色权限不写进 JWT。 */
  private AuthorizationSnapshot snapshot() {
    return new AuthorizationSnapshot(
        roles.all(),
        permissions.all(),
        systems.all(),
        assignments.hierarchy(),
        assignments.rolePermissions());
  }

  private boolean usable(ManagedSystem app, Account account) {
    return app.getEnabled() && account.usable(credentials.now());
  }

  private Account account(Long id) {
    return accounts.find(id).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
  }

  private ManagedSystem system(String code) {
    return systems.all().stream()
        .filter(app -> app.getCode().equals(code))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("SYSTEM_NOT_FOUND", "接入应用不存在"));
  }

  private ResourceNotFoundException missingDepartment() {
    return new ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "部门不存在");
  }
}
