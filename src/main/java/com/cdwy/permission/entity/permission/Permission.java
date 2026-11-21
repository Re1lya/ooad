package com.cdwy.permission.entity.permission;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;
import com.cdwy.permission.service.ddd.domain.model.FrontendPermission;
import com.cdwy.permission.service.port.data.PermissionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 前端权限（表 {@code sys_permission}）：应用的菜单或按钮，按 {@code parent} 组成菜单树；DDD 路线的持久化适配器按 {@code @CopyTo}
 * /{@code @CopyFrom} 与 {@link FrontendPermission} 互相拷贝。
 */
@CopyTo(FrontendPermission.class)
@CopyFrom(FrontendPermission.class)
@Entity
@Table(name = "sys_permission")
@Getter
@Setter
public class Permission {
  /** 主键由数据库生成：不提供 setter，从领域对象拷回实体时也不覆盖。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  @CopyFrom.Exclude(FrontendPermission.class)
  private Long id;

  /** 权限编码，全局唯一。 */
  @Column(nullable = false, unique = true, length = 128)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  /** 菜单或按钮；按钮必须隶属同一应用的菜单。 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PermissionType type;

  /** 菜单地址（前端路由）；按钮不设置。 */
  @Column(length = 255)
  private String menuPath;

  /** 上级菜单；为空表示顶层菜单，按钮必须有上级。 */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Permission parent;

  /** 所属应用。 */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "system_id", nullable = false)
  private com.cdwy.permission.entity.system.BusinessSystem system;

  /** 启用状态：自身、任一上级或所属应用停用时，该权限不计入有效权限。 */
  @Column(nullable = false)
  private boolean active = true;

}
