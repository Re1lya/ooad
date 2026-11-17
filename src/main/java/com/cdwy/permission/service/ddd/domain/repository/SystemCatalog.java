package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/** SystemCatalog 领域仓储端口。 */
public interface SystemCatalog {
  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Optional<ManagedSystem> find(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<ManagedSystem> all();

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  ManagedSystem save(ManagedSystem value);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void delete(Long id);
}
