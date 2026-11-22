package com.cdwy.permission.vo.role;

import com.cdwy.permission.vo.permission.PermissionVO;
import java.util.List;

/**
 * 一项有效权限及其全部来源；同一权限只出现一次。
 *
 * @param permission 菜单或按钮
 * @param sources 来源
 */
public record PermissionOriginVO(PermissionVO permission, List<PermissionSourceVO> sources) {}
