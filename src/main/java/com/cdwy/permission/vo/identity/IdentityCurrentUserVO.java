package com.cdwy.permission.vo.identity;

import java.util.List;

/**
 * 当前用户在本应用内的身份（GET /auth/me）。
 *
 * @param subject 统一身份用户标识，即工号或学号
 * @param roles 有效角色编码（含继承）
 * @param permissions 本应用内的菜单、按钮与后端权限码
 * @param name 姓名
 * @param no 工号或学号
 */
public record IdentityCurrentUserVO(
    String subject,
    List<String> roles,
    List<String> permissions,
    String name,
    String no) {}
