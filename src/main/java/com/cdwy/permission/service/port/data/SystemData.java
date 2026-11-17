package com.cdwy.permission.service.port.data;

/** SystemData 业务用例的不可变输入与结果。 */
public final class SystemData {
  private SystemData() {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Create(String code, String name, String baseUrl) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Update(String name, String baseUrl) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record View(Long id, String code, String name, String baseUrl, boolean enabled) {}
}
