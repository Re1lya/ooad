package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/**
 * 部门目录仓储端口：读取、保存和删除 {@link DepartmentNode} 组成的部门树，提供删除前检查和按层分页查询；
 * 领域层不接触 JPA 实体。读出的部门是副本，修改后须调用 {@link #save} 才会写回。
 */
public interface DepartmentDirectory {
  /** 按标识读取部门；不存在时返回空 Optional，由调用方决定报何种错误（如 {@code DEPARTMENT_NOT_FOUND}）。 */
  Optional<DepartmentNode> find(Long id);

  /** 读取全部部门，按标识升序、不分页；供部门列表、整树防环与唯一性校验以及员工信息同步使用。 */
  List<DepartmentNode> all();

  /**
   * 标识为空时新增，否则覆盖该标识的已有部门，已不存在则抛 {@code DEPARTMENT_NOT_FOUND}。
   * 立即写入，编码重复、同一上级下重名或上级不存在等约束冲突抛 {@code DATA_CONFLICT}，
   * 但根部门之间重名不受数据库约束，只靠领域校验。返回带数据库标识的新对象，入参不回填标识。
   */
  DepartmentNode save(DepartmentNode value);

  /**
   * 按标识删除部门，不存在时静默忽略；调用方须先确认没有子部门和直属账号，
   * 否则会违反外键约束。
   */
  void delete(Long id);

  /** 是否有直接子部门（只看下一层）；删除部门前检查。 */
  boolean hasChildren(Long id);

  /** 一层部门（parentId 为空时为根部门），关键字匹配名称或编码；页码从 1 开始，数据库分页。 */
  com.cdwy.permission.service.port.data.UserData.Page<DepartmentNode> level(
      Long parentId, String keyword, int page, int pageSize);

  /** ids 中有直接子部门的部门标识。 */
  java.util.Set<Long> withChildren(java.util.Collection<Long> ids);
}
