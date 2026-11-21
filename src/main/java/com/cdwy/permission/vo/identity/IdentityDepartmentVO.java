package com.cdwy.permission.vo.identity;

/**
 * 组织架构中的一个部门（GET /auth/departments）。
 *
 * @param id 部门标识
 * @param code 部门编码
 * @param name 名称
 * @param parentId 上级部门
 * @param hasChildren 是否有下级部门
 */
public record IdentityDepartmentVO(
    Long id,
    String code,
    String name,
    Long parentId,
    boolean hasChildren) {}
