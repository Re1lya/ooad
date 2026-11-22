package com.cdwy.permission.config;

import java.time.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 人员有效期策略，绑定配置前缀 {@code permission.policy}：外部人员与临时人员的有效期上限，以及计算日期边界所用的时区。
 *
 * @param externalMaxDays 外部人员的到期日最晚可设为今天之后的第几天，须为正数
 * @param temporaryMaxDays 临时人员的到期日最晚可设为今天之后的第几天，须为正数
 * @param zone 时区 ID（例如 {@code Asia/Shanghai}），用于确定“今天”和有效期截止时刻
 */
@ConfigurationProperties("permission.policy")
public record AccessPolicy(int externalMaxDays, int temporaryMaxDays, String zone) {
  /** 校验上限为正数、时区可解析；配置不合法时抛出异常，应用启动失败。 */
  public AccessPolicy {
    if (externalMaxDays < 1 || temporaryMaxDays < 1)
      throw new IllegalArgumentException("人员有效期上限必须为正数");
    ZoneId.of(zone);
  }

  /** 配置时区对应的 {@link ZoneId}。 */
  public ZoneId zoneId() {
    return ZoneId.of(zone);
  }
}
