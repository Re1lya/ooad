package com.cdwy.permission.entity.rbac;

import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.entity.user.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 用户直接拥有的角色（表 {@code sys_user_role}）；经继承得到的角色不在此表，计算有效权限时再沿继承边展开。 */
@Entity
@Table(
    name = "sys_user_role",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_sys_user_role",
            columnNames = {"user_id", "role_id"}))
@Getter
@NoArgsConstructor
public class UserRole {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  /** 为用户直接授予角色；同一用户与角色只能有一条记录。 */
  public UserRole(UserAccount user, Role role) {
    this.user = user;
    this.role = role;
  }
}
