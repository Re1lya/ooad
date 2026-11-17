package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/** PermissionCatalog 领域仓储端口。 */
public interface PermissionCatalog {
  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Optional<FrontendPermission> find(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<FrontendPermission> all();

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  FrontendPermission save(FrontendPermission value);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void delete(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  boolean hasChildren(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  boolean inSystem(Long id);
}
