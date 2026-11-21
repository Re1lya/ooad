package com.cdwy.permission.service.ddd.application.permission;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.permission.PermissionUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddPermissionService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddPermissionService implements PermissionUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final PermissionCatalog permissions;
  private final SystemCatalog systems;
  private final ResourceCatalog resources;
  private final AssignmentRepository assignments;

  /** 当前权限图。 */
  private Map<Long, FrontendPermission> nodes() {
    Map<Long, FrontendPermission> nodes = new LinkedHashMap<>();
    permissions.all().forEach(n -> nodes.put(n.getId(), n));
    return nodes;
  }

  /** 当前目录领域快照。 */
  private PermissionDirectory directory() {
    return new PermissionDirectory(permissions.all(), systems.all());
  }

  /** 读取前端权限。 */
  private FrontendPermission require(Long id) {
    return permissions.find(id).orElseThrow(() -> new ResourceNotFoundException("前端权限不存在"));
  }

  /** 读取 API 资源。 */
  private ProtectedResource resource(Long id) {
    return resources.find(id).orElseThrow(() -> new ResourceNotFoundException("API 资源不存在"));
  }

  /** 不重复访问数据库的结果转换。 */
  private PermissionData.View view(FrontendPermission n) {
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

  /** 根据应用过滤权限。 */
  public List<PermissionData.View> list(Long systemId) {
    return permissions.all().stream()
        .filter(n -> systemId == null || n.getSystemId().equals(systemId))
        .map(this::view)
        .toList();
  }

  /** 对象维护菜单按钮规则，仓储处理唯一约束。 */
  @Transactional
  public PermissionData.View create(PermissionData.Input input) {
    if (permissions.all().stream().anyMatch(n -> n.getCode().equals(input.code())))
      throw new ConflictException("前端权限编码已存在");
    systems.find(input.systemId()).orElseThrow(() -> new ResourceNotFoundException("对象不存在"));
    FrontendPermission n = new FrontendPermission();
    n.setSystemId(input.systemId());
    n.setCode(input.code());
    n.edit(input.name(), input.type(), input.menuPath(), input.parentId(), nodes(), false);
    return view(permissions.save(n));
  }

  /** 修改父级时重新检查整条路径。 */
  @Transactional
  public PermissionData.View update(Long id, PermissionData.Update input) {
    FrontendPermission n = require(id);
    n.edit(
        input.name(),
        input.type(),
        input.menuPath(),
        input.parentId(),
        nodes(),
        permissions.hasChildren(id));
    return view(permissions.save(n));
  }

  /** 停用权限保留来源信息。 */
  @Transactional
  public PermissionData.View status(Long id, boolean active) {
    FrontendPermission n = require(id);
    n.changeStatus(active);
    return view(permissions.save(n));
  }

  /** 先解除子节点、角色授权与 API 绑定。 */
  @Transactional
  public void delete(Long id) {
    FrontendPermission n = require(id);
    n.assertDeletable(
        permissions.hasChildren(id)
            || assignments.rolePermissions().stream().anyMatch(e -> e.right().equals(id))
            || assignments.bindings().stream().anyMatch(e -> e.left().equals(id)));
    permissions.delete(id);
  }

  /** 对象维护同应用绑定规则。 */
  @Transactional
  public void bind(Long permissionId, Long resourceId) {
    FrontendPermission n = require(permissionId);
    ProtectedResource r = resource(resourceId);
    n.assertBindable(r);
    assignments.bind(permissionId, resourceId);
  }

  /** 幂等解除绑定。 */
  @Transactional
  public void unbind(Long permissionId, Long resourceId) {
    require(permissionId);
    resource(resourceId);
    assignments.unbind(permissionId, resourceId);
  }

  /** 查询已绑定 API。 */
  public List<ResourceData.View> resources(Long id) {
    require(id);
    return assignments.bindings().stream()
        .filter(e -> e.left().equals(id))
        .map(e -> resource(e.right()))
        .map(
            n ->
                new ResourceData.View(
                    n.getId(),
                    n.getSystemId(),
                    n.getName(),
                    n.getMethod(),
                    n.getPath(),
                    n.getActive(),
                    n.getCode(),
                    n.getOperationId()))
        .toList();
  }

  /** 管理目录展示不把未授权祖先当成实际授权。 */
  public List<PermissionData.Tree> tree(Long systemId) {
    List<Long> ids =
        permissions.all().stream()
            .filter(n -> systemId == null || n.getSystemId().equals(systemId))
            .map(FrontendPermission::getId)
            .toList();
    return directory().tree(ids);
  }
}
