package com.cdwy.permission.repository.role;

import com.cdwy.permission.entity.role.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
  /** 以固定顺序锁定角色，在教学规模内串行化继承图写入，避免并发反向加边。 */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select r from Role r order by r.id")
  java.util.List<Role> lockHierarchyRoles();

  boolean existsByCode(String code);
}
