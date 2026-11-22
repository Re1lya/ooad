package com.cdwy.permission.vo.identity;

/**
 * 统一身份用户（GET /auth/users/{userId}）。
 *
 * @param userId 统一身份用户标识
 * @param no 工号或学号
 * @param name 姓名
 */
public record IdentityUserVO(String userId, String no, String name) {}
