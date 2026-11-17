package com.cdwy.permission.service.port.rbac;

import java.util.*;

/** FR-23：两种设计共享的纯角色图用例；不依赖 JPA、Web 或 Token。 */
public interface RoleGraphUseCase {
  /** 角色的不可变计算快照。 */
  record Node(Long id, boolean enabled) {}

  /** 到达某角色的一条最短有效路径。 */
  record Path(Long roleId, List<Long> ids) {}

  /** 校验高级角色到被继承角色的新增边，不忽略停用节点。 */
  void validate(Long seniorId, Long inheritedId, Map<Long, List<Node>> graph);

  /** 解析有效节点及最短路径。 */
  List<Path> resolve(Node assigned, Map<Long, List<Node>> graph);

  /** 实际装载的实现标识。 */
  String strategy();
}
