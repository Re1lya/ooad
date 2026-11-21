package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/**
 * 接入应用目录仓储端口：读取、保存和删除 {@link ManagedSystem}；没有按编码查询的方法，调用方在 {@link #all} 的结果中查找。
 * 领域层不接触 JPA 实体；读出的应用是副本，修改后须调用 {@link #save} 才会写回。
 */
public interface SystemCatalog {
  /** 按标识读取接入应用（含已停用）；不存在时返回空 Optional，由应用服务抛 {@code SYSTEM_NOT_FOUND}。 */
  Optional<ManagedSystem> find(Long id);

  /**
   * 读取全部接入应用（含已停用），按标识升序、不分页；
   * 按编码定位应用、校验令牌目标系统和编码查重都基于此结果。
   */
  List<ManagedSystem> all();

  /**
   * 标识为空时新增，否则覆盖该标识的已有应用，已不存在则抛 {@code SYSTEM_NOT_FOUND}。
   * 立即写入，编码重复等约束冲突抛 {@code DATA_CONFLICT}；返回带数据库标识的新对象，入参不回填标识。
   */
  ManagedSystem save(ManagedSystem value);

  /**
   * 按标识删除接入应用，不存在时静默忽略；调用方须先确认应用下没有前端权限和 API 资源，
   * 否则会违反外键约束。
   */
  void delete(Long id);
}
