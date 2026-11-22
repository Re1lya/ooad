package com.cdwy.permission.vo.role;

/**
 * 角色。
 *
 * @param id 标识
 * @param code 角色编码
 * @param name 名称
 * @param description 说明
 * @param enabled 是否启用
 */
public record RoleVO(Long id, String code, String name, String description, boolean enabled) {}
