package com.cdwy.permission.service.auth;

import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.AuthenticationFailedException;
import com.cdwy.permission.service.user.*;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化事务脚本：按查询、业务校验、写入和结果转换组织处理步骤。 */
@Service
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

  public AuthService(
      UserAccountRepository users,
      PasswordEncoder encoder,
      JwtTokenService tokens,
      UserService userService) {
    this.users = users;
    this.encoder = encoder;
    this.tokens = tokens;
    this.userService = userService;
  }

  public AuthData.Token login(AuthData.Login input) {
    UserAccount user =
        users
            .findByUsername(input.username())
            .filter(u -> u.usable(Instant.now()))
            .filter(u -> encoder.matches(input.password(), u.getPasswordHash()))
            .orElseThrow(() -> new AuthenticationFailedException("账号或密码错误，或账号已失效"));
    return new AuthData.Token(
        tokens.issue(user), "Bearer", tokens.expiresIn(), UserViews.view(user));
  }

  public AuthData.Identity resolve(String header) {
    JwtTokenService.Identity identity = tokens.verify(header);
    return users
        .findById(identity.userId())
        .filter(u -> u.usable(Instant.now()))
        .filter(u -> u.getCredentialVersion() == identity.credentialVersion())
        .map(u -> new AuthData.Identity(u.getId(), u.getAdministrator()))
        .orElseThrow(() -> new AuthenticationFailedException("账号或凭证已失效，请重新登录"));
  }

  @Transactional
  public void changePassword(Long id, AuthData.PasswordChange input) {
    UserAccount user =
        users.lockById(id).orElseThrow(() -> new AuthenticationFailedException("账号不存在"));
    if (!encoder.matches(input.currentPassword(), user.getPasswordHash()))
      throw new AuthenticationFailedException("原密码错误");
    userService.password(id, input.newPassword());
  }
}
