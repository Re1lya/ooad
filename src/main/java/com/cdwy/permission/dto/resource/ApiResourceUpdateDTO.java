package com.cdwy.permission.dto.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改 API 资源名称的请求；方法、路径和权限码登记后不可修改。
 *
 * @param name 名称
 */
public record ApiResourceUpdateDTO(@NotBlank @Size(max = 128) String name) {}
