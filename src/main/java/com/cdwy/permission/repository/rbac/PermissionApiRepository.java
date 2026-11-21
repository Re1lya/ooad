package com.cdwy.permission.repository.rbac;

import com.cdwy.permission.entity.rbac.PermissionApi;
import org.springframework.data.jpa.repository.JpaRepository;

/** 前端权限与 API 资源的绑定关系仓储：供绑定管理、接口鉴权（由资源反查前端权限）和统一身份汇总后端权限码使用。 */
public interface PermissionApiRepository extends JpaRepository<PermissionApi, Long> {
  /** 某前端权限的全部 API 绑定，顺序不保证，也不过滤资源的启用状态；无绑定时为空列表。 */
  java.util.List<PermissionApi> findAllByPermission_Id(Long id);

  /** 某 API 资源的全部权限绑定，顺序不保证，也不过滤权限的启用状态，由调用方与用户有效权限求交；无绑定时为空列表。 */
  java.util.List<PermissionApi> findAllByResource_Id(Long id);

  /** 该权限与资源是否已绑定；绑定前检查，已绑定时跳过写入（幂等绑定）。 */
  boolean existsByPermission_IdAndResource_Id(Long permissionId, Long resourceId);

  /** 前端权限是否仍绑定任一 API 资源；删除权限前检查。 */
  boolean existsByPermission_Id(Long id);

  /** API 资源是否仍被任一前端权限绑定；删除资源前检查。 */
  boolean existsByResource_Id(Long id);

  /** 解除一条绑定：先查出再逐条删除，不存在时不报错（幂等解绑）；须在事务内调用。 */
  void deleteByPermission_IdAndResource_Id(Long permissionId, Long resourceId);
}
