package com.cdwy.permission.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改应用名称与访问地址的请求；编码不可修改。
 *
 * @param name 名称
 * @param baseUrl 应用访问地址
 */
public record SystemUpdateDTO(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 255) String baseUrl) {}
