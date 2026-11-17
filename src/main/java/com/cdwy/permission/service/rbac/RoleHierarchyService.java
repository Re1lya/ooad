package com.cdwy.permission.service.rbac;

import com.cdwy.permission.entity.rbac.RoleHierarchy;
import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.repository.rbac.RoleHierarchyRepository;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.ResourceNotFoundException;
import com.cdwy.permission.service.port.rbac.HierarchyUseCase;
import com.cdwy.permission.service.port.rbac.RoleGraphUseCase;
import com.cdwy.permission.service.port.rbac.RoleGraphUseCase.Node;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RBAC1 角色有向无环图：维护继承边、验证无环、解析有效继承路径。 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleHierarchyService implements HierarchyUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final RoleHierarchyRepository hierarchy;
  private final RoleRepository roles;
  private final RoleGraphUseCase policy;

  /** 解析结果携带业务角色与一条最短可用路径。 */
  public record ResolvedRole(Role role, List<Long> path) {}

  /** 展示全部显式继承边，不将传递路径重复保存为直接关系。 */
  public List<HierarchyData.Edge> edges() {
    return hierarchy.findAll().stream()
        .map(
            e ->
                new HierarchyData.Edge(
                    e.getSeniorRole().getId(), e.getSeniorRole().getName(),
                    e.getInheritedRole().getId(), e.getInheritedRole().getName()))
        .toList();
  }

  /** 获取角色；不存在时保持统一 404 契约。 */
  public Role require(Long id) {
    return roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("角色不存在"));
  }

  /** 建立高级角色到被继承角色的边；串行化图写入后检查全图可达性。 */
  @Transactional
  public void inherit(Long seniorId, Long inheritedId) {
    roles.lockHierarchyRoles();
    Role senior = require(seniorId);
    Role inherited = require(inheritedId);
    if (seniorId.equals(inheritedId)) throw new IllegalArgumentException("角色不能继承自身");
    if (hierarchy.existsBySeniorRole_IdAndInheritedRole_Id(seniorId, inheritedId)) return;
    if (!senior.getEnabled() || !inherited.getEnabled())
      throw new IllegalArgumentException("请先启用关系两端的角色");
    policy.validate(seniorId, inheritedId, snapshot(graph()));
    hierarchy.save(new RoleHierarchy(senior, inherited));
  }

  /** 解除继承只删除直接边；其他继承路径及直接授权继续保留。 */
  @Transactional
  public void remove(Long seniorId, Long inheritedId) {
    roles.lockHierarchyRoles();
    require(seniorId);
    require(inheritedId);
    hierarchy.deleteBySeniorRole_IdAndInheritedRole_Id(seniorId, inheritedId);
  }

  /** 每次鉴权读取当前继承关系，避免将授权快照写入 JWT。 */
  public Map<Long, List<Role>> graph() {
    Map<Long, List<Role>> graph = new LinkedHashMap<>();
    for (RoleHierarchy edge : hierarchy.findAll())
      graph
          .computeIfAbsent(edge.getSeniorRole().getId(), ignored -> new ArrayList<>())
          .add(edge.getInheritedRole());
    return graph;
  }

  /** 按广度优先遍历有效角色，停用节点阻断该路径，菱形路径只计算一次。 */
  public List<ResolvedRole> resolve(Role assigned, Map<Long, List<Role>> graph) {
    Map<Long, Role> byId = new LinkedHashMap<>();
    byId.put(assigned.getId(), assigned);
    graph.values().forEach(values -> values.forEach(role -> byId.put(role.getId(), role)));
    return policy
        .resolve(new Node(assigned.getId(), assigned.getEnabled()), snapshot(graph))
        .stream()
        .map(path -> new ResolvedRole(byId.get(path.roleId()), path.ids()))
        .toList();
  }

  /** JPA 业务角色转换为不可变计算快照，共享基础设施不向领域层传实体。 */
  private Map<Long, List<Node>> snapshot(Map<Long, List<Role>> graph) {
    Map<Long, List<Node>> result = new LinkedHashMap<>();
    graph.forEach(
        (id, values) ->
            result.put(
                id,
                values.stream().map(role -> new Node(role.getId(), role.getEnabled())).toList()));
    return result;
  }
}
