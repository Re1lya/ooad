package com.cdwy.permission.dto.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改角色名称与说明的请求；编码不可修改。
 *
 * @param name 名称
 * @param description 说明
 */
public record RoleUpdateDTO(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 255) String description) {}
