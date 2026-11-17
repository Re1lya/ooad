package com.cdwy.permission.service.port.user;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;

/** UserUseCase：两种业务实现共用的用例边界。 */
public interface UserUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  UserResult get(Long id);

  /** 执行已校验输入对应的业务用例。 */
  UserData.Page<UserResult> list(int page, int size);

  /** 执行已校验输入对应的业务用例。 */
  UserResult create(CreateUserCommand input);

  /** 执行已校验输入对应的业务用例。 */
  UserResult update(Long id, UserData.Update input);

  /** 执行已校验输入对应的业务用例。 */
  UserResult status(Long actor, Long id, boolean enabled);

  /** 执行已校验输入对应的业务用例。 */
  void password(Long id, String password);

  /** 执行已校验输入对应的业务用例。 */
  void delete(Long actor, Long id);
}
