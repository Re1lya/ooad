package com.cdwy.permission.service.ddd.domain.model;

import com.cdwy.permission.service.port.data.PermissionData;
import java.util.*;

/** 权限目录领域快照：祖先只为导航补全，不自动获得授权。 */
public final class PermissionDirectory {
  private final Map<Long, FrontendPermission> nodes;
  private final Map<Long, ManagedSystem> systems;

  /** 读取请求内稳定快照。 */
  public PermissionDirectory(
      List<FrontendPermission> permissions, List<ManagedSystem> applications) {
    nodes = new LinkedHashMap<>();
    permissions.forEach(n -> nodes.put(n.getId(), n));
    systems = new LinkedHashMap<>();
    applications.forEach(n -> systems.put(n.getId(), n));
  }

  /** 子节点、祖先及应用都启用才有效。 */
  public boolean usable(Long id) {
    Set<Long> seen = new HashSet<>();
    for (FrontendPermission n = nodes.get(id); n != null; n = nodes.get(n.getParentId())) {
      ManagedSystem app = systems.get(n.getSystemId());
      if (!seen.add(n.getId()) || !n.getActive() || app == null || !app.getEnabled()) return false;
    }
    return nodes.containsKey(id);
  }

  /** 将当前节点转换为不可变结果。 */
  public PermissionData.View view(Long id) {
    FrontendPermission n = nodes.get(id);
    return new PermissionData.View(
        n.getId(),
        n.getSystemId(),
        n.getCode(),
        n.getName(),
        n.getKind(),
        n.getMenuPath(),
        n.getParentId(),
        n.getActive());
  }

  /** 返回有效授权列表。 */
  public List<PermissionData.View> views(Collection<Long> ids) {
    return ids.stream().map(this::view).toList();
  }

  /** 补全祖先容器，granted 仅由直接或继承授权集合决定。 */
  public List<PermissionData.Tree> tree(Collection<Long> exact) {
    // 按标识排序，导航顺序与菜单的登记顺序一致，不随授权来源的遍历顺序变化。
    Set<Long> included = new TreeSet<>();
    for (Long id : exact) {
      Set<Long> seen = new HashSet<>();
      for (FrontendPermission n = nodes.get(id);
          n != null && seen.add(n.getId());
          n = nodes.get(n.getParentId())) included.add(n.getId());
    }
    return included.stream()
        .filter(id -> nodes.get(id).getParentId() == null)
        .map(id -> branch(id, included, new HashSet<>(exact)))
        .toList();
  }

  /** 构造单个导航分支。 */
  private PermissionData.Tree branch(Long id, Set<Long> included, Set<Long> exact) {
    return new PermissionData.Tree(
        view(id),
        exact.contains(id),
        included.stream()
            .filter(child -> Objects.equals(nodes.get(child).getParentId(), id))
            .map(child -> branch(child, included, exact))
            .toList());
  }
}
