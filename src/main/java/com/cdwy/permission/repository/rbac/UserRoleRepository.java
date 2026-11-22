package com.cdwy.permission.repository.rbac;

import com.cdwy.permission.entity.rbac.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

/** 用户与角色的直接分配关系仓储：供授权管理、有效权限计算、审计操作人快照和员工信息同步使用；角色继承在鉴权时展开，不写入此表。 */
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
  /** 用户直接分配的全部角色，不含继承所得，顺序不保证，也不过滤已停用的角色；无分配时为空列表。 */
  java.util.List<UserRole> findAllByUser_Id(Long id);

  /** 用户是否已直接分配该角色；授予前检查，已分配时跳过写入（幂等授予）。 */
  boolean existsByUser_IdAndRole_Id(Long userId, Long roleId);

  /** 是否有任一用户直接分配了该角色；删除角色前检查。 */
  boolean existsByRole_Id(Long id);

  /** 撤销用户的一个直接角色：先查出再逐条删除，不存在时不报错（幂等）；须在事务内调用。 */
  void deleteByUser_IdAndRole_Id(Long userId, Long roleId);

  /** 删除用户的全部直接角色分配，在删除账号前调用：先查出再逐条删除；须在事务内调用。 */
  void deleteAllByUser_Id(Long id);

  /** 持有某个角色的全部用户标识（员工信息同步据此判断是否需要补授）。 */
  @org.springframework.data.jpa.repository.Query(
      "select ur.user.id from UserRole ur where ur.role.id = :roleId")
  java.util.List<Long> holders(
      @org.springframework.data.repository.query.Param("roleId") Long roleId);
}
