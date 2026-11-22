package com.cdwy.permission.service.ddd.domain.model;

import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository.Link;
import com.cdwy.permission.service.port.data.HierarchyData;
import com.cdwy.permission.service.port.rbac.RoleGraphUseCase.Node;
import com.cdwy.permission.service.port.rbac.RoleGraphUseCase.Path;
import java.util.*;

/** RBAC1 授权领域快照：权限并集、来源、有效路径均在此计算。 */
public final class AuthorizationSnapshot {
  private final Map<Long, AccessRole> roles = new LinkedHashMap<>();
  private final PermissionDirectory directory;
  private final List<Link> grants;
  private final RoleGraph graph;

  /** 将本次读取的角色与授权形成防御性快照。 */
  public AuthorizationSnapshot(
      List<AccessRole> values,
      List<FrontendPermission> permissions,
      List<ManagedSystem> systems,
      List<Link> hierarchy,
      List<Link> assignments) {
    values.forEach(r -> roles.put(r.getId(), r));
    directory = new PermissionDirectory(permissions, systems);
    grants = List.copyOf(assignments);
    Map<Long, List<Node>> edges = new LinkedHashMap<>();
    for (Link e : hierarchy) {
      AccessRole child = roles.get(e.right());
      if (child != null)
        edges
            .computeIfAbsent(e.left(), k -> new ArrayList<>())
            .add(new Node(child.getId(), child.getEnabled()));
    }
    graph = new RoleGraph(edges);
  }

  /** 角色继承必须在整图无环，停用节点也参与防环。 */
  public void assertCanInherit(Long senior, Long inherited) {
    graph.assertCanInherit(senior, inherited);
  }

  /** 有效来源集合，同权限只返回一次。 */
  public List<HierarchyData.PermissionOrigin> origins(List<Long> assigned) {
    Map<Long, List<HierarchyData.Origin>> origins = new LinkedHashMap<>();
    for (Long id : assigned) {
      AccessRole root = roles.get(id);
      if (root == null) continue;
      for (Path path : graph.effectivePaths(new Node(id, root.getEnabled()))) {
        AccessRole role = roles.get(path.roleId());
        for (Link grant : grants) {
          if (!grant.left().equals(role.getId()) || !directory.usable(grant.right())) continue;
          origins
              .computeIfAbsent(grant.right(), key -> new ArrayList<>())
              .add(
                  new HierarchyData.Origin(
                      id,
                      root.getName(),
                      role.getId(),
                      role.getName(),
                      !id.equals(role.getId()),
                      path.ids()));
        }
      }
    }
    return origins.entrySet().stream()
        .map(
            e ->
                new HierarchyData.PermissionOrigin(
                    directory.view(e.getKey()), List.copyOf(e.getValue())))
        .toList();
  }

  /** 有效角色：分配角色及沿继承链可达的启用角色；停用角色阻断路径，菱形只计一次。 */
  public List<AccessRole> effectiveRoles(List<Long> assigned) {
    Map<Long, AccessRole> result = new LinkedHashMap<>();
    for (Long id : assigned) {
      AccessRole root = roles.get(id);
      if (root == null) continue;
      for (Path path : graph.effectivePaths(new Node(id, root.getEnabled())))
        result.putIfAbsent(path.roleId(), roles.get(path.roleId()));
    }
    return List.copyOf(result.values());
  }

  /** 指定接入应用内的有效前端权限（菜单、按钮），供接入应用身份接口使用。 */
  public List<com.cdwy.permission.service.port.data.PermissionData.View> grantedIn(
      Long systemId, List<Long> assigned) {
    return origins(assigned).stream()
        .map(HierarchyData.PermissionOrigin::permission)
        .filter(view -> systemId.equals(view.systemId()))
        .toList();
  }

  /** 权限目录负责导航与授权区分。 */
  public PermissionDirectory directory() {
    return directory;
  }
}
