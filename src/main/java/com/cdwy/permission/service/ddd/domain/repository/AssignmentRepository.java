package com.cdwy.permission.service.ddd.domain.repository;

import com.cdwy.permission.service.ddd.domain.model.*;
import java.util.*;

/** AssignmentRepository 领域仓储端口。 */
public interface AssignmentRepository {
  /** 两端业务标识；不向领域层暴露关联实体。 */
  record Link(Long left, Long right) {}

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<Link> userRoles();

  /** 某个用户直接分配的角色标识；按用户查询，不加载全部用户的分配关系。 */
  List<Long> rolesOf(Long userId);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<Link> rolePermissions();

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<Link> bindings();

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  List<Link> hierarchy();

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void addUserRole(Long userId, Long roleId);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void removeUserRole(Long userId, Long roleId);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void removeUserRoles(Long userId);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void addRolePermission(Long roleId, Long permissionId);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void removeRolePermission(Long roleId, Long permissionId);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void bind(Long permissionId, Long resourceId);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void unbind(Long permissionId, Long resourceId);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void inherit(Long senior, Long inherited);

  /** 读取或保存领域状态；数据库异常由适配器翻译。 */
  void removeInheritance(Long senior, Long inherited);
}
