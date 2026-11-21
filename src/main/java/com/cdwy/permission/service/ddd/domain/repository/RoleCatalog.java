package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/**
 * 角色目录仓储端口：读取、保存和删除 {@link AccessRole}，并提供串行化继承图写入的锁；领域层不接触 JPA 实体。
 * 读出的角色是副本，修改后须调用 {@link #save} 才会写回。
 */
public interface RoleCatalog {
  /** 按标识读取角色（含已停用），不加锁；不存在时返回空 Optional，由应用服务抛 {@code ROLE_NOT_FOUND}。 */
  Optional<AccessRole> find(Long id);

  /** 读取全部角色（含已停用），按标识升序、不分页；供角色列表、编码查重、按编码查找和授权快照使用。 */
  List<AccessRole> all();

  /**
   * 标识为空时新增，否则覆盖该标识的已有角色，已不存在则抛 {@code ROLE_NOT_FOUND}。
   * 立即写入，编码重复等约束冲突抛 {@code DATA_CONFLICT}；返回带数据库标识的新对象，入参不回填标识。
   */
  AccessRole save(AccessRole value);

  /**
   * 按标识删除角色，不存在时静默忽略；调用方须先 {@link #lockGraph}，并确认没有继承边、用户分配和权限授权，
   * 否则会违反外键约束。
   */
  void delete(Long id);

  /**
   * 按标识顺序给全部角色加悲观写锁，锁持有到当前事务结束，须在事务内调用；
   * 继承边增删和角色删除都先调用它，防止并发加边绕过防环检查。
   */
  void lockGraph();
}
