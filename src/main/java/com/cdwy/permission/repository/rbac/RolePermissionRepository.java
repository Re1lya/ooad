package com.cdwy.permission.repository.rbac;

import com.cdwy.permission.entity.rbac.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

/** 角色与前端权限的直接授权关系仓储：供授权管理和有效权限计算使用；继承所得的权限在鉴权时展开，不写入此表。 */
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
  /** 角色直接授予的全部权限，不含继承所得，顺序不保证，也不过滤已停用的权限；无授权时为空列表。 */
  java.util.List<RolePermission> findAllByRole_Id(Long id);

  /** 角色是否已直接拥有该权限；授权前检查，已授予时跳过写入（幂等授权）。 */
  boolean existsByRole_IdAndPermission_Id(Long roleId, Long permissionId);

  /** 是否有任一角色直接拥有该权限；删除权限前检查。 */
  boolean existsByPermission_Id(Long id);

  /** 角色是否还直接拥有任何权限；删除角色前检查。 */
  boolean existsByRole_Id(Long id);

  /** 撤回角色的一条直接授权：先查出再逐条删除，不存在时不报错（幂等）；须在事务内调用。 */
  void deleteByRole_IdAndPermission_Id(Long roleId, Long permissionId);
}
