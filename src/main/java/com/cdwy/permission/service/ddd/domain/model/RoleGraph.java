package com.cdwy.permission.service.ddd.domain.model;

import com.cdwy.permission.service.port.rbac.RoleGraphUseCase.Node;
import com.cdwy.permission.service.port.rbac.RoleGraphUseCase.Path;
import java.util.*;
import com.cdwy.permission.service.port.error.InvalidDataException;

/** 面向对象的角色图：封装邻接关系、无环不变量及有效路径行为；无 Spring/JPA 依赖。 */
public final class RoleGraph {
  private final Map<Long, List<Node>> edges;

  /** 防御性复制快照，领域对象不修改持久化实体。 */
  public RoleGraph(Map<Long, List<Node>> edges) {
    Map<Long, List<Node>> copy = new LinkedHashMap<>();
    edges.forEach((id, nodes) -> copy.put(id, List.copyOf(nodes)));
    this.edges = Collections.unmodifiableMap(copy);
  }

  /** 新增边的无环不变量由图对象自身维护。 */
  public void assertCanInherit(Long senior, Long inherited) {
    if (senior.equals(inherited)) throw new InvalidDataException("ROLE_SELF_INHERITANCE", "角色不能继承自身");
    Set<Long> seen = new HashSet<>();
    Deque<Long> pending = new ArrayDeque<>();
    pending.add(inherited);
    while (!pending.isEmpty()) {
      Long id = pending.removeFirst();
      if (id.equals(senior)) throw new InvalidDataException("ROLE_INHERITANCE_CYCLE", "该关系会形成循环继承");
      if (seen.add(id)) edges.getOrDefault(id, List.of()).forEach(n -> pending.addLast(n.id()));
    }
  }

  /** 图以有效路径对象承载遍历状态，返回最短且不重复的到达结果。 */
  public List<Path> effectivePaths(Node root) {
    List<Path> found = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    Deque<Traversal> pending = new ArrayDeque<>();
    pending.add(new Traversal(root, List.of(root.id())));
    while (!pending.isEmpty()) {
      Traversal current = pending.removeFirst();
      if (!current.node.enabled() || !seen.add(current.node.id())) continue;
      found.add(new Path(current.node.id(), current.ids));
      for (Node child : edges.getOrDefault(current.node.id(), List.of()))
        pending.addLast(current.extend(child));
    }
    return List.copyOf(found);
  }

  /** 一条有效遍历分支，不与外部分支共享可变路径。 */
  private record Traversal(Node node, List<Long> ids) {
    /** 扩展到子节点，不修改当前路径。 */
    Traversal extend(Node next) {
      List<Long> copy = new ArrayList<>(ids);
      copy.add(next.id());
      return new Traversal(next, List.copyOf(copy));
    }
  }
}
