package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;

/** AccessRole 领域对象：只维护业务状态和不变量，不依赖 JPA。 */
@Getter
@Setter
public class AccessRole {
  private Long id;
  private String code;
  private String name;
  private String description;
  private boolean enabled = true;

  /** 角色业务继承由 RoleGraph 表达，不使用 Java extends。 */
  public void rename(String value, String detail) {
    name = value;
    description = detail;
  }

  /**
   * 启停角色；停用节点阻断所有经过它的继承路径。内置管理员角色不能停用，否则管理员会被锁在控制台之外。
   *
   * @param value 启用为 true
   * @param builtIn 是否为内置管理员角色，由应用服务按内置目录判断
   */
  public void changeStatus(boolean value, boolean builtIn) {
    if (!value) assertNotBuiltIn(builtIn);
    enabled = value;
  }

  /**
   * 删除前检查：内置管理员角色抛 {@code CONSOLE_BUILT_IN}；仍是继承关系一端抛 {@code ROLE_HAS_INHERITANCE}；仍有分配或授权抛
   * {@code ROLE_IN_USE}（均 409）。
   *
   * @param builtIn 是否为内置管理员角色
   * @param hierarchy 是否仍是任一继承关系的一端
   * @param granted 是否仍分配给用户或持有权限
   */
  public void assertDeletable(boolean builtIn, boolean hierarchy, boolean granted) {
    assertNotBuiltIn(builtIn);
    if (hierarchy)
      throw new com.cdwy.permission.service.port.error.ConflictException("ROLE_HAS_INHERITANCE", "仍有角色继承关系，请先解除关联");
    if (granted)
      throw new com.cdwy.permission.service.port.error.ConflictException("ROLE_IN_USE", "仍有授权或资源引用，请先解除关联");
  }

  private static void assertNotBuiltIn(boolean builtIn) {
    if (builtIn)
      throw new com.cdwy.permission.service.port.error.ConflictException("CONSOLE_BUILT_IN", "内置管理员角色不能停用或删除");
  }
}
