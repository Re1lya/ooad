package com.cdwy.permission.vo.organization;

/**
 * 部门。
 *
 * @param id 部门标识
 * @param name 名称
 * @param parentId 上级部门
 * @param code 部门编码
 */
public record DepartmentVO(Long id, String name, Long parentId, String code) {}
