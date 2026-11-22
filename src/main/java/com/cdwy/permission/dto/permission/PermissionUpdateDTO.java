package com.cdwy.permission.dto.permission;

import com.cdwy.permission.service.port.data.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改菜单或按钮的请求；编码和所属应用不可修改。
 *
 * @param name 名称
 * @param type 菜单或按钮
 * @param menuPath 菜单地址，按钮不填
 * @param parentId 上级菜单
 */
public record PermissionUpdateDTO(
    @NotBlank @Size(max = 128) String name,
    @NotNull PermissionType type,
    @Size(max = 255) String menuPath,
    Long parentId) {}
