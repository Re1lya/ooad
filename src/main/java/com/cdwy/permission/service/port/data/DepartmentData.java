package com.cdwy.permission.service.port.data;

/** DepartmentData 业务用例的不可变输入与结果。 */
public final class DepartmentData {
  private DepartmentData() {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Input(String name, Long parentId) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record View(Long id, String name, Long parentId, String managerName) {}
}
