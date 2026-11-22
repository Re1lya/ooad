package com.cdwy.permission.service.port.data;

/**
 * 前端权限的类型。请求 DTO、结构化实现的实体与内置授权目录使用本枚举；用例数据以枚举名称字符串传递，DDD 领域模型使用同名字符串常量。
 */
public enum PermissionType {
  /** 菜单：可设置菜单地址，可作为子菜单与按钮的上级。 */
  MENU,
  /** 按钮：必须隶属同一应用的菜单，不设置菜单地址，也不能作为其他节点的上级。 */
  BUTTON
}
