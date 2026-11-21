package com.cdwy.permission.service.port.data;

import java.util.List;

/** 接入应用身份用例的不可变结果：与 HTTP 表现及数据库技术无关。 */
public final class IdentityData {
  private IdentityData() {}

  /**
   * 某接入应用看到的当前用户。
   *
   * @param subject 统一身份用户标识（登录名，即工号或学号）
   * @param roles 有效角色编码：直接分配角色及沿继承链可达的启用角色
   * @param permissions 该应用内的有效菜单、按钮权限码与其绑定的后端 API 权限码
   * @param name 姓名
   * @param no 工号或学号（取登录名）
   */
  public record Current(
      String subject, List<String> roles, List<String> permissions, String name, String no) {}

  /** 按统一身份用户标识查询到的人员；no 为工号或学号，本系统取登录名。 */
  public record User(String userId, String no, String name) {}

  /** 统一身份部门；hasChildren 表示是否有直接子部门。 */
  public record Department(Long id, String code, String name, Long parentId, boolean hasChildren) {}

  /** 部门下的统一身份用户，不含电话等联系方式。 */
  public record DepartmentUser(
      String userId, String no, String name, Long departmentId, String departmentName) {}

  /** 页码从 1 开始的分页结果。 */
  public record Page<T>(int page, int pageSize, long total, List<T> items) {}

  /** 能否调用指定 operationId 的接口；不返回权限码。 */
  public record ApiCheck(String operationId, boolean allowed) {}

  /**
   * 分页查询条件；取值范围由 API 层校验。
   *
   * @param keyword 关键字，可空
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   */
  public record Query(String keyword, int page, int pageSize) {}
}
