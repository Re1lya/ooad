package com.cdwy.permission.service.structured.rbac;

import com.cdwy.permission.service.port.rbac.RoleGraphUseCase;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.cdwy.permission.service.port.error.InvalidDataException;

/** 结构化事务脚本中的图算法：数据与过程分离，步骤集中在服务方法。 */
@Service
@ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
public class StructuredRoleGraphService implements RoleGraphUseCase {
  /** 先校验自身，再从目标沿全图检查是否回到源。 */
  public void validate(Long seniorId, Long inheritedId, Map<Long, List<Node>> graph) {
    if (seniorId.equals(inheritedId)) throw new InvalidDataException("ROLE_SELF_INHERITANCE", "角色不能继承自身");
    Deque<Long> queue = new ArrayDeque<>();
    Set<Long> visited = new HashSet<>();
    queue.add(inheritedId);
    while (!queue.isEmpty()) {
      Long current = queue.removeFirst();
      if (current.equals(seniorId)) throw new InvalidDataException("ROLE_INHERITANCE_CYCLE", "该关系会形成循环继承");
      if (visited.add(current))
        for (Node child : graph.getOrDefault(current, List.of())) queue.addLast(child.id());
    }
  }

  /** BFS 展开；停用节点阻断当前路径，菱形去重。 */
  public List<Path> resolve(Node assigned, Map<Long, List<Node>> graph) {
    List<Path> result = new ArrayList<>();
    Deque<Path> queue = new ArrayDeque<>();
    Set<Long> visited = new HashSet<>();
    Map<Long, Boolean> enabled = new HashMap<>();
    graph.values().forEach(nodes -> nodes.forEach(n -> enabled.put(n.id(), n.enabled())));
    enabled.put(assigned.id(), assigned.enabled());
    queue.add(new Path(assigned.id(), List.of(assigned.id())));
    while (!queue.isEmpty()) {
      Path next = queue.removeFirst();
      if (!enabled.getOrDefault(next.roleId(), false) || !visited.add(next.roleId())) continue;
      result.add(next);
      for (Node child : graph.getOrDefault(next.roleId(), List.of())) {
        List<Long> ids = new ArrayList<>(next.ids());
        ids.add(child.id());
        queue.addLast(new Path(child.id(), List.copyOf(ids)));
      }
    }
    return List.copyOf(result);
  }

  /** 标识结构化实现。 */
  public String strategy() {
    return "structured";
  }
}
