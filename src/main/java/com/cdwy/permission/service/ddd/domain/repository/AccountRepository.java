package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/**
 * 账号仓储端口：读取、锁定、分页检索、保存和删除 {@link Account}，领域层不接触 JPA 实体。
 * 读出的账号是副本，修改后须调用 {@link #save} 才会写回。
 */
public interface AccountRepository {
  /** 按标识读取账号，不加锁，不过滤停用或过期账号；不存在时返回空 Optional，由调用方决定报何种错误。 */
  Optional<Account> find(Long id);

  /**
   * 按标识读取账号并加悲观写锁，锁持有到当前事务结束，须在事务内调用；不存在时返回空 Optional。
   * 用于串行化同一账号的资料、状态、密码修改和删除，避免凭证版本的递增被并发写覆盖。
   */
  Optional<Account> lock(Long id);

  /** 按登录名精确查找（区分大小写），不过滤停用或过期账号，可用性由调用方判断；不存在时返回空 Optional。 */
  Optional<Account> username(String name);

  /**
   * 读取全部账号（含停用和过期），按标识升序、不分页。
   * 供员工信息同步按登录名整体比对；列表和检索用数据库分页的 {@link #search}。
   */
  List<Account> all();

  /** 按登录名或姓名模糊查询，关键字为空时不过滤；页码从 1 开始，数据库分页。 */
  com.cdwy.permission.service.port.data.UserData.Page<Account> search(
      String keyword, int page, int pageSize);

  /**
   * 标识为空时新增，否则覆盖该标识的已有账号，已不存在则抛 {@code USER_NOT_FOUND}。
   * 立即写入数据库，登录名重复、部门不存在等约束冲突抛 {@code DATA_CONFLICT}；
   * 返回带数据库标识的新对象，入参不回填标识。
   */
  Account save(Account value);

  /** 批量保存（员工信息同步）：一次刷新，返回带标识的账号。 */
  List<Account> saveAll(List<Account> values);

  /**
   * 按标识删除账号，不存在时静默忽略。调用方须先做删除保护检查，并用
   * {@link AssignmentRepository#removeUserRoles} 解除其直接角色，否则会违反外键约束。
   */
  void delete(Long id);

  /** 部门是否仍有直属账号（含停用账号，不含下级部门的人员），{@code id} 为部门标识；删除部门前检查。 */
  boolean inDepartment(Long id);

  /** 部门下的账号，关键字匹配登录名或姓名；页码从 1 开始，按登录名排序，数据库分页。 */
  com.cdwy.permission.service.port.data.UserData.Page<Account> members(
      Long departmentId, String keyword, int page, int pageSize);
}
