package com.cdwy.permission.service.identity;

import com.cdwy.permission.entity.organization.Department;
import com.cdwy.permission.entity.permission.Permission;
import com.cdwy.permission.entity.rbac.PermissionApi;
import com.cdwy.permission.entity.rbac.UserRole;
import com.cdwy.permission.entity.resource.ApiResource;
import com.cdwy.permission.entity.system.BusinessSystem;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.LikePattern;
import com.cdwy.permission.repository.organization.DepartmentRepository;
import com.cdwy.permission.repository.rbac.PermissionApiRepository;
import com.cdwy.permission.repository.rbac.UserRoleRepository;
import com.cdwy.permission.repository.resource.ApiResourceRepository;
import com.cdwy.permission.repository.system.BusinessSystemRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.data.IdentityData;
import com.cdwy.permission.service.port.data.AuditData;
import com.cdwy.permission.service.port.error.ResourceNotFoundException;
import com.cdwy.permission.service.port.error.ForbiddenException;
import com.cdwy.permission.service.port.identity.ApplicationIdentityUseCase;
import com.cdwy.permission.service.rbac.AuthorizationService;
import com.cdwy.permission.service.rbac.RoleHierarchyService;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 结构化事务脚本：按“读应用 → 读用户 → 展开角色继承 → 汇总应用内权限码 → 追加绑定 API 权限码”的步骤计算接入应用身份；
 * 目录查询直接用 Spring Data 分页查询。
 *
 * <p>数据（JPA 实体）与处理过程分离，步骤集中在服务方法中，直接使用 Spring Data Repository。
 */
