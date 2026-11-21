package com.cdwy.permission.service.port.data;

/** 角色继承与权限来源的用例结果：继承边来自 {@code HierarchyUseCase}，权限来源来自 {@code AuthorizationUseCase}。 */
public final class HierarchyData {
  private HierarchyData() {}

  /**
   * 一条显式继承边（{@code HierarchyUseCase#edges}）：高级角色获得被继承角色的全部有效权限。
   *
   * @param seniorRoleId 高级角色标识
   * @param seniorRoleName 高级角色名称
   * @param inheritedRoleId 被继承角色标识
   * @param inheritedRoleName 被继承角色名称
   */
  public record Edge(
      Long seniorRoleId, String seniorRoleName, Long inheritedRoleId, String inheritedRoleName) {}

  /**
   * 一项权限的一个来源：从起点角色沿继承链到直接拥有该权限的角色的最短有效路径。
   *
   * @param assignedRoleId 起点角色：用户直接分配的角色，单角色预览时为被预览的角色
   * @param assignedRoleName 起点角色名称
   * @param permissionRoleId 直接拥有该权限的角色
   * @param permissionRoleName 直接拥有该权限的角色名称
   * @param inherited 是否经继承获得，即两者不是同一角色
   * @param path 路径上的角色标识，从起点到直接拥有者，含两端；直接拥有时只有起点
   */
  public record Origin(
      Long assignedRoleId,
      String assignedRoleName,
      Long permissionRoleId,
      String permissionRoleName,
      boolean inherited,
      java.util.List<Long> path) {}

  /**
   * 一项有效权限及其全部来源（{@code AuthorizationUseCase} 的 {@code origins}、{@code roleOrigins}）；同一权限只出现一次。
   *
   * @param permission 有效的菜单或按钮
   * @param sources 全部来源，至少一条
   */
  public record PermissionOrigin(PermissionData.View permission, java.util.List<Origin> sources) {}
}
