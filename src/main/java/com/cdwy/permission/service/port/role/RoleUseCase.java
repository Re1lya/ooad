package com.cdwy.permission.service.port.role;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/**
 * 角色用例：角色的新建、修改、启停、删除与列表。角色继承见 {@code HierarchyUseCase}，用户与权限授权见
 * {@code AuthorizationUseCase}。
 */
public interface RoleUseCase extends BackendUseCase {
  /** 按标识升序列出全部角色，含已停用的和内置的权限管理员角色（{@code rbac-admin}）。 */
  List<RoleData.View> list();

  /**
   * 新建角色，默认启用、没有任何权限，返回含新标识的结果；编码全局唯一，已存在抛 {@code ROLE_CODE_EXISTS}（409），创建后
   * 不可修改。不写审计日志。
   */
  RoleData.View create(RoleData.Create input);

  /** 修改名称与说明，编码不变；不存在抛 {@code ROLE_NOT_FOUND}（404），不写审计日志。 */
  RoleData.View update(Long id, RoleData.Update input);

  /**
   * 启用或停用角色，重复设置幂等；不存在抛 {@code ROLE_NOT_FOUND}（404）。已有分配、授权与继承关系都保留；停用的角色不再
   * 提供权限并阻断经过它的继承路径，也不能再被分配、授予权限或参与新建继承。凭证版本不变，下一次请求即生效；不写审计日志。
   */
  RoleData.View status(Long id, boolean enabled);

  /**
   * 删除角色：先锁定全部角色，与继承关系的修改串行化；不存在抛 {@code ROLE_NOT_FOUND}（404）；仍是任一继承关系的一端抛
   * {@code ROLE_HAS_INHERITANCE}，仍分配给用户或持有权限抛 {@code ROLE_IN_USE}（均 409），不级联删除；不写审计日志。
   */
  void delete(Long id);
}
