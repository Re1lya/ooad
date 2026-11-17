package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;

/** DepartmentNode 领域对象：只维护业务状态和不变量，不依赖 JPA。 */
@Getter
@Setter
public class DepartmentNode {
  private Long id;
  private String name;
  private Long parentId;
  private String managerName;

  /** 自身及任意祖先不得指回当前节点。 */
  public void relocate(String value, Long parent, java.util.Map<Long, DepartmentNode> nodes) {
    java.util.Set<Long> seen = new java.util.HashSet<>();
    for (Long next = parent; next != null; ) {
      if (java.util.Objects.equals(next, id) || !seen.add(next))
        throw new IllegalArgumentException("部门树不能形成循环");
      DepartmentNode n = nodes.get(next);
      if (n == null)
        throw new com.cdwy.permission.service.port.error.ResourceNotFoundException("部门不存在");
      next = n.parentId;
    }
    name = value;
    parentId = parent;
  }

  /** 子部门和员工仍引用时不能删除。 */
  public void assertDeletable(boolean referenced) {
    if (referenced)
      throw new com.cdwy.permission.service.port.error.ConflictException("部门仍有子部门或员工");
  }
}
