package com.cdwy.permission.vo.permission;

/**
 * 菜单或按钮。
 *
 * @param id 标识
 * @param systemId 所属应用
 * @param code 权限编码
 * @param name 名称
 * @param type MENU 或 BUTTON
 * @param menuPath 菜单地址
 * @param parentId 上级菜单
 * @param active 是否生效
 */
public record PermissionVO(
    Long id,
    Long systemId,
    String code,
    String name,
    String type,
    String menuPath,
    Long parentId,
    boolean active) {}
