package com.cdwy.permission.service.port.resource;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/** ResourceUseCase：两种业务实现共用的用例边界。 */
public interface ResourceUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  List<ResourceData.View> list();

  /** 执行已校验输入对应的业务用例。 */
  ResourceData.View create(ResourceData.Create input);

  /** 执行已校验输入对应的业务用例。 */
  ResourceData.View update(Long id, ResourceData.Update input);

  /** 执行已校验输入对应的业务用例。 */
  ResourceData.View status(Long id, boolean active);

  /** 执行已校验输入对应的业务用例。 */
  void delete(Long id);
}
