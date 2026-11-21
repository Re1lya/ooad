package com.cdwy.permission.service.port.data;

/** 角色用例（{@code RoleUseCase}）的输入与结果；授权用例列出用户的直接角色时也用 {@code View}。 */
public final class RoleData {
  private RoleData() {}

  /**
   * 新建角色（{@code RoleUseCase#create}）的输入。
   *
   * @param code 角色编码，全局唯一，创建后不可改；接入应用按它识别身份，例如 {@code teacher}
   * @param name 名称
   * @param description 说明，可空
   */
  public record Create(String code, String name, String description) {}

  /**
   * 修改角色（{@code RoleUseCase#update}）的输入；编码不可修改。
   *
   * @param name 名称
   * @param description 说明，可空
   */
  public record Update(String name, String description) {}

  /**
   * 一个角色：角色用例的结果，也是用户直接角色列表的元素。
   *
   * @param id 标识
   * @param code 角色编码
   * @param name 名称
   * @param description 说明
   * @param enabled 是否启用；停用的角色不提供权限，并阻断经过它的继承路径
   */
  public record View(Long id, String code, String name, String description, boolean enabled) {}
}
