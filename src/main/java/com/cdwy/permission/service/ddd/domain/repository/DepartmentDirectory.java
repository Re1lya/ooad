package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/** DepartmentDirectory 领域仓储端口。 */
public interface DepartmentDirectory {
  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Optional<DepartmentNode> find(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<DepartmentNode> all();

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  DepartmentNode save(DepartmentNode value);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void delete(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  boolean hasChildren(Long id);
}
