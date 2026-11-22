package com.cdwy.permission.dto.permission;

import com.cdwy.permission.service.port.data.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 新建菜单或按钮的请求；按钮必须隶属同一应用的菜单，由服务层检查。
 *
 * @param systemId 所属应用
 * @param code 权限编码，全局唯一
 * @param name 名称
 * @param type 菜单或按钮
 * @param menuPath 菜单地址，按钮不填
 * @param parentId 上级菜单
 */
public record PermissionCreateDTO(
    @NotNull @Positive Long systemId,
    @NotBlank @Size(max = 128) String code,
    @NotBlank @Size(max = 128) String name,
    @NotNull PermissionType type,
    @Size(max = 255) String menuPath,
    Long parentId) {}
