package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/**
 * 前端权限目录仓储端口：读取、保存和删除各应用的菜单与按钮 {@link FrontendPermission}，提供删除前检查；
 * 领域层不接触 JPA 实体。读出的权限是副本，修改后须调用 {@link #save} 才会写回。
 */
public interface PermissionCatalog {
  /** 按标识读取前端权限（含已停用）；不存在时返回空 Optional，由应用服务抛 {@code PERMISSION_NOT_FOUND}。 */
  Optional<FrontendPermission> find(Long id);

  /**
   * 读取所有应用的全部前端权限（含已停用），按标识升序、不分页；
   * 按应用过滤、编码查重和构建权限树都在内存完成。
   */
  List<FrontendPermission> all();

  /**
   * 标识为空时新增，否则覆盖该标识的已有权限，已不存在则抛 {@code PERMISSION_NOT_FOUND}。
   * 立即写入，编码重复或应用、上级菜单不存在等约束冲突抛 {@code DATA_CONFLICT}；
   * 返回带数据库标识的新对象，入参不回填标识。
   */
  FrontendPermission save(FrontendPermission value);

  /**
   * 按标识删除前端权限，不存在时静默忽略；调用方须先确认没有子节点、角色授权和 API 绑定，
   * 否则会违反外键约束。
   */
  void delete(Long id);

  /** 是否有直接子权限（含已停用）；删除权限或把菜单改为按钮前检查。 */
  boolean hasChildren(Long id);

  /** 应用下是否还有前端权限（含已停用），{@code id} 为应用标识；删除应用前检查。 */
  boolean inSystem(Long id);
}
