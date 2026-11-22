package com.cdwy.permission.vo.identity;

/**
 * 部门下的一名人员，不含电话等联系方式（GET /auth/departments/{id}/users）。
 *
 * @param userId 统一身份用户标识
 * @param no 工号或学号
 * @param name 姓名
 * @param departmentId 所属部门
 * @param departmentName 部门名称
 */
public record IdentityDepartmentUserVO(
    String userId,
    String no,
    String name,
    Long departmentId,
    String departmentName) {}
