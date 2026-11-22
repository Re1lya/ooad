package com.cdwy.permission.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理控制台登录请求。
 *
 * @param username 登录名（工号或学号）
 * @param password 密码，只用于校验，不落日志
 */
public record LoginDTO(
    @NotBlank @Size(max = 128) String username,
    @NotBlank @Size(max = 256) String password) {}
