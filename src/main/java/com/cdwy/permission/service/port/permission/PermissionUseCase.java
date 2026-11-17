package com.cdwy.permission.service.port.permission;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/** PermissionUseCase：两种业务实现共用的用例边界。 */
public interface PermissionUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  List<PermissionData.View> list(Long systemId);

  /** 执行已校验输入对应的业务用例。 */
  PermissionData.View create(PermissionData.Input input);

  /** 执行已校验输入对应的业务用例。 */
  PermissionData.View update(Long id, PermissionData.Update input);

  /** 执行已校验输入对应的业务用例。 */
  PermissionData.View status(Long id, boolean active);

  /** 执行已校验输入对应的业务用例。 */
  void delete(Long id);

  /** 执行已校验输入对应的业务用例。 */
  void bind(Long permissionId, Long resourceId);

  /** 执行已校验输入对应的业务用例。 */
  void unbind(Long permissionId, Long resourceId);

  /** 执行已校验输入对应的业务用例。 */
  List<ResourceData.View> resources(Long permissionId);

  /** 执行已校验输入对应的业务用例。 */
  List<PermissionData.Tree> tree(Long systemId);
}
