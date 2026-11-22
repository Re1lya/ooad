package com.cdwy.permission.repository.role;

import com.cdwy.permission.entity.role.Role;
import org.springframework.data.jpa.repository.JpaRepository;

/** 角色仓储：角色编码、名称与启用状态，供角色管理、授权和员工信息同步使用；继承图写入前在此锁定全部角色。 */
public interface RoleRepository extends JpaRepository<Role, Long> {
  /** 以固定顺序锁定角色，在教学规模内串行化继承图写入，避免并发反向加边。 */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select r from Role r order by r.id")
  java.util.List<Role> lockHierarchyRoles();

  /** 编码是否已被占用：精确匹配（区分大小写），含已停用角色。 */
  boolean existsByCode(String code);

  /** 按编码精确查找角色（区分大小写），含已停用角色；空表示该编码的角色不存在。 */
  java.util.Optional<Role> findByCode(String code);
}
