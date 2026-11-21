package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;

/** FrontendPermission 领域对象：只维护业务状态和不变量，不依赖 JPA。 */
@Getter
@Setter
public class FrontendPermission {
  private Long id;
  private Long systemId;
  private String code;
  private String name;
  private String kind = MENU;
  private String menuPath;
  private Long parentId;
  private boolean active = true;

  public static final String MENU = "MENU", BUTTON = "BUTTON";

  /** 维护同应用菜单父级、无环、按钮无地址等不变量。 */
  public void edit(
      String value,
      String type,
      String path,
      Long parent,
      java.util.Map<Long, FrontendPermission> nodes,
      boolean hasChildren) {
    FrontendPermission p = parent == null ? null : nodes.get(parent);
    if (parent != null && p == null)
      throw new com.cdwy.permission.service.port.error.ResourceNotFoundException("前端权限不存在");
    if (BUTTON.equals(type) && p == null) throw new IllegalArgumentException("按钮必须隶属菜单");
    if (p != null && (!MENU.equals(p.kind) || !java.util.Objects.equals(systemId, p.systemId)))
      throw new IllegalArgumentException("父节点必须是同一应用的菜单");
    java.util.Set<Long> seen = new java.util.HashSet<>();
    for (FrontendPermission n = p; n != null; n = nodes.get(n.parentId))
      if (java.util.Objects.equals(n.id, id) || !seen.add(n.id))
        throw new IllegalArgumentException("权限树不能形成循环");
    if (BUTTON.equals(type) && id != null && hasChildren)
      throw new com.cdwy.permission.service.port.error.ConflictException("有子节点的菜单不能改为按钮");
    if (BUTTON.equals(type) && path != null && !path.isBlank())
      throw new IllegalArgumentException("按钮不设置菜单地址");
    name = value;
    kind = type;
    menuPath = path == null || path.isBlank() ? null : path;
    parentId = parent;
  }

  /** 停用仍保留原授权关系，后续计算实时排除。 */
  public void changeStatus(boolean value) {
    active = value;
  }

  /** 关系未解除不允许删除。 */
  public void assertDeletable(boolean referenced) {
    if (referenced)
      throw new com.cdwy.permission.service.port.error.ConflictException("仍有子节点、角色授权或 API 绑定");
  }

  /** 同一业务系统内才允许绑定前端权限和 API 资源。 */
  public void assertBindable(ProtectedResource resource) {
    if (!java.util.Objects.equals(systemId, resource.getSystemId()))
      throw new IllegalArgumentException("前端权限与 API 资源必须属于同一应用");
  }

  /** CloneFactory 的布尔 get 访问器约定。 */
  public boolean getActive() {
    return active;
  }
}
