package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/**
 * API 资源目录仓储端口：读取、保存和删除接入应用登记的 {@link ProtectedResource}，并为删除应用提供引用检查；
 * 领域层不接触 JPA 实体。读出的资源是副本，修改后须调用 {@link #save} 才会写回。
 */
public interface ResourceCatalog {
  /** 按标识读取 API 资源（含已停用）；不存在时返回空 Optional，由应用服务抛 {@code API_RESOURCE_NOT_FOUND}。 */
  Optional<ProtectedResource> find(Long id);

  /**
   * 读取所有应用的全部 API 资源（含已停用），按标识升序、不分页；
   * 鉴权时的路径匹配、唯一性检查和按 operationId 定位都在内存完成。
   */
  List<ProtectedResource> all();

  /**
   * 标识为空时新增，否则覆盖该标识的已有资源，已不存在则抛 {@code API_RESOURCE_NOT_FOUND}。
   * 立即写入，同一应用内方法与路径或 operationId 重复、应用不存在等约束冲突抛 {@code DATA_CONFLICT}；
   * 返回带数据库标识的新对象，入参不回填标识。
   */
  ProtectedResource save(ProtectedResource value);

  /**
   * 按标识删除 API 资源，不存在时静默忽略；调用方须先确认没有前端权限绑定，
   * 否则会违反外键约束。
   */
  void delete(Long id);

  /** 应用下是否还有 API 资源（含已停用），{@code id} 为应用标识；删除应用前检查。 */
  boolean inSystem(Long id);
}
