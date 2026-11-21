package com.cdwy.permission.entity.system;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;
import com.cdwy.permission.service.ddd.domain.model.ManagedSystem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 接入应用（表 {@code biz_system}），权限系统自身在启动时登记为 {@code rbac}；DDD 路线的持久化适配器按 {@code @CopyTo}/{@code
 * @CopyFrom} 与 {@link ManagedSystem} 互相拷贝。
 */
@CopyTo(ManagedSystem.class)
@CopyFrom(ManagedSystem.class)
@Entity
@Table(name = "biz_system")
@Getter
@Setter
public class BusinessSystem {
  /** 主键由数据库生成：不提供 setter，从领域对象拷回实体时也不覆盖。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  @CopyFrom.Exclude(ManagedSystem.class)
  private Long id;

  /** 应用编码，全局唯一；也是令牌 {@code aud} 的取值和 {@code /api/apps/{应用编码}} 路径中的应用段。 */
  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  /** 应用访问地址，只做登记展示，不参与鉴权。 */
  @Column(length = 255)
  private String baseUrl;

  /** 启用状态：停用后该应用的 API 资源不再参与鉴权匹配，菜单和按钮也不计入有效权限。 */
  @Column(nullable = false)
  private boolean enabled = true;

}
