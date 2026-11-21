package com.cdwy.permission.vo.identity;

/**
 * 是否拥有指定菜单或按钮权限的判断结果。
 *
 * @param allowed 是否拥有
 */
public record PermissionCheckVO(boolean allowed) {}
