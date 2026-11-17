package com.cdwy.permission.service.port.data;

/** HierarchyData 业务用例的不可变输入与结果。 */
public final class HierarchyData {
  private HierarchyData() {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Edge(
      Long seniorRoleId, String seniorRoleName, Long inheritedRoleId, String inheritedRoleName) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Origin(
      Long assignedRoleId,
      String assignedRoleName,
      Long permissionRoleId,
      String permissionRoleName,
      boolean inherited,
      java.util.List<Long> path) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record PermissionOrigin(PermissionData.View permission, java.util.List<Origin> sources) {}
}
