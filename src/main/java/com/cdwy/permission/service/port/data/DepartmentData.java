package com.cdwy.permission.service.port.data;

/** DepartmentData 业务用例的不可变输入与结果。 */
public final class DepartmentData {
  private DepartmentData() {}

  /** 编码可选：不填时新部门取 {@code D} 加四位标识，修改时保留原编码。 */
  public record Input(String name, Long parentId, String code) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record View(Long id, String name, Long parentId, String code) {}

  /** 部门编码规则：字母或数字开头，只含字母、数字和 _.-，最长 64 位。 */
  public static String requireCode(String code) {
    if (!code.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"))
      throw new IllegalArgumentException("部门编码须以字母或数字开头，只含字母、数字和 _.-，最长 64 位");
    return code;
  }

  /** 未指定编码的部门的默认编码。 */
  public static String defaultCode(Long id) {
    return String.format("D%04d", id);
  }
}
