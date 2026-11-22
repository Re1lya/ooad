package com.cdwy.permission.entity.rbac;

import com.cdwy.permission.entity.permission.Permission;
import com.cdwy.permission.entity.resource.ApiResource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 前端权限与 API 资源的绑定（表 {@code permission_api}，同一对只绑定一次）：用户的有效权限中有任一菜单或按钮绑定到某个 API 资源时，才能调用该接口。 */
@Entity
@Table(
    name = "permission_api",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_permission_api",
            columnNames = {"permission_id", "resource_id"}))
@Getter
@NoArgsConstructor
public class PermissionApi {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "permission_id", nullable = false)
  private Permission permission;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resource_id", nullable = false)
  private ApiResource resource;

  /** 把前端权限绑定到它要调用的 API 资源；二者须属于同一应用。 */
  public PermissionApi(Permission permission, ApiResource resource) {
    this.permission = permission;
    this.resource = resource;
  }
}
