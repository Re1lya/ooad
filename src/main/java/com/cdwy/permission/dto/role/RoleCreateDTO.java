package com.cdwy.permission.dto.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建角色的请求；编码全局唯一由服务层检查。
 *
 * @param code 角色编码，接入应用按它识别身份，例如 teacher
 * @param name 名称
 * @param description 说明
 */
public record RoleCreateDTO(
    @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{0,63}") String code,
    @NotBlank @Size(max = 128) String name,
    @Size(max = 255) String description) {}
