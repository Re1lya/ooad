package com.cdwy.permission.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员重置密码的请求；强度规则由服务层的密码策略检查。
 *
 * @param password 新密码
 */
public record PasswordResetDTO(@NotBlank String password) {}
