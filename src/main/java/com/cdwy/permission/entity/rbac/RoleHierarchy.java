package com.cdwy.permission.entity.rbac;

import com.cdwy.permission.entity.role.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 角色继承边：高级角色获得被继承角色的权限，与部门树、菜单树独立。 */
@Entity
@Table(
    name = "sys_role_hierarchy",
    uniqueConstraints = @UniqueConstraint(columnNames = {"senior_role_id", "inherited_role_id"}))
@org.hibernate.annotations.Check(constraints = "senior_role_id <> inherited_role_id")
@Getter
@NoArgsConstructor
public class RoleHierarchy {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "senior_role_id", nullable = false)
  private Role seniorRole;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "inherited_role_id", nullable = false)
  private Role inheritedRole;

  /** 构造有方向的继承边；无环约束由继承服务在事务内校验。 */
  public RoleHierarchy(Role seniorRole, Role inheritedRole) {
    this.seniorRole = seniorRole;
    this.inheritedRole = inheritedRole;
  }
}
