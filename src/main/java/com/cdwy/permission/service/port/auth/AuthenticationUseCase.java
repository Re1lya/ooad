package com.cdwy.permission.service.port.auth;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;

/** AuthenticationUseCase：两种业务实现共用的用例边界。 */
public interface AuthenticationUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  AuthData.Token login(AuthData.Login input);

  /** 执行已校验输入对应的业务用例。 */
  AuthData.Identity resolve(String header);

  /** 执行已校验输入对应的业务用例。 */
  void changePassword(Long id, AuthData.PasswordChange input);
}
