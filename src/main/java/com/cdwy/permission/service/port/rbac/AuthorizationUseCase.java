package com.cdwy.permission.service.port.rbac;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/** AuthorizationUseCase：两种业务实现共用的用例边界。 */
public interface AuthorizationUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  void grantRole(Long userId, Long roleId);

  /** 执行已校验输入对应的业务用例。 */
  void revokeRole(Long userId, Long roleId);

  /** 执行已校验输入对应的业务用例。 */
  void grantPermission(Long roleId, Long permissionId);

  /** 执行已校验输入对应的业务用例。 */
  void revokePermission(Long roleId, Long permissionId);

  /** 执行已校验输入对应的业务用例。 */
  List<RoleData.View> roles(Long userId);

  /** 执行已校验输入对应的业务用例。 */
  List<PermissionData.View> rolePermissions(Long roleId);

  /** 执行已校验输入对应的业务用例。 */
  List<PermissionData.View> effectiveViews(Long id);

  /** 执行已校验输入对应的业务用例。 */
  List<PermissionData.Tree> menu(Long id);

  /** 用户在指定应用内的有效菜单树（控制台导航按应用 rbac 生成）；应用不存在返回 404。 */
  List<PermissionData.Tree> menu(Long id, String systemCode);

  /** 执行已校验输入对应的业务用例。 */
  List<HierarchyData.PermissionOrigin> origins(Long id);

  /** 执行已校验输入对应的业务用例。 */
  List<HierarchyData.PermissionOrigin> roleOrigins(Long id);

  /** 执行已校验输入对应的业务用例。 */
  void requireApi(Long userId, String systemCode, String method, String path);
}
