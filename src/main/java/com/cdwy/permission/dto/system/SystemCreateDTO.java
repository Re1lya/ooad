package com.cdwy.permission.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 接入应用的请求；编码全局唯一由服务层检查。
 *
 * @param code 应用编码，也是统一身份接口地址中的应用段
 * @param name 名称
 * @param baseUrl 应用访问地址
 */
public record SystemCreateDTO(
    @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{0,63}") String code,
    @NotBlank @Size(max = 128) String name,
    @Size(max = 255) String baseUrl) {}
