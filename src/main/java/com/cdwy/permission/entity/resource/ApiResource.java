package com.cdwy.permission.entity.resource;

import jakarta.persistence.*;

@cn.edu.xmu.clonefactory.CopyTo(
    com.cdwy.permission.service.ddd.domain.model.ProtectedResource.class)
@cn.edu.xmu.clonefactory.CopyFrom(
    com.cdwy.permission.service.ddd.domain.model.ProtectedResource.class)
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
public class ApiResource {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
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

  @Column(nullable = false, length = 8)
  private String method;

  @Column(nullable = false, length = 255)
  private String path;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "system_id", nullable = false)
  private com.cdwy.permission.entity.system.BusinessSystem system;

  @Column(nullable = false)
  private boolean active = true;

  public ApiResource() {}

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String value) {
    code = value;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String value) {
    method = value;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String value) {
    path = value;
  }

  public com.cdwy.permission.entity.system.BusinessSystem getSystem() {
    return system;
  }

  public void setSystem(com.cdwy.permission.entity.system.BusinessSystem value) {
    system = value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }
}
