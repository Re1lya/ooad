package com.cdwy.permission.dto.common;

import jakarta.validation.constraints.NotNull;

/**
 * 生效或失效请求（菜单与按钮、API 资源）。
 *
 * @param active true 为生效，false 为失效
 */
public record ActivityDTO(@NotNull Boolean active) {}
