package com.cdwy.permission.entity.role;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;
import com.cdwy.permission.service.ddd.domain.model.AccessRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色（表 {@code sys_role}），可经 {@code sys_role_hierarchy} 继承其他角色（RBAC1）；DDD 路线的持久化适配器按 {@code @CopyTo}
 * /{@code @CopyFrom} 与 {@link AccessRole} 互相拷贝。
 */
@CopyTo(AccessRole.class)
@CopyFrom(AccessRole.class)
@Entity
@Table(name = "sys_role")
@Getter
@Setter
public class Role {
  /** 主键由数据库生成：不提供 setter，从领域对象拷回实体时也不覆盖。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  @CopyFrom.Exclude(AccessRole.class)
  private Long id;

  /** 角色编码，全局唯一；接入应用按它识别身份，例如 {@code teacher}。 */
  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(length = 255)
  private String description;

  /** 启用状态：停用的角色不提供权限，并阻断经过它的继承路径；也不能再新增授权。 */
  @Column(nullable = false)
  private boolean enabled = true;

}
