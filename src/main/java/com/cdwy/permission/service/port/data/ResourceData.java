package com.cdwy.permission.service.port.data;

/** ResourceData 业务用例的不可变输入与结果。 */
public final class ResourceData {
  private ResourceData() {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Create(Long systemId, String name, String method, String path, String code) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Update(String name) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record View(
      Long id,
      Long systemId,
      String name,
      String method,
      String path,
      boolean active,
      String code) {}
}
