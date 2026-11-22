package com.cdwy.permission.service.ddd.application.rbac;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository.Link;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddAuthorizationService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddAuthorizationService implements AuthorizationUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final AccountRepository accounts;
  private final RoleCatalog roles;
  private final PermissionCatalog permissions;
  private final SystemCatalog systems;
  private final ResourceCatalog resources;
  private final AssignmentRepository assignments;
  private final com.cdwy.permission.service.port.auth.CredentialGateway credentials;
  private final com.cdwy.permission.service.port.rbac.ResourcePathMatcher matcher;

  /** 当前账号。 */
  private Account user(Long id) {
    return accounts.find(id).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "用户不存在"));
  }

  /** 当前角色。 */
  private AccessRole role(Long id) {
    return roles.find(id).orElseThrow(() -> new ResourceNotFoundException("ROLE_NOT_FOUND", "角色不存在"));
  }

  /** 当前权限。 */
  private FrontendPermission permission(Long id) {
    return permissions.find(id).orElseThrow(() -> new ResourceNotFoundException("PERMISSION_NOT_FOUND", "前端权限不存在"));
  }

  /** 当前领域快照，角色权限不会写进 JWT。 */
  private AuthorizationSnapshot snapshot() {
    return new AuthorizationSnapshot(
        roles.all(),
        permissions.all(),
        systems.all(),
        assignments.hierarchy(),
        assignments.rolePermissions());
  }

  /** 用户显式分配角色；按用户查询，一万名用户时不加载全部分配关系。 */
  private List<Long> assigned(Long id) {
    return assignments.rolesOf(id);
  }

  /** 失效主体不新增授权。 */
  @Transactional
  public void grantRole(Long userId, Long roleId) {
    Account a = user(userId);
    AccessRole r = role(roleId);
    if (!a.usable(credentials.now()) || !r.getEnabled())
      throw new InvalidDataException("GRANT_TARGET_INACTIVE", "不能给失效账号或停用角色新增授权");
    assignments.addUserRole(userId, roleId);
  }

  /** 撤权只删除显式关系。 */
  @Transactional
  public void revokeRole(Long userId, Long roleId) {
    user(userId);
    role(roleId);
    assignments.removeUserRole(userId, roleId);
  }

  /** 新授权必须处于有效应用与祖先链。 */
  @Transactional
  public void grantPermission(Long roleId, Long permissionId) {
    AccessRole r = role(roleId);
    permission(permissionId);
    if (!r.getEnabled() || !snapshot().directory().usable(permissionId))
      throw new InvalidDataException("GRANT_TARGET_INACTIVE", "不能新增失效角色或权限的授权");
    assignments.addRolePermission(roleId, permissionId);
  }

  /** 撤回显式角色权限。 */
  @Transactional
  public void revokePermission(Long roleId, Long permissionId) {
    role(roleId);
    permission(permissionId);
    assignments.removeRolePermission(roleId, permissionId);
  }

  /** 展示显式分配角色。 */
  public List<RoleData.View> roles(Long id) {
    user(id);
    return assigned(id).stream()
        .map(this::role)
        .map(
            r ->
                new RoleData.View(
                    r.getId(), r.getCode(), r.getName(), r.getDescription(), r.getEnabled()))
        .toList();
  }

  /** 展示显式角色权限。 */
  public List<PermissionData.View> rolePermissions(Long id) {
    role(id);
    PermissionDirectory directory = snapshot().directory();
    return assignments.rolePermissions().stream()
        .filter(e -> e.left().equals(id))
        .map(e -> directory.view(e.right()))
        .toList();
  }

  /** 失效用户的有效权限为空。 */
  public List<HierarchyData.PermissionOrigin> origins(Long id) {
    if (!user(id).usable(credentials.now())) return List.of();
    return snapshot().origins(assigned(id));
  }

  /** 单角色继承预览。 */
  public List<HierarchyData.PermissionOrigin> roleOrigins(Long id) {
    role(id);
    return snapshot().origins(List.of(id));
  }

  /** 由来源结果取得权限并集。 */
  public List<PermissionData.View> effectiveViews(Long id) {
    return origins(id).stream().map(HierarchyData.PermissionOrigin::permission).toList();
  }

  /** 只给导航补祖先，不扩大真实授权。 */
  public List<PermissionData.Tree> menu(Long id) {
    return snapshot()
        .directory()
        .tree(effectiveViews(id).stream().map(PermissionData.View::id).toList());
  }

  /** 只取该应用的有效权限组成导航树；应用由编码定位。 */
  public List<PermissionData.Tree> menu(Long id, String systemCode) {
    ManagedSystem app =
        systems.all().stream()
            .filter(a -> a.getCode().equals(systemCode))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("SYSTEM_NOT_FOUND", "接入应用不存在"));
    return snapshot()
        .directory()
        .tree(
            effectiveViews(id).stream()
                .filter(view -> view.systemId().equals(app.getId()))
                .map(PermissionData.View::id)
                .toList());
  }

  /** 先选最具体的资源，再检查启用与独立 API 绑定；不把管理员自动视为业务角色。 */
  public void requireApi(Long id, String code, String method, String path) {
    Map<Long, ManagedSystem> apps = new LinkedHashMap<>();
    systems.all().forEach(a -> apps.put(a.getId(), a));
    List<ProtectedResource> candidates =
        resources.all().stream()
            .filter(r -> r.getMethod().equals(method))
            .filter(
                r -> {
                  ManagedSystem a = apps.get(r.getSystemId());
                  return a != null && a.getEnabled() && a.getCode().equals(code);
                })
            .filter(r -> matcher.matches(r.getPath(), path))
            .sorted((a, b) -> matcher.compare(a.getPath(), b.getPath()))
            .toList();
    if (candidates.isEmpty()) throw new ForbiddenException("此接口尚未注册为有效 API 资源");
    ProtectedResource selected = candidates.getFirst();
    if (!selected.getActive()) throw new ForbiddenException("此 API 资源已停用");
    Set<Long> effective = new HashSet<>();
    effectiveViews(id).forEach(p -> effective.add(p.id()));
    if (assignments.bindings().stream()
        .noneMatch(e -> e.right().equals(selected.getId()) && effective.contains(e.left())))
      throw new ForbiddenException("当前用户没有此 API 资源的访问权限");
  }
}
