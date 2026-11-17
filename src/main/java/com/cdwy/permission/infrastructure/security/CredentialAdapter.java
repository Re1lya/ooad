package com.cdwy.permission.infrastructure.security;

import com.cdwy.permission.config.AccessPolicy;
import com.cdwy.permission.service.auth.JwtTokenService;
import com.cdwy.permission.service.ddd.domain.model.AccessWindow;
import com.cdwy.permission.service.port.auth.CredentialGateway;
import com.cdwy.permission.service.user.PasswordPolicy;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 项目现有本地密码与 JWT 的共享技术适配器，不替换登录方案。 */
@Component
@RequiredArgsConstructor
public class CredentialAdapter implements CredentialGateway {
  private final PasswordEncoder encoder;
  private final JwtTokenService tokens;
  private final AccessPolicy policy;

  /** 委托已配置的密码编码器。 */
  public String encode(String value) {
    return encoder.encode(value);
  }

  /** 不把密码及散列记录到日志。 */
  public boolean matches(String value, String hash) {
    return encoder.matches(value, hash);
  }

  /** 沿用项目密码规则。 */
  public void validatePassword(String value) {
    PasswordPolicy.validate(value);
  }

  /** Token 只携带身份和凭证版本。 */
  public String issue(Long id, long version) {
    return tokens.issue(id, version);
  }

  /** 委托统一的签名及时间验证。 */
  public Claims verify(String header) {
    JwtTokenService.Identity identity = tokens.verify(header);
    return new Claims(identity.userId(), identity.credentialVersion());
  }

  /** 返回声明有效时长。 */
  public long expiresIn() {
    return tokens.expiresIn();
  }

  /** 实际运行时间。 */
  public Instant now() {
    return Instant.now();
  }

  /** 以配置时区计算日期边界。 */
  public AccessWindow window() {
    return new AccessWindow(
        policy.externalMaxDays(),
        policy.temporaryMaxDays(),
        policy.zoneId(),
        LocalDate.now(policy.zoneId()));
  }
}
