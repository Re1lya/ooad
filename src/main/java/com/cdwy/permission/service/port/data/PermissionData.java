package com.cdwy.permission.service.port.data;

/** PermissionData 业务用例的不可变输入与结果。 */
public final class PermissionData {
  private PermissionData() {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Input(
      Long systemId, String code, String name, String type, String menuPath, Long parentId) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Update(String name, String type, String menuPath, Long parentId) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record View(
      Long id,
      Long systemId,
      String code,
      String name,
      String type,
      String menuPath,
      Long parentId,
      boolean active) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Tree(View permission, boolean granted, java.util.List<Tree> children) {}
}
