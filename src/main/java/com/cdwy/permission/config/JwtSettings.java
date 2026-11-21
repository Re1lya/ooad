package com.cdwy.permission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 签发配置，绑定配置前缀 {@code permission.jwt}，由 {@code JwtTokenService} 使用。
 *
 * @param secret HMAC 签名密钥（{@code permission.jwt.secret}），非空时至少 32 字节；为空时每次启动随机生成，重启后已签发的令牌全部失效
 * @param expirationSeconds 令牌有效期（秒），须大于零
 */
@ConfigurationProperties("permission.jwt")
public record JwtSettings(String secret, long expirationSeconds) {
  /** 校验有效期为正数；不合法时应用启动失败。 */
  public JwtSettings {
    if (expirationSeconds < 1) throw new IllegalArgumentException("Token 有效期必须大于零");
  }
}
