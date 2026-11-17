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

  /** 停用节点阻断所有经过该节点的继承路径。 */
  public void changeStatus(boolean value) {
    enabled = value;
  }

  /** 授权和继承关系必须先解除再删除角色。 */
  public void assertDeletable(boolean hierarchy, boolean granted) {
    if (hierarchy)
      throw new com.cdwy.permission.service.port.error.ConflictException("仍有角色继承关系，请先解除关联");
    if (granted)
      throw new com.cdwy.permission.service.port.error.ConflictException("仍有授权或资源引用，请先解除关联");
  }

  /** CloneFactory 的布尔 get 访问器约定。 */
  public boolean getEnabled() {
    return enabled;
  }
}
