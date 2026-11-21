package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/** AccountRepository 领域仓储端口。 */
public interface AccountRepository {
  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Optional<Account> find(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Optional<Account> lock(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Optional<Account> username(String name);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<Account> all();

  /** 按登录名或姓名模糊查询，关键字为空时不过滤；页码从 0 开始，数据库分页。 */
  com.cdwy.permission.service.port.data.UserData.Page<Account> search(
      String keyword, int page, int size);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  Account save(Account value);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void delete(Long id);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  boolean inDepartment(Long id);

  /** 部门下的账号，关键字匹配登录名或姓名；页码从 0 开始，按登录名排序，数据库分页。 */
  com.cdwy.permission.service.port.data.UserData.Page<Account> members(
      Long departmentId, String keyword, int page, int size);
}
