package com.cdwy.permission.entity.resource;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;
import com.cdwy.permission.service.ddd.domain.model.ProtectedResource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * API 资源（表 {@code api_resource}）：应用登记的一个接口，方法与路径模式在应用内唯一；DDD 路线的持久化适配器按 {@code @CopyTo}/{@code
 * @CopyFrom} 与 {@link ProtectedResource} 互相拷贝。
 */
@CopyTo(ProtectedResource.class)
@CopyFrom(ProtectedResource.class)
@Entity
@Table(
    name = "api_resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_api_route",
            columnNames = {"system_id", "method", "path"}),
        @UniqueConstraint(
            name = "uk_api_operation",
            columnNames = {"system_id", "operation_id"})
    })
@Getter
@Setter
public class ApiResource {
  /** 主键由数据库生成：不提供 setter，从领域对象拷回实体时也不覆盖。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  @CopyFrom.Exclude(ProtectedResource.class)
  private Long id;

  @Column(nullable = false, length = 128)
  private String name;

  /**
   * 后端权限码（如课程系统的 TC、CV），接入应用据此做方法级授权；未登记时为空。
   * 同一权限码可以覆盖多个接口（例如 TCM 同时覆盖 PUT 与 DELETE /current-term），因此不设唯一约束。
   */
  @Column(length = 64)
  private String code;

  /** 接入应用 OpenAPI 中的 operationId，供统一身份接口按 operationId 判断能否调用；同一应用内唯一。 */
  @lombok.Getter
  @lombok.Setter
  @Column(name = "operation_id", length = 128)
  private String operationId;

  /** HTTP 方法：GET、POST、PUT、PATCH 或 DELETE。 */
  @Column(nullable = false, length = 8)
  private String method;

  /**
   * 路径模式，Spring {@code PathPattern} 语法，例如 {@code /api/users/{id}}；统一身份目录接口登记去掉
   * {@code /api/apps/{应用编码}} 前缀后的路径。鉴权时在同一应用、同一方法下取匹配的最具体模式，再检查绑定到它的菜单或按钮。
   */
  @Column(nullable = false, length = 255)
  private String path;

  /** 所属应用。 */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "system_id", nullable = false)
  private com.cdwy.permission.entity.system.BusinessSystem system;

  /** 启用状态：最具体的匹配资源已停用时直接拒绝请求，不回退到更宽泛的资源。 */
  @Column(nullable = false)
  private boolean active = true;

}
