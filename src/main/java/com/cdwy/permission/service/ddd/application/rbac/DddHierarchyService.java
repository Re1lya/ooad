package com.cdwy.permission.service.ddd.application.rbac;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository.Link;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.rbac.HierarchyUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddHierarchyService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddHierarchyService implements HierarchyUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final com.cdwy.permission.service.port.rbac.RoleGraphUseCase policy;
  private final RoleCatalog roles;
  private final AssignmentRepository assignments;

  /** 读取业务角色。 */
  private AccessRole require(Long id) {
    return roles.find(id).orElseThrow(() -> new ResourceNotFoundException("角色不存在"));
  }

  /** 只返回显式边。 */
  public List<HierarchyData.Edge> edges() {
    Map<Long, AccessRole> byId = new LinkedHashMap<>();
    roles.all().forEach(r -> byId.put(r.getId(), r));
    return assignments.hierarchy().stream()
        .map(
            e ->
                new HierarchyData.Edge(
                    e.left(),
                    byId.get(e.left()).getName(),
                    e.right(),
                    byId.get(e.right()).getName()))
        .toList();
  }

  /** 加锁后由领域图完成防环，重复边保持幂等。 */
  @Transactional
  public void inherit(Long senior, Long inherited) {
    roles.lockGraph();
    AccessRole first = require(senior), second = require(inherited);
    if (senior.equals(inherited)) throw new IllegalArgumentException("角色不能继承自身");
    if (assignments.hierarchy().contains(new Link(senior, inherited))) return;
    if (!first.getEnabled() || !second.getEnabled())
      throw new IllegalArgumentException("请先启用关系两端的角色");
    Map<Long, AccessRole> byId = new LinkedHashMap<>();
    roles.all().forEach(r -> byId.put(r.getId(), r));
    Map<Long, List<com.cdwy.permission.service.port.rbac.RoleGraphUseCase.Node>> graph =
        new LinkedHashMap<>();
    for (AssignmentRepository.Link edge : assignments.hierarchy()) {
      AccessRole child = byId.get(edge.right());
      graph
          .computeIfAbsent(edge.left(), key -> new ArrayList<>())
          .add(
              new com.cdwy.permission.service.port.rbac.RoleGraphUseCase.Node(
                  child.getId(), child.getEnabled()));
    }
    policy.validate(senior, inherited, graph);
    assignments.inherit(senior, inherited);
  }

  /** 解除直接边不影响其他路径。 */
  @Transactional
  public void remove(Long senior, Long inherited) {
    roles.lockGraph();
    require(senior);
    require(inherited);
    assignments.removeInheritance(senior, inherited);
  }
}
