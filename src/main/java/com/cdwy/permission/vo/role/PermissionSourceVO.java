package com.cdwy.permission.vo.role;

import java.util.List;

/**
 * 一项权限的一个来源：从分配给用户的角色出发、经继承到达直接拥有该权限的角色的最短有效路径。
 *
 * @param assignedRoleId 分配给用户的角色
 * @param assignedRoleName 其名称
 * @param permissionRoleId 直接拥有该权限的角色
 * @param permissionRoleName 其名称
 * @param inherited 是否经继承获得
 * @param path 路径上的角色标识
 */
public record PermissionSourceVO(
    Long assignedRoleId,
    String assignedRoleName,
    Long permissionRoleId,
    String permissionRoleName,
    boolean inherited,
    List<Long> path) {}
