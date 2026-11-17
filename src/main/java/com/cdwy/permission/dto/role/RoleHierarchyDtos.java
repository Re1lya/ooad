package com.cdwy.permission.dto.role;

import com.cdwy.permission.dto.permission.PermissionDtos;
import java.util.List;

/** 继承展示与权限来源的只读结果；不可变 record 使用显式构造映射。 */
public final class RoleHierarchyDtos {
  private RoleHierarchyDtos() {}

  /** 高级角色继承 inheritedRole 的权限。 */
  public record Edge(
      Long seniorRoleId, String seniorRoleName, Long inheritedRoleId, String inheritedRoleName) {}

  /** 每个来源保留从用户分配角色到权限所属角色的一条最短有效路径。 */
  public record Origin(
      Long assignedRoleId,
      String assignedRoleName,
      Long permissionRoleId,
      String permissionRoleName,
      boolean inherited,
      List<Long> path) {}

  /** 相同权限仅返回一次，同时展示所有有效角色来源。 */
  public record PermissionOrigin(PermissionDtos.View permission, List<Origin> sources) {}
}
