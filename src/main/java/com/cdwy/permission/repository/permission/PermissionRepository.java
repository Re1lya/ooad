package com.cdwy.permission.repository.permission;

import com.cdwy.permission.entity.permission.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

/** 前端权限（菜单、按钮）仓储：权限树及其所属应用，供权限管理、控制台内置数据登记和删除前引用检查使用。 */
public interface PermissionRepository extends JpaRepository<Permission, Long> {
  /** 编码是否已被占用：编码跨应用全局唯一，精确匹配（区分大小写），含已停用的权限。 */
  boolean existsByCode(String code);

  /** 按编码精确查找（区分大小写），含已停用的权限；空表示该编码尚未登记。 */
  java.util.Optional<Permission> findByCode(String code);

  /** 是否有以该权限为直接父节点的子权限（含已停用）；删除权限或把菜单改为按钮前检查。 */
  boolean existsByParent_Id(Long parentId);

  /** 应用下是否还有前端权限（含已停用）；删除应用前检查。 */
  boolean existsBySystem_Id(Long systemId);

  /** 某应用的全部前端权限，按标识升序，含已停用的；供管理列表和权限树使用，应用不存在时为空列表。 */
  java.util.List<Permission> findAllBySystem_IdOrderById(Long systemId);
}
