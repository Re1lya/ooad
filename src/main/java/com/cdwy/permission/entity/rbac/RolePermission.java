package com.cdwy.permission.entity.rbac;

import com.cdwy.permission.entity.permission.Permission;
import com.cdwy.permission.entity.role.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 角色与前端权限的授权关系（表 {@code sys_role_permission}）；同一角色与权限只有一条记录。 */
@Entity
@Table(
    name = "sys_role_permission",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_sys_role_permission",
            columnNames = {"role_id", "permission_id"}))
@Getter
@NoArgsConstructor
public class RolePermission {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "permission_id", nullable = false)
  private Permission permission;

  /** 为角色授予前端权限；同一角色与权限只能有一条记录。 */
  public RolePermission(Role role, Permission permission) {
    this.role = role;
    this.permission = permission;
  }
}
