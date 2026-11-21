package com.cdwy.permission.repository.user;

import com.cdwy.permission.entity.user.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
  java.util.Optional<UserAccount> findByUsername(String username);

  boolean existsByUsername(String username);

  boolean existsByAdministratorTrue();

  boolean existsByDepartment_Id(Long departmentId);

  /** 部门下的用户：pattern 来自 {@code LikePattern}，匹配登录名（工号/学号）或姓名；数据库分页。 */
  @org.springframework.data.jpa.repository.Query(
      "select u from UserAccount u where u.department.id = :departmentId"
          + " and (:pattern is null or lower(u.username) like :pattern escape '!'"
          + " or lower(u.displayName) like :pattern escape '!')")
  org.springframework.data.domain.Page<UserAccount> members(
      @org.springframework.data.repository.query.Param("departmentId") Long departmentId,
      @org.springframework.data.repository.query.Param("pattern") String pattern,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.id=:id")
  java.util.Optional<UserAccount> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
