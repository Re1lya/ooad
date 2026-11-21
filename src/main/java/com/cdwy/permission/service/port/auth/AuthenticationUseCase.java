package com.cdwy.permission.service.port.auth;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;

/**
 * 认证用例：控制台与接入应用登录、每次请求的令牌解析、退出登录和修改本人密码。
 *
 * <p>服务端不保存令牌：令牌只携带用户标识、凭证版本与目标系统（{@code aud}），不含角色和权限；递增凭证版本即可让该用户
 * 已签发的全部令牌在下一次请求时失效。认证失败抛 {@code UNAUTHORIZED}（401），登录失败的提示不透露账号是否存在。
 */
public interface AuthenticationUseCase extends BackendUseCase {
  /**
   * 控制台登录：按登录名查找账号，账号须已启用、未过期且密码匹配，否则抛 {@code UNAUTHORIZED}（401），不区分账号不存在、
   * 密码错误和账号失效。成功时按当前凭证版本签发 {@code aud} 为权限系统自身（{@code rbac}）的令牌，返回令牌、类型
   * {@code Bearer}、有效秒数和用户资料；不改凭证版本（此前签发的令牌仍有效），不写审计日志。
   */
  AuthData.Token login(AuthData.Login input);

  /** 接入应用登录：令牌 {@code aud} 为请求中的目标系统。 */
  AuthData.Token loginTo(AuthData.AppLogin input);

  /** 校验令牌、账号状态、凭证版本，并要求令牌的 {@code aud} 包含 {@code audience}。 */
  AuthData.Identity resolve(String header, String audience);

  /** 退出登录：递增凭证版本，使该用户已签发的全部令牌在下一次请求时失效（不保存令牌状态）。 */
  void logout(Long id);

  /**
   * 修改本人密码：在账号写锁内核对原密码，账号不存在或原密码不符都抛 {@code UNAUTHORIZED}（401）；新密码不符合项目密码
   * 规则抛 {@code PASSWORD_POLICY_VIOLATION}（400）。成功后凭证版本加一，该用户已签发的全部令牌（包括本次请求所用的）
   * 都失效，需重新登录；不写审计日志。
   */
  void changePassword(Long id, AuthData.PasswordChange input);
}
