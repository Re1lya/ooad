package com.cdwy.permission.dto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 接入应用的登录请求（POST /auth/login）。
 *
 * @param username 学号或工号
 * @param password 密码
 * @param systems 令牌的目标系统编码，必须包含当前应用
 */
public record IdentityLoginDTO(
    @NotBlank @Size(max = 128) String username,
    @NotBlank @Size(max = 256) String password,
    @NotEmpty List<@NotBlank String> systems) {}
