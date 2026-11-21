package com.cdwy.permission.service.port.rbac;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/**
 * RBAC1 角色继承用例：维护角色之间的显式继承边，高级角色获得被继承角色的全部有效权限。
 *
 * <p>继承图必须无环，停用角色也参与防环。新增与解除都先锁定全部角色，串行化图写入；实际发生变更时写审计日志（应用
 * {@code rbac}，操作人取当前登录用户）。授权不写入令牌，下一次鉴权即按新继承图计算。
 */
public interface HierarchyUseCase extends BackendUseCase {
  /** 全部显式继承边（含停用角色之间的），不展开传递关系；每条边带两端角色的名称。 */
  List<HierarchyData.Edge> edges();

  /**
   * 让高级角色继承被继承角色：两端须存在（{@code ROLE_NOT_FOUND}，404），不能继承自身（{@code ROLE_SELF_INHERITANCE}）；
   * 关系已存在时直接返回（幂等，不再检查启停，不写审计）；两端须都已启用（{@code ROLE_DISABLED}），新边不能使全图成环
   * （{@code ROLE_INHERITANCE_CYCLE}），以上均 400。建立后写审计日志 {@code ROLE_INHERITANCE_ADDED}。
   */
  void inherit(Long seniorId, Long inheritedId);

  /**
   * 解除一条直接继承边：两端须存在（{@code ROLE_NOT_FOUND}，404）；边不存在时直接返回（幂等，不写审计），否则删除并写
   * 审计日志 {@code ROLE_INHERITANCE_REMOVED}。经其他路径的继承与角色的直接授权不受影响。
   */
  void remove(Long seniorId, Long inheritedId);
}
