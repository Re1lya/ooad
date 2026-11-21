package com.cdwy.permission.service.permission;

import com.cdwy.permission.entity.permission.*;
import com.cdwy.permission.entity.rbac.PermissionApi;
import com.cdwy.permission.repository.permission.PermissionRepository;
import com.cdwy.permission.repository.rbac.*;
import com.cdwy.permission.service.auth.ConsoleCatalog;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.data.PermissionType;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.permission.PermissionUseCase;
import com.cdwy.permission.service.resource.ApiResourceService;
import com.cdwy.permission.service.system.SystemService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化事务脚本：按查询、业务校验、写入和结果转换组织处理步骤。 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@Transactional(readOnly = true)
public class PermissionService implements PermissionUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final PermissionRepository permissions;
  private final RolePermissionRepository grants;
  private final PermissionApiRepository bindings;
  private final SystemService systems;
  private final ApiResourceService resources;

  /** 按标识读取前端权限（菜单或按钮），不存在抛 {@code PERMISSION_NOT_FOUND}；授权服务也复用此方法。 */
  public Permission require(Long id) {
    return permissions.findById(id).orElseThrow(() -> new ResourceNotFoundException("PERMISSION_NOT_FOUND", "前端权限不存在"));
  }

  /** 实体转为用例结果：类型取枚举名，根节点的 {@code parentId} 为 null；授权服务输出权限时也用它。 */
  public static PermissionData.View view(Permission p) {
    return new PermissionData.View(
        p.getId(),
        p.getSystem().getId(),
        p.getCode(),
        p.getName(),
        p.getType().name(),
        p.getMenuPath(),
        p.getParent() == null ? null : p.getParent().getId(),
        p.getActive());
  }

  /** 按标识顺序列出菜单与按钮，含已失效节点；{@code systemId} 为空时返回全部应用，未知应用得到空列表。 */
  public List<PermissionData.View> list(Long systemId) {
    return (systemId == null
            ? permissions.findAll(org.springframework.data.domain.Sort.by("id"))
            : permissions.findAllBySystem_IdOrderById(systemId))
        .stream().map(PermissionService::view).toList();
  }

  /**
   * 新建与修改共用的结构校验，依次为：上级须存在（{@code PERMISSION_NOT_FOUND}）；按钮必须有上级
   * （{@code BUTTON_PARENT_REQUIRED}）；上级须是同一应用的菜单（{@code PERMISSION_PARENT_INVALID}）；
   * 沿上级链向上不能回到自身，以自身为上级也算成环（{@code PERMISSION_CYCLE}）；已有下级的菜单不能改为按钮
   * （冲突 {@code PERMISSION_HAS_CHILDREN}）；按钮不能填菜单地址（{@code BUTTON_PATH_NOT_ALLOWED}）。
   * 全部通过后才写入名称、类型、地址和上级，空白地址存为 null。
   */
  private void apply(Permission p, String name, PermissionType type, String path, Long parentId) {
    Permission parent = parentId == null ? null : require(parentId);
    if (type == PermissionType.BUTTON && parent == null)
      throw new InvalidDataException("BUTTON_PARENT_REQUIRED", "按钮必须隶属菜单");
    if (parent != null
        && (parent.getType() != PermissionType.MENU
            || !parent.getSystem().getId().equals(p.getSystem().getId())))
      throw new InvalidDataException("PERMISSION_PARENT_INVALID", "父节点必须是同一应用的菜单");
    for (Permission next = parent; next != null; next = next.getParent())
      if (Objects.equals(next.getId(), p.getId())) throw new InvalidDataException("PERMISSION_CYCLE", "权限树不能形成循环");
    if (type == PermissionType.BUTTON
        && p.getId() != null
        && permissions.existsByParent_Id(p.getId())) throw new ConflictException("PERMISSION_HAS_CHILDREN", "有子节点的菜单不能改为按钮");
    if (type == PermissionType.BUTTON && path != null && !path.isBlank())
      throw new InvalidDataException("BUTTON_PATH_NOT_ALLOWED", "按钮不设置菜单地址");
    p.setName(name);
    p.setType(type);
    p.setMenuPath(path == null || path.isBlank() ? null : path);
    p.setParent(parent);
  }

  /**
   * 新建菜单或按钮并返回含新标识的视图：编码全局唯一（冲突 {@code PERMISSION_CODE_EXISTS}），所属应用须存在
   * （{@code SYSTEM_NOT_FOUND}），结构规则见 {@code apply}。新节点默认生效，授予角色后才进入有效权限；不写审计日志。
   */
  @Transactional
  public PermissionData.View create(PermissionData.Input input) {
    if (permissions.existsByCode(input.code())) throw new ConflictException("PERMISSION_CODE_EXISTS", "前端权限编码已存在");
    Permission p = new Permission();
    p.setCode(input.code());
    p.setSystem(systems.require(input.systemId()));
    apply(
        p, input.name(), PermissionType.valueOf(input.type()), input.menuPath(), input.parentId());
    return view(permissions.save(p));
  }

  /**
   * 修改名称、类型、菜单地址与上级，编码、所属应用和生效状态不变；不存在抛 {@code PERMISSION_NOT_FOUND}，
   * 结构规则见 {@code apply}。已有角色授权和 API 绑定保留；改动在事务提交时由脏检查写回，不写审计日志。
   */
  @Transactional
  public PermissionData.View update(Long id, PermissionData.Update input) {
    Permission p = require(id);
    apply(
        p, input.name(), PermissionType.valueOf(input.type()), input.menuPath(), input.parentId());
    return view(p);
  }

  /**
   * 设置生效状态，重复设置无副作用；不存在抛 {@code PERMISSION_NOT_FOUND}。失效后该节点及全部下级都不再计入
   * 任何人的有效权限，也不能再授予角色；已有授权、绑定和下级的状态字段都保留，重新生效即恢复。鉴权按请求实时计算，
   * 已签发令牌的下一次请求即受影响，凭证版本不变；事务提交时写回，不写审计日志。
   */
  @Transactional
  public PermissionData.View status(Long id, boolean active) {
    Permission p = require(id);
    if (!active) protectBuiltIn(p);
    p.setActive(active);
    return view(p);
  }

  /**
   * 删除菜单或按钮：不存在抛 {@code PERMISSION_NOT_FOUND}；仍有下级、角色授权或 API 绑定时抛
   * {@code PERMISSION_IN_USE}（冲突），不做级联删除。不写审计日志。
   */
  @Transactional
  public void delete(Long id) {
    Permission p = require(id);
    protectBuiltIn(p);
    if (permissions.existsByParent_Id(id)
        || grants.existsByPermission_Id(id)
        || bindings.existsByPermission_Id(id)) throw new ConflictException("PERMISSION_IN_USE", "仍有子节点、角色授权或 API 绑定");
    permissions.delete(p);
  }

  /**
   * 把 API 资源绑定到菜单或按钮：两端须存在（{@code PERMISSION_NOT_FOUND}、{@code API_RESOURCE_NOT_FOUND}）
   * 且属于同一应用（{@code BINDING_SYSTEM_MISMATCH}），不检查两端是否生效；已绑定时幂等。
   * 持有该权限的用户下一次请求即可调用该资源；不写审计日志。
   */
  @Transactional
  public void bind(Long permissionId, Long resourceId) {
    Permission permission = require(permissionId);
    com.cdwy.permission.entity.resource.ApiResource resource = resources.require(resourceId);
    if (!permission.getSystem().getId().equals(resource.getSystem().getId()))
      throw new InvalidDataException("BINDING_SYSTEM_MISMATCH", "前端权限与 API 资源必须属于同一应用");
    if (!bindings.existsByPermission_IdAndResource_Id(permissionId, resourceId))
      bindings.save(new PermissionApi(permission, resource));
  }

  /**
   * 解除绑定，保留 API 资源登记：两端须存在（{@code PERMISSION_NOT_FOUND}、{@code API_RESOURCE_NOT_FOUND}），
   * 未绑定时幂等。已签发令牌的下一次请求即失去经此绑定得到的访问；不写审计日志。
   */
  @Transactional
  public void unbind(Long permissionId, Long resourceId) {
    require(permissionId);
    resources.require(resourceId);
    bindings.deleteByPermission_IdAndResource_Id(permissionId, resourceId);
  }

  /** 该权限绑定的 API 资源，含已停用的；权限不存在抛 {@code PERMISSION_NOT_FOUND}。 */
  public List<ResourceData.View> resources(Long permissionId) {
    require(permissionId);
    return bindings.findAllByPermission_Id(permissionId).stream()
        .map(b -> ApiResourceService.view(b.getResource()))
        .toList();
  }

  /** 自身及每一级上级都生效且所属应用启用才算可用；授予与有效权限计算都以此为准，祖先失效会屏蔽整棵子树。 */
  public static boolean usable(Permission p) {
    for (Permission n = p; n != null; n = n.getParent())
      if (!n.getActive() || !n.getSystem().getEnabled()) return false;
    return true;
  }

  /**
   * 把权限集合组装成森林，沿上级链补齐祖先作为导航容器：只有集合内的节点 {@code granted} 为 true，
   * 补出的祖先不代表拥有其授权或 API。本方法不按生效状态过滤，由调用方先筛选。同级节点按标识升序，与 DDD 路线一致，
   * 不随授权来源的遍历顺序变化。
   */
  public static List<PermissionData.Tree> tree(Collection<Permission> granted) {
    Map<Long, Permission> nodes = new TreeMap<>();
    Set<Long> exact = new HashSet<>();
    for (Permission p : granted) {
      exact.add(p.getId());
      for (Permission n = p; n != null; n = n.getParent()) nodes.putIfAbsent(n.getId(), n);
    }
    return nodes.values().stream()
        .filter(p -> p.getParent() == null)
        .map(p -> branch(p, nodes.values(), exact))
        .toList();
  }

  /** 递归构造以 {@code p} 为根的子树；下级沿用 {@code nodes} 的标识升序。 */
  private static PermissionData.Tree branch(
      Permission p, Collection<Permission> nodes, Set<Long> exact) {
    return new PermissionData.Tree(
        view(p),
        exact.contains(p.getId()),
        nodes.stream()
            .filter(n -> n.getParent() != null && n.getParent().getId().equals(p.getId()))
            .map(n -> branch(n, nodes, exact))
            .toList());
  }

  /**
   * 管理端权限目录树，含已失效节点；传入的就是全部节点，所以每个节点的 {@code granted} 都为 true。
   * {@code systemId} 为空时返回全部应用，不校验应用是否存在。
   */
  public List<PermissionData.Tree> tree(Long systemId) {
    return tree(
        (systemId == null
            ? permissions.findAll(org.springframework.data.domain.Sort.by("id"))
            : permissions.findAllBySystem_IdOrderById(systemId)));
  }

  /** 控制台的内置菜单与按钮：停用或删除后所有人（包括管理员）都用不了对应页面，可能连恢复它的页面也打不开，抛 {@code CONSOLE_BUILT_IN}（409）。 */
  private static void protectBuiltIn(Permission p) {
    if (ConsoleCatalog.isEntry(p.getCode()))
      throw new ConflictException("CONSOLE_BUILT_IN", "权限管理系统的内置菜单与按钮不能停用或删除");
  }
}
