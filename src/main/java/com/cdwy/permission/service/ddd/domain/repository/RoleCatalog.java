package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/** RoleCatalog 领域仓储端口。 */
public interface RoleCatalog {
  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Optional<AccessRole> find(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<AccessRole> all();

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  AccessRole save(AccessRole value);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void delete(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void lockGraph();
}
