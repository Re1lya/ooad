package com.cdwy.permission.service.port.data;

/** 接入应用用例（{@code SystemUseCase}）的输入与结果。 */
public final class SystemData {
  private SystemData() {}

  /**
   * 登记接入应用（{@code SystemUseCase#create}）的输入。
   *
   * @param code 应用编码，全局唯一，创建后不可改；也是统一身份接口地址中的应用段和令牌 {@code aud} 的取值
   * @param name 名称
   * @param baseUrl 应用访问地址，可空
   */
  public record Create(String code, String name, String baseUrl) {}

  /**
   * 修改接入应用（{@code SystemUseCase#update}）的输入；编码不可修改。
   *
   * @param name 名称
   * @param baseUrl 应用访问地址，可空
   */
  public record Update(String name, String baseUrl) {}

  /**
   * 一个接入应用：应用用例的结果。
   *
   * @param id 标识
   * @param code 应用编码
   * @param name 名称
   * @param baseUrl 应用访问地址
   * @param enabled 是否启用；停用后该应用内的菜单、按钮与 API 资源都视为无效
   */
  public record View(Long id, String code, String name, String baseUrl, boolean enabled) {}
}
