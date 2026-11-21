package com.cdwy.permission.service.port.auth;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;

/** AuthenticationUseCase：两种业务实现共用的用例边界。 */
public interface AuthenticationUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  AuthData.Token login(AuthData.Login input);

  /** 接入应用登录：令牌 {@code aud} 为请求中的目标系统。 */
  AuthData.Token loginTo(AuthData.AppLogin input);

  /** 校验令牌、账号状态、凭证版本，并要求令牌的 {@code aud} 包含 {@code audience}。 */
  AuthData.Identity resolve(String header, String audience);

  /** 退出登录：递增凭证版本，使该用户已签发的全部令牌在下一次请求时失效（不保存令牌状态）。 */
  void logout(Long id);

  /** 执行已校验输入对应的业务用例。 */
  void changePassword(Long id, AuthData.PasswordChange input);
}
