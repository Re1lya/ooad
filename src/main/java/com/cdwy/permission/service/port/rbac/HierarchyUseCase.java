package com.cdwy.permission.service.port.rbac;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/** HierarchyUseCase：两种业务实现共用的用例边界。 */
public interface HierarchyUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  List<HierarchyData.Edge> edges();

  /** 执行已校验输入对应的业务用例。 */
  void inherit(Long seniorId, Long inheritedId);

  /** 执行已校验输入对应的业务用例。 */
  void remove(Long seniorId, Long inheritedId);
}
