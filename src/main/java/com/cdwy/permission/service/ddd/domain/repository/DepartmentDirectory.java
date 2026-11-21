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

  /** 一层部门（parentId 为空时为根部门），关键字匹配名称或编码；页码从 0 开始，数据库分页。 */
  com.cdwy.permission.service.port.data.UserData.Page<DepartmentNode> level(
      Long parentId, String keyword, int page, int size);

  /** ids 中有直接子部门的部门标识。 */
  java.util.Set<Long> withChildren(java.util.Collection<Long> ids);
}
