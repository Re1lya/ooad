package com.cdwy.permission.service.port.data;

/** 用户用例（{@code UserUseCase}）的修改输入与分页结果；{@code Page} 也被 DDD 仓储端口的分页查询复用。 */
public final class UserData {
  private UserData() {}

  /**
   * 修改人员资料（{@code UserUseCase#update}）的输入；登录名、密码与启用状态由其他用例修改。
   *
   * @param displayName 姓名
   * @param departmentId 所属部门，为空表示清除部门
   * @param personType {@link PersonType} 的名称
   * @param expiresOn 有效期截止日（项目时区，含当天）；正式员工必须为空，外部与临时人员必填
   */
  public record Update(
      String displayName, Long departmentId, String personType, java.time.LocalDate expiresOn) {}

  /**
   * 页码从 1 开始的分页结果。
   *
   * @param page 页码
   * @param pageSize 每页条数
   * @param total 总条数
   * @param items 本页数据
   */
  public record Page<T>(int page, int pageSize, long total, java.util.List<T> items) {}
}
