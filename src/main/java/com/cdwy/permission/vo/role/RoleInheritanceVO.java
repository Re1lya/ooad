package com.cdwy.permission.vo.role;

/**
 * 一条直接继承关系：高级角色获得被继承角色的全部有效权限。
 *
 * @param seniorRoleId 高级角色
 * @param seniorRoleName 高级角色名称
 * @param inheritedRoleId 被继承角色
 * @param inheritedRoleName 被继承角色名称
 */
public record RoleInheritanceVO(
    Long seniorRoleId,
    String seniorRoleName,
    Long inheritedRoleId,
    String inheritedRoleName) {}
