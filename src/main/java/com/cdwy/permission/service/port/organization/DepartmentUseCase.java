package com.cdwy.permission.service.port.organization;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/** DepartmentUseCase：两种业务实现共用的用例边界。 */
public interface DepartmentUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  List<DepartmentData.View> list();

  /** 执行已校验输入对应的业务用例。 */
  DepartmentData.View create(DepartmentData.Input input);

  /** 执行已校验输入对应的业务用例。 */
  DepartmentData.View update(Long id, DepartmentData.Input input);

  /** 执行已校验输入对应的业务用例。 */
  void delete(Long id);
}
