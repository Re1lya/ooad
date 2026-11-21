package com.cdwy.permission.dto.common;

import jakarta.validation.constraints.NotNull;

/**
 * 启用或停用请求（用户、应用、角色）。
 *
 * @param enabled true 为启用，false 为停用
 */
public record StatusDTO(@NotNull Boolean enabled) {}
