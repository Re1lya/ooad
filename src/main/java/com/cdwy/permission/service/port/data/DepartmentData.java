package com.cdwy.permission.service.port.data;

/** 部门管理用例（{@code DepartmentUseCase}）的输入与结果，以及默认部门编码规则。 */
public final class DepartmentData {
  private DepartmentData() {}

  /** 编码可选：不填时新部门取 {@code D} 加四位标识，修改时保留原编码。 */
  public record Input(String name, Long parentId, String code) {}

  /**
   * 部门管理用例返回的一个部门。
   *
   * @param id 部门标识
   * @param name 名称，同一上级下唯一
   * @param parentId 上级部门标识，根部门为 null
   * @param code 部门编码，全局唯一；未指定时为默认编码
   */
  public record View(Long id, String name, Long parentId, String code) {}


  /** 未指定编码的部门的默认编码。 */
  public static String defaultCode(Long id) {
    return String.format("D%04d", id);
  }
}
