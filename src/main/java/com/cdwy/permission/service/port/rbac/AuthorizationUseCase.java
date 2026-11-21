package com.cdwy.permission.service.port.rbac;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/**
 * RBAC1 授权用例：给用户分配角色、给角色授予菜单与按钮，计算有效权限、权限来源与菜单树，并为 API 拦截器做接口鉴权。
 *
 * <p>有效权限：账号已启用且未过期时，直接分配且启用的角色及沿继承链可达的启用角色所授予的菜单与按钮，且其自身与各级上级
 * 都生效、所属应用已启用；停用角色阻断经过它的继承路径。授权不写入令牌，每次请求按当前数据计算，变更在下一次请求即生效，
 * 无需重新登录，也不改凭证版本。授予与撤销都幂等，不写审计日志。
 */
public interface AuthorizationUseCase extends BackendUseCase {
  /**
   * 给用户分配角色：用户、角色须存在（{@code USER_NOT_FOUND}、{@code ROLE_NOT_FOUND}，404）；账号已停用或已过期、角色已
   * 停用时抛 {@code GRANT_TARGET_INACTIVE}（400）；已分配时幂等。
   */
  void grantRole(Long userId, Long roleId);

  /**
   * 撤销用户的一条直接角色分配：两端须存在（{@code USER_NOT_FOUND}、{@code ROLE_NOT_FOUND}，404），不看启停状态，未分配时
   * 幂等；经其他角色得到的相同权限仍保留。
   */
  void revokeRole(Long userId, Long roleId);

  /**
   * 给角色授予菜单或按钮：两端须存在（{@code ROLE_NOT_FOUND}、{@code PERMISSION_NOT_FOUND}，404）；角色已停用，或权限本身、
   * 任一上级已失效、所属应用已停用时抛 {@code GRANT_TARGET_INACTIVE}（400）；已授予时幂等。只授予该节点本身，不连带上级
   * 或下级；继承该角色的角色同时获得。
   */
  void grantPermission(Long roleId, Long permissionId);

  /**
   * 撤销角色的一项直接授权：两端须存在（{@code ROLE_NOT_FOUND}、{@code PERMISSION_NOT_FOUND}，404），未授予时幂等；继承
   * 该角色的角色同时失去这一来源。
   */
  void revokePermission(Long roleId, Long permissionId);

  /** 用户直接分配的角色，含已停用的，不含继承得到的；用户不存在抛 {@code USER_NOT_FOUND}（404）。 */
  List<RoleData.View> roles(Long userId);

  /** 角色直接授予的菜单与按钮，含已失效的，不含继承来的；角色不存在抛 {@code ROLE_NOT_FOUND}（404）。 */
  List<PermissionData.View> rolePermissions(Long roleId);

  /**
   * 用户（{@code id}）的有效菜单与按钮，每项只出现一次；账号已停用或已过期时为空，用户不存在抛 {@code USER_NOT_FOUND}
   * （404）。
   */
  List<PermissionData.View> effectiveViews(Long id);

  /**
   * 用户（{@code id}）在全部应用内的有效菜单树（含按钮）：补出的祖先只作导航容器（{@code granted} 为 false），不代表拥有
   * 其授权或 API；账号已停用或已过期时为空，用户不存在抛 {@code USER_NOT_FOUND}（404）。同级顺序：结构化实现按有效权限的
   * 遍历先后，DDD 实现按标识升序。
   */
  List<PermissionData.Tree> menu(Long id);

  /** 用户在指定应用内的有效菜单树（控制台导航按应用 rbac 生成）；应用不存在返回 404。 */
  List<PermissionData.Tree> menu(Long id, String systemCode);

  /**
   * 用户（{@code id}）的有效权限及来源：每项权限只出现一次，附带从每个直接分配角色到直接拥有该权限的角色的最短有效路径；
   * 账号已停用或已过期时为空，用户不存在抛 {@code USER_NOT_FOUND}（404）。
   */
  List<HierarchyData.PermissionOrigin> origins(Long id);

  /**
   * 预览单个角色（{@code id}）的有效权限及来源，不涉及任何用户：以该角色为起点沿继承链展开，规则同 {@link #origins}；
   * 角色已停用时为空，不存在抛 {@code ROLE_NOT_FOUND}（404）。
   */
  List<HierarchyData.PermissionOrigin> roleOrigins(Long id);

  /**
   * 接口鉴权，API 拦截器对需要按 RBAC 授权的每个请求调用，管理员没有豁免。在该应用（须启用）同一 HTTP 方法的 API 资源中
   * 选路径匹配且最具体的一项：没有匹配、该项已停用（不回退到更宽泛的模式），或用户的有效权限都没有绑定该资源时，抛
   * {@code FORBIDDEN}（403）；通过时正常返回。{@code path} 为请求路径，接入应用接口去掉 {@code /api/apps/{应用编码}} 前缀。
   */
  void requireApi(Long userId, String systemCode, String method, String path);
}
