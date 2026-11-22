package com.cdwy.permission.vo.permission;

import java.util.List;

/**
 * 菜单树的一个节点。
 *
 * @param permission 菜单或按钮
 * @param granted 当前用户是否拥有
 * @param children 下级节点
 */
public record PermissionTreeVO(
    PermissionVO permission,
    boolean granted,
    List<PermissionTreeVO> children) {}
