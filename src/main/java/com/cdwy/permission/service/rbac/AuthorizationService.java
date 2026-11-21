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
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.*;

/** RBAC1：合并直接及继承角色权限，独立 API 绑定鉴权；导航祖先只作容器。 */
@Service
@RequiredArgsConstructor
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
  private final com.cdwy.permission.repository.system.BusinessSystemRepository systems;

  /**
   * 给用户分配角色：两端须存在（{@code USER_NOT_FOUND}、{@code ROLE_NOT_FOUND}）；账号已停用或已过期、
   * 角色已停用时抛 {@code GRANT_TARGET_INACTIVE}；已分配时幂等。授权不写入令牌，下一次请求即生效，
   * 无需重新登录；不写审计日志。
   */
  @Transactional
  public void grantRole(Long userId, Long roleId) {
    UserAccount user = users.require(userId);
    Role role = roles.require(roleId);
    if (!user.usable(Instant.now()) || !role.getEnabled())
      throw new InvalidDataException("GRANT_TARGET_INACTIVE", "不能给失效账号或停用角色新增授权");
    if (!userRoles.existsByUser_IdAndRole_Id(userId, roleId))
      userRoles.save(new UserRole(user, role));
  }

  /**
   * 撤销用户的一条直接角色分配：两端须存在（{@code USER_NOT_FOUND}、{@code ROLE_NOT_FOUND}），未分配时幂等，
   * 不看启停状态。经其他角色得到的相同权限仍保留；已签发令牌的下一次请求即按新授权鉴权，不写审计日志。
   */
  @Transactional
  public void revokeRole(Long userId, Long roleId) {
    users.require(userId);
    roles.require(roleId);
    userRoles.deleteByUser_IdAndRole_Id(userId, roleId);
  }

  /**
   * 给角色授予菜单或按钮：两端须存在（{@code ROLE_NOT_FOUND}、{@code PERMISSION_NOT_FOUND}）；角色已停用，
   * 或权限本身、任一上级失效、所属应用停用时抛 {@code GRANT_TARGET_INACTIVE}；已授予时幂等。
   * 只授予该节点本身，不连带上级或下级；下一次请求即生效，不写审计日志。
   */
  @Transactional
  public void grantPermission(Long roleId, Long permissionId) {
    Role role = roles.require(roleId);
    Permission p = permissions.require(permissionId);
    if (!role.getEnabled() || !PermissionService.usable(p))
      throw new InvalidDataException("GRANT_TARGET_INACTIVE", "不能新增失效角色或权限的授权");
    if (!rolePermissions.existsByRole_IdAndPermission_Id(roleId, permissionId))
      rolePermissions.save(new RolePermission(role, p));
  }

  /**
   * 撤销角色的一项直接权限：两端须存在（{@code ROLE_NOT_FOUND}、{@code PERMISSION_NOT_FOUND}），未授予时幂等。
   * 继承该角色的角色同时失去这一来源，下一次请求即生效；不写审计日志。
   */
  @Transactional
  public void revokePermission(Long roleId, Long permissionId) {
    roles.require(roleId);
    permissions.require(permissionId);
    rolePermissions.deleteByRole_IdAndPermission_Id(roleId, permissionId);
  }

  /** 用户直接分配的角色，不含继承得到的、含已停用的；用户不存在抛 {@code USER_NOT_FOUND}。 */
  public List<RoleData.View> roles(Long userId) {
    users.require(userId);
    return userRoles.findAllByUser_Id(userId).stream()
        .map(g -> RoleService.view(g.getRole()))
        .toList();
  }

  /** 角色直接授予的菜单与按钮，不含继承来的、含已失效的；角色不存在抛 {@code ROLE_NOT_FOUND}。 */
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

  /** 有效权限的结果视图，规则同 {@link #effective}：账号不可用时为空，用户不存在抛 {@code USER_NOT_FOUND}。 */
  public List<PermissionData.View> effectiveViews(Long id) {
    return effective(id).stream().map(PermissionService::view).toList();
  }

  /** 用户在全部应用内的有效菜单树（含按钮）：补出的祖先只作导航容器（{@code granted} 为 false），不代表拥有其授权。 */
  public List<PermissionData.Tree> menu(Long id) {
    return PermissionService.tree(effective(id));
  }

  /** 步骤：应用存在 → 有效权限中只保留该应用的 → 组成菜单树。 */
  public List<PermissionData.Tree> menu(Long id, String systemCode) {
    com.cdwy.permission.entity.system.BusinessSystem app =
        systems
            .findByCode(systemCode)
            .orElseThrow(() -> new ResourceNotFoundException("SYSTEM_NOT_FOUND", "接入应用不存在"));
    return PermissionService.tree(
        effective(id).stream()
            .filter(p -> p.getSystem().getId().equals(app.getId()))
            .toList());
  }

  /**
   * 接口鉴权，API 拦截器对需要按 RBAC 授权的每个请求调用；管理员没有豁免，令牌也不携带授权，每次按当前数据计算。
   *
   * <p>在该应用（须启用）同一 HTTP 方法的 API 资源中选路径匹配且最具体的一项：没有匹配时拒绝；该项已停用时直接拒绝，
   * 不回退到更宽泛的模式；用户有效权限中没有任何一项绑定该资源时拒绝。拒绝均抛 {@link ForbiddenException}
   * （错误码 {@code FORBIDDEN}），通过时正常返回。
   */
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
