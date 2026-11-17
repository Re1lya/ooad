package com.cdwy.permission.service.ddd.domain.model;

import java.time.*;

/** 按项目时区计算人员有效期，包含截止日而不包含次日零点。 */
public record AccessWindow(int externalDays, int temporaryDays, ZoneId zone, LocalDate today) {
  /** 正式人员无限期；外部及临时人员必须处于允许的时间窗口。 */
  public Instant expiry(String type, LocalDate day) {
    if (Account.EMPLOYEE.equals(type)) {
      if (day != null) throw new IllegalArgumentException("正式员工不设置临时有效期");
      return null;
    }
    if (!Account.EXTERNAL.equals(type) && !Account.TEMPORARY.equals(type))
      throw new IllegalArgumentException("人员类型不合法");
    if (day == null) throw new IllegalArgumentException("外部或临时人员必须设置有效期");
    int max = Account.EXTERNAL.equals(type) ? externalDays : temporaryDays;
    if (day.isBefore(today) || day.isAfter(today.plusDays(max)))
      throw new IllegalArgumentException("有效期须在今天至 " + max + " 天以内");
    return day.plusDays(1).atStartOfDay(zone).toInstant();
  }
}
