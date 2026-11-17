package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/** ResourceCatalog 领域仓储端口。 */
public interface ResourceCatalog {
  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Optional<ProtectedResource> find(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<ProtectedResource> all();

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  ProtectedResource save(ProtectedResource value);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void delete(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  boolean inSystem(Long id);
}
