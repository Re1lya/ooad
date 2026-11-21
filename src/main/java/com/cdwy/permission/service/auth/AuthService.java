package com.cdwy.permission.service.auth;

import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.system.BusinessSystemRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.AuthenticationFailedException;
import com.cdwy.permission.service.port.error.InvalidDataException;
import com.cdwy.permission.service.user.*;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化事务脚本：按查询、业务校验、写入和结果转换组织处理步骤。 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@Transactional(readOnly = true)
public class AuthService implements AuthenticationUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final UserAccountRepository users;
  private final PasswordEncoder encoder;
  private final JwtTokenService tokens;
  private final UserService userService;
  private final BusinessSystemRepository systems;

  /** 控制台登录：校验账号密码，签发目标系统为权限系统自身的令牌。 */
  public AuthData.Token login(AuthData.Login input) {
    UserAccount user = authenticate(input.username(), input.password());
    return new AuthData.Token(
        tokens.issue(user), "Bearer", tokens.expiresIn(), UserViews.view(user));
  }

  /** 接入应用登录：步骤 1 校验目标系统，步骤 2 校验账号密码，步骤 3 按目标系统签发。 */
  public AuthData.Token loginTo(AuthData.AppLogin input) {
    List<String> audience = audience(input.systems(), input.target());
    UserAccount user = authenticate(input.username(), input.password());
    return new AuthData.Token(
        tokens.issue(user.getId(), user.getCredentialVersion(), audience),
        "Bearer",
        tokens.expiresIn(),
        UserViews.view(user));
  }

  /** systems 必须包含被调用的应用，且每一项都是已登记的应用编码。 */
  private List<String> audience(List<String> requested, String target) {
    if (!requested.contains(target))
      throw new InvalidDataException("LOGIN_TARGET_NOT_IN_SYSTEMS", "systems 必须包含当前应用 " + target);
    List<String> distinct = requested.stream().distinct().toList();
    for (String code : distinct)
      if (systems.findByCode(code).isEmpty())
        throw new InvalidDataException("LOGIN_SYSTEM_UNKNOWN", "未登记的目标系统：" + code);
    return distinct;
  }

  /** 失败不泄露账号是否存在。 */
  private UserAccount authenticate(String username, String password) {
    return users
        .findByUsername(username)
        .filter(u -> u.usable(Instant.now()))
        .filter(u -> encoder.matches(password, u.getPasswordHash()))
        .orElseThrow(() -> new AuthenticationFailedException("账号或密码错误，或账号已失效"));
  }

  /**
   * 每个请求由访问拦截器调用。验签与有效期由 {@link JwtTokenService#verify} 负责；令牌 {@code aud} 不含 audience 时抛“令牌不适用于当前系统”；
   * 账号不存在、已停用或已过期，或凭证版本与令牌不一致（退出登录、改密、启停后）时抛“账号或凭证已失效”，均为 401。
   */
  public AuthData.Identity resolve(String header, String audience) {
    JwtTokenService.Identity identity = tokens.verify(header);
    if (!identity.audience().contains(audience))
      throw new AuthenticationFailedException("令牌不适用于当前系统，请重新登录");
    return users
        .findById(identity.userId())
        .filter(u -> u.usable(Instant.now()))
        .filter(u -> u.getCredentialVersion() == identity.credentialVersion())
        .map(u -> new AuthData.Identity(u.getId(), u.getAdministrator()))
        .orElseThrow(() -> new AuthenticationFailedException("账号或凭证已失效，请重新登录"));
  }

  /** 退出登录：递增凭证版本，已签发的令牌在下一次请求时失效。 */
  @Transactional
  public void logout(Long id) {
    UserAccount user =
        users.lockById(id).orElseThrow(() -> new AuthenticationFailedException("账号不存在"));
    user.setCredentialVersion(user.getCredentialVersion() + 1);
  }

  /**
   * 修改本人密码：锁定账号后核对原密码，不符抛 401；新密码按项目密码规则校验（{@code PASSWORD_POLICY_VIOLATION}），保存后递增凭证版本，
   * 已签发的令牌（包括当前令牌）全部失效。
   */
  @Transactional
  public void changePassword(Long id, AuthData.PasswordChange input) {
    UserAccount user =
        users.lockById(id).orElseThrow(() -> new AuthenticationFailedException("账号不存在"));
    if (!encoder.matches(input.currentPassword(), user.getPasswordHash()))
      throw new AuthenticationFailedException("原密码错误");
    userService.password(id, input.newPassword());
  }
}
