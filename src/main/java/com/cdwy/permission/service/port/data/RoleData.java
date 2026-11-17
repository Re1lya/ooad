package com.cdwy.permission.service.port.data;

/** RoleData 业务用例的不可变输入与结果。 */
public final class RoleData {
  private RoleData() {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Create(String code, String name, String description) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Update(String name, String description) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record View(Long id, String code, String name, String description, boolean enabled) {}
}
