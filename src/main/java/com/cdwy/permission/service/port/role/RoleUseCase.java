package com.cdwy.permission.service.port.role;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/** RoleUseCase：两种业务实现共用的用例边界。 */
public interface RoleUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  List<RoleData.View> list();

  /** 执行已校验输入对应的业务用例。 */
  RoleData.View create(RoleData.Create input);

  /** 执行已校验输入对应的业务用例。 */
  RoleData.View update(Long id, RoleData.Update input);

  /** 执行已校验输入对应的业务用例。 */
  RoleData.View status(Long id, boolean enabled);

  /** 执行已校验输入对应的业务用例。 */
  void delete(Long id);
}
