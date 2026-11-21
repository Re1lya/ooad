package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;
import com.cdwy.permission.service.port.error.InvalidDataException;

/** DepartmentNode 领域对象：只维护业务状态和不变量，不依赖 JPA。 */
@Getter
@Setter
public class DepartmentNode {
  private Long id;
  private String name;
  private Long parentId;
  private String code;

  /**
   * 同级名称唯一、编码全局唯一（信息专家：部门知道自己的名称、上级和编码）。
   *
   * @param requested 新编码；为空时保留原编码
   */
  public void assertUnique(
      String requested, java.util.Collection<DepartmentNode> all, Long newParent, String newName) {
    for (DepartmentNode other : all) {
      if (java.util.Objects.equals(other.id, id)) continue;
      if (java.util.Objects.equals(other.parentId, newParent) && other.name.equals(newName))
        throw new com.cdwy.permission.service.port.error.ConflictException("DEPARTMENT_NAME_EXISTS", "同一上级下已有同名部门");
      if (requested != null && requested.equals(other.code))
        throw new com.cdwy.permission.service.port.error.ConflictException("DEPARTMENT_CODE_EXISTS", "部门编码已存在");
    }
    if (requested != null) {
      if (!requested.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"))
        throw new com.cdwy.permission.service.port.error.InvalidDataException(
            "DEPARTMENT_CODE_INVALID", "部门编码须以字母或数字开头，只含字母、数字和 _.-，最长 64 位");
      code = requested;
    }
  }

  /** 保存后仍无编码时取默认编码；返回是否需要再次保存。 */
  public boolean assignDefaultCode() {
    if (code != null) return false;
    code = com.cdwy.permission.service.port.data.DepartmentData.defaultCode(id);
    return true;
  }

  /** 自身及任意祖先不得指回当前节点。 */
  public void relocate(String value, Long parent, java.util.Map<Long, DepartmentNode> nodes) {
    java.util.Set<Long> seen = new java.util.HashSet<>();
    for (Long next = parent; next != null; ) {
      if (java.util.Objects.equals(next, id) || !seen.add(next))
        throw new InvalidDataException("DEPARTMENT_CYCLE", "部门树不能形成循环");
      DepartmentNode n = nodes.get(next);
      if (n == null)
        throw new com.cdwy.permission.service.port.error.ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "部门不存在");
      next = n.parentId;
    }
    name = value;
    parentId = parent;
  }

  /** 子部门和员工仍引用时不能删除。 */
  public void assertDeletable(boolean referenced) {
    if (referenced)
      throw new com.cdwy.permission.service.port.error.ConflictException("DEPARTMENT_IN_USE", "部门仍有子部门或员工");
  }
}
