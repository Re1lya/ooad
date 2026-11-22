package com.cdwy.permission.service.port.data;

/** 菜单与按钮（前端权限）的用例数据：{@code PermissionUseCase} 的输入与结果，也用于授权用例的有效权限与菜单树。 */
public final class PermissionData {
  private PermissionData() {}

  /**
   * 新建菜单或按钮（{@code PermissionUseCase#create}）的输入。
   *
   * @param systemId 所属应用，创建后不可改
   * @param code 权限编码，全局唯一，创建后不可改
   * @param name 名称
   * @param type {@link PermissionType} 的名称
   * @param menuPath 菜单地址，按钮必须为空；空白视为未填
   * @param parentId 上级菜单，须是同一应用的菜单；按钮必填，为空表示一级菜单
   */
  public record Input(
      Long systemId, String code, String name, String type, String menuPath, Long parentId) {}

  /**
   * 修改菜单或按钮（{@code PermissionUseCase#update}）的输入；编码、所属应用和生效状态不经此修改。
   *
   * @param name 名称
   * @param type {@link PermissionType} 的名称
   * @param menuPath 菜单地址，按钮必须为空；空白视为未填
   * @param parentId 上级菜单，规则同新建
   */
  public record Update(String name, String type, String menuPath, Long parentId) {}

  /**
   * 一个菜单或按钮：权限用例的查询与写入结果，也用于角色授权、有效权限和权限来源。
   *
   * @param id 标识
   * @param systemId 所属应用
   * @param code 权限编码
   * @param name 名称
   * @param type {@link PermissionType} 的名称
   * @param menuPath 菜单地址，按钮为 null
   * @param parentId 上级菜单，一级菜单为 null
   * @param active 自身是否生效；能否计入有效权限还取决于各级上级与所属应用
   */
  public record View(
      Long id,
      Long systemId,
      String code,
      String name,
      String type,
      String menuPath,
      Long parentId,
      boolean active) {}

  /**
   * 菜单树的一个节点：管理端目录树（{@code PermissionUseCase#tree}）与用户菜单（{@code AuthorizationUseCase#menu}）共用。
   *
   * @param permission 节点本身
   * @param granted 是否属于构树时给定的权限集合：用户菜单中即是否实际拥有，补出的祖先为 false；目录树中都为 true
   * @param children 下级节点
   */
  public record Tree(View permission, boolean granted, java.util.List<Tree> children) {}
}
