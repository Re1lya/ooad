package com.cdwy.permission.repository.rbac;

import com.cdwy.permission.entity.rbac.RoleHierarchy;
import org.springframework.data.jpa.repository.JpaRepository;

/** 角色继承关系持久化；业务层负责防环及状态语义。 */
public interface RoleHierarchyRepository extends JpaRepository<RoleHierarchy, Long> {
  /** 判断角色是否仍被任一方向的继承关系引用。 */
  boolean existsBySeniorRole_IdOrInheritedRole_Id(Long seniorId, Long inheritedId);

  /** 检查重复边，支持幂等创建。 */
  boolean existsBySeniorRole_IdAndInheritedRole_Id(Long seniorId, Long inheritedId);

  /** 幂等解除指定继承边。 */
  void deleteBySeniorRole_IdAndInheritedRole_Id(Long seniorId, Long inheritedId);
}
