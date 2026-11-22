package com.cdwy.permission.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建或修改部门的请求；同一上级下名称唯一、编码全局唯一由服务层检查。
 *
 * @param name 部门名称
 * @param parentId 上级部门，空为一级部门
 * @param code 部门编码，空则新建时按编号生成
 */
public record DepartmentDTO(
    @NotBlank @Size(max = 128) String name,
    Long parentId,
    @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,63}",
            message = "部门编码须以字母或数字开头，只含字母、数字和 _.-，最长 64 位")
        String code) {}
