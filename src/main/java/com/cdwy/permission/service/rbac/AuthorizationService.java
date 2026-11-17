package com.cdwy.permission.service.rbac;

import com.cdwy.permission.entity.permission.Permission;
import com.cdwy.permission.entity.rbac.*;
import com.cdwy.permission.entity.resource.ApiResource;
import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.rbac.*;
import com.cdwy.permission.repository.resource.ApiResourceRepository;
import com.cdwy.permission.service.permission.PermissionService;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import com.cdwy.permission.service.role.RoleService;
import com.cdwy.permission.service.user.UserService;
import java.time.Instant;
import java.util.*;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.*;

/** RBAC1：合并直接及继承角色权限，独立 API 绑定鉴权；导航祖先只作容器。 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@Transactional(readOnly = true)
public class AuthorizationService implements AuthorizationUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final RoleHierarchyService hierarchy;
  private final UserRoleRepository userRoles;
  private final RolePermissionRepository rolePermissions;
  private final PermissionApiRepository bindings;
  private final ApiResourceRepository resources;
  private final UserService users;
  private final RoleService roles;
  private final PermissionService permissions;

  public AuthorizationService(
      RoleHierarchyService hierarchy,
      UserRoleRepository userRoles,
      RolePermissionRepository rolePermissions,
      PermissionApiRepository bindings,
      ApiResourceRepository resources,
      UserService users,
      RoleService roles,
      PermissionService permissions) {
    this.hierarchy = hierarchy;
    this.userRoles = userRoles;
    this.rolePermissions = rolePermissions;
    this.bindings = bindings;
    this.resources = resources;
    this.users = users;
    this.roles = roles;
    this.permissions = permissions;
  }

  @Transactional
  public void grantRole(Long userId, Long roleId) {
    UserAccount user = users.require(userId);
    Role role = roles.require(roleId);
    if (!user.usable(Instant.now()) || !role.getEnabled())
      throw new IllegalArgumentException("不能给失效账号或停用角色新增授权");
    if (!userRoles.existsByUser_IdAndRole_Id(userId, roleId))
      userRoles.save(new UserRole(user, role));
  }

  @Transactional
  public void revokeRole(Long userId, Long roleId) {
    users.require(userId);
    roles.require(roleId);
    userRoles.deleteByUser_IdAndRole_Id(userId, roleId);
  }

  @Transactional
  public void grantPermission(Long roleId, Long permissionId) {
    Role role = roles.require(roleId);
    Permission p = permissions.require(permissionId);
    if (!role.getEnabled() || !PermissionService.usable(p))
      throw new IllegalArgumentException("不能新增失效角色或权限的授权");
    if (!rolePermissions.existsByRole_IdAndPermission_Id(roleId, permissionId))
      rolePermissions.save(new RolePermission(role, p));
  }

  @Transactional
  public void revokePermission(Long roleId, Long permissionId) {
    roles.require(roleId);
    permissions.require(permissionId);
    rolePermissions.deleteByRole_IdAndPermission_Id(roleId, permissionId);
  }

  public List<RoleData.View> roles(Long userId) {
    users.require(userId);
    return userRoles.findAllByUser_Id(userId).stream()
        .map(g -> RoleService.view(g.getRole()))
        .toList();
  }

  public List<PermissionData.View> rolePermissions(Long roleId) {
    roles.require(roleId);
    return rolePermissions.findAllByRole_Id(roleId).stream()
        .map(g -> PermissionService.view(g.getPermission()))
        .toList();
  }

  /** RBAC1 有效权限：直接角色与所有有效继承角色的权限并集。 */
  public List<Permission> effective(Long userId) {
    if (!users.require(userId).usable(Instant.now())) return List.of();
    Map<Long, Permission> result = new LinkedHashMap<>();
    Map<Long, List<Role>> graph = hierarchy.graph();
    for (UserRole grant : userRoles.findAllByUser_Id(userId))
      for (RoleHierarchyService.ResolvedRole resolved : hierarchy.resolve(grant.getRole(), graph))
        for (RolePermission assignment :
            rolePermissions.findAllByRole_Id(resolved.role().getId())) {
          Permission permission = assignment.getPermission();
          if (PermissionService.usable(permission))
            result.putIfAbsent(permission.getId(), permission);
        }
    return List.copyOf(result.values());
  }

  /** 用户权限来源；同一权限可保留多个分配角色提供的独立来源。 */
  public List<HierarchyData.PermissionOrigin> origins(Long userId) {
    if (!users.require(userId).usable(Instant.now())) return List.of();
    return explain(
        userRoles.findAllByUser_Id(userId).stream()
            .map(com.cdwy.permission.entity.rbac.UserRole::getRole)
            .toList());
  }

  /** 管理员预览单角色的有效权限，包含多级继承来源。 */
  public List<HierarchyData.PermissionOrigin> roleOrigins(Long roleId) {
    return explain(List.of(hierarchy.require(roleId)));
  }

  /** 汇总来源；同一个分配角色到同一权限角色展示一条最短有效路径。 */
  private List<HierarchyData.PermissionOrigin> explain(List<Role> assigned) {
    Map<Long, Permission> values = new LinkedHashMap<>();
    Map<Long, List<HierarchyData.Origin>> sources = new LinkedHashMap<>();
    Map<Long, List<Role>> graph = hierarchy.graph();
    for (Role root : assigned)
      for (RoleHierarchyService.ResolvedRole resolved : hierarchy.resolve(root, graph))
        for (RolePermission assignment :
            rolePermissions.findAllByRole_Id(resolved.role().getId())) {
          Permission permission = assignment.getPermission();
          if (!PermissionService.usable(permission)) continue;
          values.putIfAbsent(permission.getId(), permission);
          sources
              .computeIfAbsent(permission.getId(), ignored -> new ArrayList<>())
              .add(
                  new HierarchyData.Origin(
                      root.getId(),
                      root.getName(),
                      resolved.role().getId(),
                      resolved.role().getName(),
                      !root.getId().equals(resolved.role().getId()),
                      resolved.path()));
        }
    return values.values().stream()
        .map(
            value ->
                new HierarchyData.PermissionOrigin(
                    PermissionService.view(value), List.copyOf(sources.get(value.getId()))))
        .toList();
  }

  public List<PermissionData.View> effectiveViews(Long id) {
    return effective(id).stream().map(PermissionService::view).toList();
  }

  public List<PermissionData.Tree> menu(Long id) {
    return PermissionService.tree(effective(id));
  }

  public void requireApi(Long userId, String systemCode, String method, String path) {
    List<ApiResource> candidates =
        resources.findAllBySystem_CodeAndMethod(systemCode, method).stream()
            .filter(r -> r.getSystem().getEnabled())
            .filter(
                r ->
                    PathPatternParser.defaultInstance
                        .parse(r.getPath())
                        .matches(PathContainer.parsePath(path)))
            .sorted(
                (a, b) ->
                    PathPattern.SPECIFICITY_COMPARATOR.compare(
                        PathPatternParser.defaultInstance.parse(a.getPath()),
                        PathPatternParser.defaultInstance.parse(b.getPath())))
            .toList();
    if (candidates.isEmpty()) throw new ForbiddenException("此接口尚未注册为有效 API 资源");
    // Prefer the most specific pattern. A broader grant cannot bypass a more specific resource.
    ApiResource selected = candidates.getFirst();
    if (!selected.getActive()) throw new ForbiddenException("此 API 资源已停用");
    Set<Long> effectiveIds = new HashSet<>();
    effective(userId).forEach(p -> effectiveIds.add(p.getId()));
    boolean allowed =
        bindings.findAllByResource_Id(selected.getId()).stream()
            .anyMatch(b -> effectiveIds.contains(b.getPermission().getId()));
    if (!allowed) throw new ForbiddenException("当前用户没有此 API 资源的访问权限");
  }
}
