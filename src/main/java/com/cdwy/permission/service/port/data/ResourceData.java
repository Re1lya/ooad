package com.cdwy.permission.service.port.data;

/** API 资源用例（{@code ResourceUseCase}）的输入与结果；菜单或按钮绑定的资源列表也用 {@code View}。 */
public final class ResourceData {
  private ResourceData() {}

  /**
   * 登记 API 资源（{@code ResourceUseCase#create}）的输入；除名称外登记后都不可修改。
   *
   * @param systemId 所属应用
   * @param name 显示名称
   * @param method HTTP 方法：GET、POST、PUT、PATCH 或 DELETE
   * @param path 路径模式（Spring {@code PathPattern} 语法），以 / 开头，可含 {@code {id}} 这类变量，不含查询串，不支持
   *     {@code **}
   * @param code 下发给接入应用的后端权限码，可空
   * @param operationId 接口的 operationId，可空，同一应用内唯一
   */
  public record Create(
      Long systemId, String name, String method, String path, String code, String operationId) {}

  /**
   * 修改 API 资源（{@code ResourceUseCase#update}）的输入：只能改显示名称。
   *
   * @param name 显示名称
   */
  public record Update(String name) {}

  /**
   * 一个已登记的 API 资源：资源用例的结果，也是菜单或按钮所绑定资源列表的元素。
   *
   * @param id 标识
   * @param systemId 所属应用
   * @param name 显示名称
   * @param method HTTP 方法
   * @param path 路径模式
   * @param active 是否生效；停用的资源仍参与鉴权匹配，命中即拒绝
   * @param code 后端权限码，可空
   * @param operationId 接口的 operationId，可空
   */
  public record View(
      Long id,
      Long systemId,
      String name,
      String method,
      String path,
      boolean active,
      String code,
      String operationId) {}
}
