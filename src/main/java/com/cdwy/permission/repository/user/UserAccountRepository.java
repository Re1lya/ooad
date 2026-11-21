package com.cdwy.permission.repository.user;

import com.cdwy.permission.entity.user.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

/** 用户账号仓储：人员资料、登录名与密码散列、启用状态和有效期，供用户管理、登录认证、员工信息同步和统一身份接口使用。 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
  /** 按登录名（工号/学号）精确查找，区分大小写；不过滤停用或过期账号，可用性由调用方判断；空表示无此登录名。 */
  java.util.Optional<UserAccount> findByUsername(String username);

  /** 登录名是否已被占用：精确匹配（区分大小写），含停用和过期账号；新建用户前查重。 */
  boolean existsByUsername(String username);

  /** 是否已有任一管理员账号（不论启用状态和有效期）；启动时只在尚无管理员时自动创建首个管理员。 */
  boolean existsByAdministratorTrue();

  /** 部门是否仍有直属账号（不含下级部门的人员，含停用账号）；删除部门前检查。 */
  boolean existsByDepartment_Id(Long departmentId);

  /** 按登录名或姓名模糊查询；pattern 为空时不过滤；数据库分页。 */
  @org.springframework.data.jpa.repository.Query(
      "select u from UserAccount u where :pattern is null"
          + " or lower(u.username) like :pattern escape '!'"
          + " or lower(u.displayName) like :pattern escape '!'")
  org.springframework.data.domain.Page<UserAccount> search(
      @org.springframework.data.repository.query.Param("pattern") String pattern,
      org.springframework.data.domain.Pageable pageable);

  /** 部门下的用户：pattern 来自 {@code LikePattern}，匹配登录名（工号/学号）或姓名；数据库分页。 */
  @org.springframework.data.jpa.repository.Query(
      "select u from UserAccount u where u.department.id = :departmentId"
          + " and (:pattern is null or lower(u.username) like :pattern escape '!'"
          + " or lower(u.displayName) like :pattern escape '!')")
  org.springframework.data.domain.Page<UserAccount> members(
      @org.springframework.data.repository.query.Param("departmentId") Long departmentId,
      @org.springframework.data.repository.query.Param("pattern") String pattern,
      org.springframework.data.domain.Pageable pageable);

  /** 按标识读取账号并加悲观写锁，串行化同一账号的并发修改与删除（资料、状态、密码、凭证版本）；须在事务内调用，锁持有到事务结束；空表示账号不存在。 */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.id=:id")
  java.util.Optional<UserAccount> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
