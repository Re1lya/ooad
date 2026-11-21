package com.cdwy.permission.service.auth;

import com.cdwy.permission.config.JwtSettings;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.service.port.error.AuthenticationFailedException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * 无登录会话存储。签发与验签只处理 JWT，自身不访问数据库。
 *
 * <p>{@code aud} 记录令牌的目标系统：控制台登录签发 {@value #CONSOLE_AUDIENCE}，接入应用登录签发请求中的 {@code
 * systems}。令牌不携带角色与权限。
 */
@Service
public class JwtTokenService {
  /** 权限系统自身（管理控制台）的系统标识。 */
  public static final String CONSOLE_AUDIENCE = com.cdwy.permission.config.BusinessAccess.CONSOLE;

  private final SecretKey signingKey;
  private final long expirationSeconds;

  /**
   * 密钥取 {@code permission.jwt.secret}：非空时至少 32 字节（UTF-8），否则启动失败；为空时每次启动随机生成，重启后已签发的令牌全部失效。
   * 有效期取 {@code permission.jwt.expiration-seconds}。
   */
  public JwtTokenService(JwtSettings settings) {
    byte[] bytes = settings.secret().getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 0 && bytes.length < 32) throw new IllegalArgumentException("JWT 密钥至少 32 字节");
    signingKey = bytes.length == 0 ? Jwts.SIG.HS256.key().build() : Keys.hmacShaKeyFor(bytes);
    expirationSeconds = settings.expirationSeconds();
  }

  /** 令牌有效时长（秒），即登录结果中的 {@code expiresIn}。 */
  public long expiresIn() {
    return expirationSeconds;
  }

  /** 控制台令牌。 */
  public String issue(UserAccount user) {
    return issue(user.getId(), user.getCredentialVersion(), Set.of(CONSOLE_AUDIENCE));
  }

  /** 按身份、凭证版本和目标系统签发，供领域端口适配器使用。 */
  public String issue(Long id, long version, Collection<String> audience) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(id.toString())
        .audience()
        .add(audience)
        .and()
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expirationSeconds)))
        .claim("cv", version)
        .signWith(signingKey)
        .compact();
  }

  /** 验签后的身份：用户、凭证版本与令牌目标系统。 */
  public record Identity(Long userId, long credentialVersion, Set<String> audience) {}

  /**
   * 解析 {@code Authorization} 请求头：须以 {@code Bearer} 加空格开头（不区分大小写），签名有效且未过期，并带有正整数用户标识、凭证版本
   * {@code cv}、非空 {@code aud} 和不晚于当前时间的签发时间。只校验令牌本身，账号状态与目标系统由调用方检查；任何不符都抛同一个 401 提示。
   */
  public Identity verify(String header) {
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) throw invalid();
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(signingKey)
              .build()
              .parseSignedClaims(header.substring(7).trim())
              .getPayload();
      long id = Long.parseLong(claims.getSubject());
      Number version = claims.get("cv", Number.class);
      Set<String> audience = claims.getAudience();
      if (id < 1
          || version == null
          || audience == null
          || audience.isEmpty()
          || claims.getIssuedAt() == null
          || claims.getExpiration() == null
          || claims.getIssuedAt().after(new Date())
          || !claims.getExpiration().after(claims.getIssuedAt())) throw invalid();
      return new Identity(id, version.longValue(), Set.copyOf(audience));
    } catch (JwtException | IllegalArgumentException e) {
      throw invalid();
    }
  }

  /** 统一的令牌失败结果，不向调用方透露具体原因。 */
  private static AuthenticationFailedException invalid() {
    return new AuthenticationFailedException("Token 无效或已过期，请重新登录");
  }
}
