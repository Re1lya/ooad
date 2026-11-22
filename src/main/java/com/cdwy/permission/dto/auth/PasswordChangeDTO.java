package com.cdwy.permission.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 本人修改密码请求；新密码的强度规则由服务层的密码策略检查。
 *
 * @param currentPassword 当前密码
 * @param newPassword 新密码
 */
public record PasswordChangeDTO(@NotBlank String currentPassword, @NotBlank String newPassword) {}
