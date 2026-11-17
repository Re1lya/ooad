package com.cdwy.permission.service.port.data;

/** UserData 业务用例的不可变输入与结果。 */
public final class UserData {
  private UserData() {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Update(
      String displayName, Long departmentId, String personType, java.time.LocalDate expiresOn) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Page<T>(java.util.List<T> items, long total, int page, int size) {}
}
