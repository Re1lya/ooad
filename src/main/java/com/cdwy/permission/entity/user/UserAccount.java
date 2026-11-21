package com.cdwy.permission.entity.user;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;
import com.cdwy.permission.service.ddd.domain.model.Account;
import com.cdwy.permission.service.port.data.CreateUserCommand;
import com.cdwy.permission.service.port.data.PersonType;
import com.cdwy.permission.service.port.data.UserResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户账号：人员信息存表 {@code sys_user}，登录名与密码散列存副表 {@code sys_account}；DDD 路线据 {@code @CopyTo}/{@code
 * @CopyFrom} 与 {@link Account} 互相拷贝，结构化路线由 {@link CreateUserCommand} 建实体、拷出 {@link UserResult}。
 */
@CopyTo({Account.class, UserResult.class})
@CopyFrom({Account.class, CreateUserCommand.class})
@Entity
@Table(name = "sys_user")
@SecondaryTable(
    name = "sys_account",
    pkJoinColumns = @PrimaryKeyJoinColumn(name = "user_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_account_username", columnNames = "username"))
@Getter
@Setter
public class UserAccount {
  /** 主键由数据库生成：不提供 setter，从领域对象拷回实体时也不覆盖。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  @CopyFrom.Exclude(Account.class)
  private Long id;

  /** 登录名，存于副表、全局唯一；也是统一身份接口中的用户标识（工号或学号）。 */
  @Column(table = "sys_account", nullable = false, length = 64)
  private String username;

  /** 密码的 BCrypt 散列，存于副表；员工信息同步新建的人员是任何密码都无法匹配的占位值。 */
  @Column(table = "sys_account", nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 128)
  private String displayName;

  /** 启用状态：停用后不能登录，已签发的令牌失效，也不计入有效权限。 */
  @Column(nullable = false)
  private boolean enabled = true;

  /** 管理员标志，只由启动初始化设置：不能被停用、删除或改为限期人员，员工信息同步也不能覆盖；接口访问仍按 RBAC 授权。 */
  @Column(nullable = false)
  private boolean administrator = false;

  /** 凭证版本，写入令牌的 {@code cv} 声明：退出登录、修改密码、启用或停用时递增，版本不一致的令牌即失效。 */
  @Column(nullable = false)
  private long credentialVersion = 0;

  /** 人员身份：正式员工长期有效；外部人员和临时人员必须设置有效期，上限见 {@link com.cdwy.permission.config.AccessPolicy}。 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PersonType personType = PersonType.EMPLOYEE;

  /** 有效期截止时刻（不含）：所选到期日次日零点（配置时区）；为空表示长期有效。 */
  private java.time.Instant expiresAt;

  /** 所属部门，可为空。 */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private com.cdwy.permission.entity.organization.Department department;

  /** 所属部门的 ID；未分配部门时返回 {@code null}。 */
  public Long getDepartmentId() {
    return department == null ? null : department.getId();
  }

  /** 账号在 {@code now} 时是否可用：已启用且未过有效期；登录、令牌校验和有效权限计算都以此为准。 */
  public boolean usable(java.time.Instant now) {
    return enabled && (expiresAt == null || expiresAt.isAfter(now));
  }

  /** 导入表中的原始来源字段，普通编辑不隐式覆盖。 */
  @lombok.Getter
  @lombok.Setter
  @Column(length = 128)
  private String companyName;

  /** 导入表中的原始来源字段，普通编辑不隐式覆盖。 */
  @lombok.Getter
  @lombok.Setter
  @Column(length = 128)
  private String jobTitle;

  /** 导入表中的原始来源字段，普通编辑不隐式覆盖。 */
  @lombok.Getter
  @lombok.Setter
  @Column(length = 32)
  private String phone;

  /** 导入表中的原始来源字段，普通编辑不隐式覆盖。 */
  @lombok.Getter
  @lombok.Setter
  @Column(length = 128)
  private String sourceDepartment;
}
