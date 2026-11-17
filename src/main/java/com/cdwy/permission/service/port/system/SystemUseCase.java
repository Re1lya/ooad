package com.cdwy.permission.service.port.system;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/** SystemUseCase：两种业务实现共用的用例边界。 */
public interface SystemUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  List<SystemData.View> list();

  /** 执行已校验输入对应的业务用例。 */
  SystemData.View create(SystemData.Create input);

  /** 执行已校验输入对应的业务用例。 */
  SystemData.View update(Long id, SystemData.Update input);

  /** 执行已校验输入对应的业务用例。 */
  SystemData.View status(Long id, boolean enabled);

  /** 执行已校验输入对应的业务用例。 */
  void delete(Long id);
}