@Service
@ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationIdentityService implements ApplicationIdentityUseCase {
  private static final Logger log = LoggerFactory.getLogger(ApplicationIdentityService.class);

  private final BusinessSystemRepository systems;
  private final UserAccountRepository users;
  private final UserRoleRepository userRoles;
  private final PermissionApiRepository bindings;
  private final ApiResourceRepository resources;
  private final DepartmentRepository departments;
  private final RoleHierarchyService hierarchy;
  private final AuthorizationService authorization;
  private final com.cdwy.permission.service.port.audit.AuditTrailUseCase audit;

  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  /** 步骤 1-5：应用 → 用户 → 有效角色 → 应用内前端权限码 → 绑定的后端 API 权限码。 */
  @Override
  public IdentityData.Current current(Long userId, String systemCode) {
    BusinessSystem app = requireSystem(systemCode);
    UserAccount user = requireUser(userId);

    // 步骤 3：直接分配角色 + 沿继承链可达的启用角色；停用角色阻断经过它的路径。
    Set<String> roles = new LinkedHashSet<>();
    Map<Long, List<com.cdwy.permission.entity.role.Role>> graph = hierarchy.graph();
    for (UserRole grant : userRoles.findAllByUser_Id(userId))
      for (RoleHierarchyService.ResolvedRole resolved : hierarchy.resolve(grant.getRole(), graph))
        roles.add(resolved.role().getCode());

    // 步骤 4、5：只汇总本应用的有效前端权限，以及它们绑定的启用且登记了权限码的 API 资源。
    Set<String> codes = new LinkedHashSet<>();
    if (usable(app, user)) {
      List<Permission> granted = granted(userId, app);
      granted.forEach(p -> codes.add(p.getCode()));
      for (Permission permission : granted)
        for (PermissionApi binding : bindings.findAllByPermission_Id(permission.getId())) {
          ApiResource resource = binding.getResource();
          if (resource.getActive()
              && resource.getCode() != null
              && resource.getSystem().getId().equals(app.getId())) codes.add(resource.getCode());
        }
    } else roles.clear();
    log.info(
        "接入应用 {} 查询身份：用户 {} 角色 {} 个、权限码 {} 个",
        systemCode,
        user.getUsername(),
        roles.size(),
        codes.size());
    return new IdentityData.Current(
        user.getUsername(),
        List.copyOf(roles),
        List.copyOf(codes),
        user.getDisplayName(),
        user.getUsername());
  }

  /** 应用存在时按登录名查询人员；工号沿用登录名。 */
  @Override
  public IdentityData.User lookup(String systemCode, String userId) {
    requireSystem(systemCode);
    UserAccount user =
        users
            .findByUsername(userId)
            .orElseThrow(
                () -> new ResourceNotFoundException("IDENTITY_USER_NOT_FOUND", "统一身份认证系统中没有找到该用户"));
    return new IdentityData.User(user.getUsername(), user.getUsername(), user.getDisplayName());
  }

  /** 步骤：应用 → 用户 → 应用内有效前端权限中是否有该权限码。 */
  @Override
  public boolean permitted(Long userId, String systemCode, String permission) {
    BusinessSystem app = requireSystem(systemCode);
    UserAccount user = requireUser(userId);
    return usable(app, user)
        && granted(userId, app).stream().anyMatch(p -> p.getCode().equals(permission));
  }

  /** 步骤：按 operationId 找资源 → 资源启用 → 资源绑定的前端权限是否在用户有效权限中。 */
  @Override
  public IdentityData.ApiCheck apiPermission(Long userId, String systemCode, String operationId) {
    BusinessSystem app = requireSystem(systemCode);
    ApiResource resource =
        resources
            .findBySystem_CodeAndOperationId(systemCode, operationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("API_OPERATION_NOT_FOUND", "没有找到对应的 API 操作"));
    UserAccount user = requireUser(userId);
    boolean allowed = false;
    if (usable(app, user) && resource.getActive()) {
      Set<Long> granted = new HashSet<>();
      granted(userId, app).forEach(p -> granted.add(p.getId()));
      allowed =
          bindings.findAllByResource_Id(resource.getId()).stream()
              .anyMatch(b -> granted.contains(b.getPermission().getId()));
    }
    return new IdentityData.ApiCheck(operationId, allowed);
  }

  /** 步骤：上级存在 → 数据库分页取一层 → 批量判断是否有子部门。 */
  @Override
  public IdentityData.Page<IdentityData.Department> departments(
      String systemCode, Long parentId, IdentityData.Query query) {
    requireSystem(systemCode);
    if (parentId != null && !departments.existsById(parentId)) throw missingDepartment();
    Page<Department> rows =
        departments.level(
            parentId,
            LikePattern.of(query.keyword()),
            PageRequest.of(query.page() - 1, query.pageSize(), Sort.by("code", "id")));
    List<Long> ids = rows.map(Department::getId).getContent();
    Set<Long> parents = ids.isEmpty() ? Set.of() : new HashSet<>(departments.parentsAmong(ids));
    return new IdentityData.Page<>(
        query.page(),
        query.pageSize(),
        rows.getTotalElements(),
        rows.map(
                d ->
                    new IdentityData.Department(
                        d.getId(),
                        d.getCode(),
                        d.getName(),
                        d.getParent() == null ? null : d.getParent().getId(),
                        parents.contains(d.getId())))
            .getContent());
  }

  /** 步骤：部门存在 → 数据库分页取该部门用户（按工号排序）。 */
  @Override
  public IdentityData.Page<IdentityData.DepartmentUser> departmentUsers(
      String systemCode, Long departmentId, IdentityData.Query query) {
    requireSystem(systemCode);
    Department department = departments.findById(departmentId).orElseThrow(this::missingDepartment);
    Page<UserAccount> rows =
        users.members(
            departmentId,
            LikePattern.of(query.keyword()),
            PageRequest.of(query.page() - 1, query.pageSize(), Sort.by("username")));
    return new IdentityData.Page<>(
        query.page(),
        query.pageSize(),
        rows.getTotalElements(),
        rows.map(
                u ->
                    new IdentityData.DepartmentUser(
                        u.getUsername(),
                        u.getUsername(),
                        u.getDisplayName(),
                        department.getId(),
                        department.getName()))
            .getContent());
  }

  /** 应用启用且账号可用时才有任何授权。 */
  private static boolean usable(BusinessSystem app, UserAccount user) {
    return app.getEnabled() && user.usable(Instant.now());
  }

  /** 用户在该应用内的有效前端权限（菜单与按钮）。 */
  private List<Permission> granted(Long userId, BusinessSystem app) {
    return authorization.effective(userId).stream()
        .filter(p -> p.getSystem().getId().equals(app.getId()))
        .toList();
  }

  /** 步骤：应用存在 → 管理员直接放行，否则须在本应用内至少拥有一项有效权限 → 查询。 */
  @Override
  public AuditData.Page auditLogs(
      Long userId, boolean administrator, String systemCode, AuditData.Query query) {
    BusinessSystem app = requireSystem(systemCode);
    if (!administrator && granted(userId, app).isEmpty())
      throw new ForbiddenException("当前用户在该应用内没有任何有效权限，不能查看审计日志");
    return audit.query(systemCode, query);
  }

  /** 步骤：应用存在 → 账号存在 → 写入（操作人快照由审计服务记录）。 */
  @Override
  @Transactional
  public AuditData.Entry writeAuditLog(Long userId, String systemCode, AuditData.Write write) {
    requireSystem(systemCode);
    requireUser(userId);
    return audit.write(systemCode, userId, write);
  }

  private UserAccount requireUser(Long id) {
    return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "用户不存在"));
  }

  private BusinessSystem requireSystem(String code) {
    return systems
        .findByCode(code)
        .orElseThrow(() -> new ResourceNotFoundException("SYSTEM_NOT_FOUND", "接入应用不存在"));
  }

  private ResourceNotFoundException missingDepartment() {
    return new ResourceNotFoundException("IDENTITY_DEPARTMENT_NOT_FOUND", "部门不存在");
  }
}
