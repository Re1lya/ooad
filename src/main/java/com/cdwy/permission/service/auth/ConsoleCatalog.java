package com.cdwy.permission.service.auth;

import com.cdwy.permission.service.port.data.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限系统自身（应用 rbac）的内置授权目录：控制台每个页面一个菜单，写操作一个按钮，管理接口登记为 API 资源并绑定到菜单或按钮。
 *
 * <p>一个读接口可以绑定多个菜单（例如角色列表同时服务“角色管理”“角色继承”“授权工作台”“用户管理”页面），体现前端权限与 API 的多对多关系。
 * “我的权限”“修改密码”等个人接口只要求登录，不在此登记。
 */
public final class ConsoleCatalog {
  private ConsoleCatalog() {}

  /** 应用编码，与 JWT 中控制台令牌的 aud 一致。 */
  public static final String SYSTEM = JwtTokenService.CONSOLE_AUDIENCE;

  /** rbac 应用首次登记时的名称；之后管理员改的名称不会被覆盖。 */
  public static final String SYSTEM_NAME = "权限管理系统";

  /** 内置管理员角色：拥有 rbac 应用的全部菜单与按钮，分配给启动管理员。 */
  public static final String ADMIN_ROLE = "rbac-admin";

  /** 内置管理员角色首次创建时的名称。 */
  public static final String ADMIN_ROLE_NAME = "权限管理员";

  /** 菜单或按钮；按钮的 parent 是所属菜单的编码。 */
  public record Entry(String code, String name, PermissionType type, String menuPath, String parent) {}

  /** 管理接口与它绑定的菜单或按钮编码。 */
  public record Api(String name, String method, String path, List<String> grantedBy) {}

  private static Entry menu(String code, String name, String path) {
    return new Entry(code, name, PermissionType.MENU, path, null);
  }

  private static Entry button(String code, String name, String parent) {
    return new Entry(code, name, PermissionType.BUTTON, null, parent);
  }

  private static Api api(String name, String method, String path, String... grantedBy) {
    return new Api(name, method, path, List.of(grantedBy));
  }

  /** 控制台的菜单与按钮。顺序有意义：按钮必须排在所属菜单之后，登记时按顺序查找上级。 */
  public static final List<Entry> ENTRIES =
      List.of(
          menu("rbac.overview", "工作台", "/overview"),
          menu("rbac.users", "用户管理", "/users"),
          button("rbac.users.write", "维护用户", "rbac.users"),
          button("rbac.directory.sync", "同步员工信息", "rbac.users"),
          menu("rbac.departments", "部门管理", "/departments"),
          button("rbac.departments.write", "维护部门", "rbac.departments"),
          menu("rbac.systems", "应用管理", "/systems"),
          button("rbac.systems.write", "维护应用", "rbac.systems"),
          menu("rbac.roles", "角色管理", "/roles"),
          button("rbac.roles.write", "维护角色", "rbac.roles"),
          menu("rbac.hierarchy", "角色继承", "/hierarchy"),
          button("rbac.hierarchy.write", "维护继承关系", "rbac.hierarchy"),
          menu("rbac.permissions", "菜单与按钮", "/permissions"),
          button("rbac.permissions.write", "维护菜单与按钮", "rbac.permissions"),
          menu("rbac.resources", "API 资源", "/resources"),
          button("rbac.resources.write", "维护 API 资源", "rbac.resources"),
          menu("rbac.grants", "授权工作台", "/grants"),
          button("rbac.grants.write", "授予与撤销权限", "rbac.grants"),
          menu("rbac.effective", "有效权限", "/effective"),
          menu("rbac.audit", "审计日志", "/audit"));

  private static final String USERS = "rbac.users", USERS_W = "rbac.users.write";

  /**
   * 控制台的管理接口及其绑定的菜单或按钮。按应用 rbac 鉴权的控制器接口（{@code /api/me} 个人接口除外）必须与本清单一一对应，
   * 由测试 FR-38 校验；未登记的接口请求一律拒绝（403）。
   */
  public static final List<Api> APIS =
      List.of(
          api("系统信息", "GET", "/api/system-info", "rbac.overview"),
          api("用户列表", "GET", "/api/users", USERS, "rbac.effective", "rbac.overview"),
          api("用户详情", "GET", "/api/users/{id}", USERS, "rbac.effective"),
          api("新建用户", "POST", "/api/users", USERS_W),
          api("修改用户", "PUT", "/api/users/{id}", USERS_W),
          api("启停用户", "PUT", "/api/users/{id}/status", USERS_W),
          api("重置密码", "PUT", "/api/users/{id}/password", USERS_W),
          api("删除用户", "DELETE", "/api/users/{id}", USERS_W),
          api("用户的角色", "GET", "/api/users/{userId}/roles", USERS, "rbac.effective"),
          api("分配角色", "PUT", "/api/users/{userId}/roles/{roleId}", USERS_W),
          api("取消角色", "DELETE", "/api/users/{userId}/roles/{roleId}", USERS_W),
          api("预览员工信息同步", "POST", "/api/directory/sync/preview", "rbac.directory.sync"),
          api("确认员工信息同步", "POST", "/api/directory/sync/commit", "rbac.directory.sync"),
          api("部门列表", "GET", "/api/departments", "rbac.departments", USERS),
          api("新建部门", "POST", "/api/departments", "rbac.departments.write"),
          api("修改部门", "PUT", "/api/departments/{id}", "rbac.departments.write"),
          api("删除部门", "DELETE", "/api/departments/{id}", "rbac.departments.write"),
          api(
              "应用列表",
              "GET",
              "/api/systems",
              "rbac.systems",
              "rbac.permissions",
              "rbac.resources",
              "rbac.grants",
              "rbac.audit",
              "rbac.overview"),
          api("接入应用", "POST", "/api/systems", "rbac.systems.write"),
          api("修改应用", "PUT", "/api/systems/{id}", "rbac.systems.write"),
          api("启停应用", "PUT", "/api/systems/{id}/status", "rbac.systems.write"),
          api("删除应用", "DELETE", "/api/systems/{id}", "rbac.systems.write"),
          api(
              "角色列表",
              "GET",
              "/api/roles",
              "rbac.roles",
              "rbac.hierarchy",
              "rbac.grants",
              USERS,
              "rbac.overview"),
          api("新建角色", "POST", "/api/roles", "rbac.roles.write"),
          api("修改角色", "PUT", "/api/roles/{id}", "rbac.roles.write"),
          api("启停角色", "PUT", "/api/roles/{id}/status", "rbac.roles.write"),
          api("删除角色", "DELETE", "/api/roles/{id}", "rbac.roles.write"),
          api("角色层级", "GET", "/api/role-hierarchy", "rbac.hierarchy", "rbac.roles"),
          api("角色有效权限", "GET", "/api/roles/{roleId}/effective-permissions", "rbac.hierarchy", "rbac.grants"),
          api("建立继承", "PUT", "/api/roles/{seniorId}/inherits/{inheritedId}", "rbac.hierarchy.write"),
          api("解除继承", "DELETE", "/api/roles/{seniorId}/inherits/{inheritedId}", "rbac.hierarchy.write"),
          api(
              "菜单与按钮列表",
              "GET",
              "/api/permissions",
              "rbac.permissions",
              "rbac.grants",
              "rbac.hierarchy",
              "rbac.resources"),
          api("菜单树", "GET", "/api/permissions/tree", "rbac.permissions", "rbac.grants"),
          api("新建菜单或按钮", "POST", "/api/permissions", "rbac.permissions.write"),
          api("修改菜单或按钮", "PUT", "/api/permissions/{id}", "rbac.permissions.write"),
          api("启停菜单或按钮", "PUT", "/api/permissions/{id}/status", "rbac.permissions.write"),
          api("删除菜单或按钮", "DELETE", "/api/permissions/{id}", "rbac.permissions.write"),
          api("菜单绑定的接口", "GET", "/api/permissions/{id}/resources", "rbac.permissions", "rbac.resources"),
          api("绑定接口", "PUT", "/api/permissions/{id}/resources/{resourceId}", "rbac.resources.write"),
          api("解绑接口", "DELETE", "/api/permissions/{id}/resources/{resourceId}", "rbac.resources.write"),
          api("API 资源列表", "GET", "/api/resources", "rbac.resources", "rbac.permissions"),
          api("登记接口", "POST", "/api/resources", "rbac.resources.write"),
          api("修改接口", "PUT", "/api/resources/{id}", "rbac.resources.write"),
          api("启停接口", "PUT", "/api/resources/{id}/status", "rbac.resources.write"),
          api("删除接口", "DELETE", "/api/resources/{id}", "rbac.resources.write"),
          api("角色的直接权限", "GET", "/api/roles/{roleId}/permissions", "rbac.grants", "rbac.hierarchy"),
          api("授予权限", "PUT", "/api/roles/{roleId}/permissions/{permissionId}", "rbac.grants.write"),
          api("撤销权限", "DELETE", "/api/roles/{roleId}/permissions/{permissionId}", "rbac.grants.write"),
          api("用户权限来源", "GET", "/api/users/{userId}/permission-origins", "rbac.effective", USERS),
          api("审计日志", "GET", "/api/audit-logs", "rbac.audit", "rbac.overview"));

  /** 内置菜单与按钮的编码；放在 ENTRIES 之后初始化。 */
  private static final Set<String> ENTRY_CODES =
      ENTRIES.stream().map(Entry::code).collect(Collectors.toUnmodifiableSet());

  /** 内置管理接口的“方法 路径”；放在 APIS 之后初始化。 */
  private static final Set<String> API_KEYS =
      APIS.stream().map(api -> api.method() + " " + api.path()).collect(Collectors.toUnmodifiableSet());

  /** 是否为权限系统自身的内置应用 rbac。 */
  public static boolean isSystem(String systemCode) {
    return SYSTEM.equals(systemCode);
  }

  /** 是否为内置管理员角色 rbac-admin。 */
  public static boolean isAdminRole(String roleCode) {
    return ADMIN_ROLE.equals(roleCode);
  }

  /** 是否为控制台的内置菜单或按钮。权限编码全局唯一，内置编码只会属于 rbac 应用，所以按编码判断即可。 */
  public static boolean isEntry(String permissionCode) {
    return ENTRY_CODES.contains(permissionCode);
  }

  /** 是否为控制台的内置管理接口：属于 rbac 应用，且方法与路径在内置目录中（不同应用可以登记相同的方法与路径）。 */
  public static boolean isApi(String systemCode, String method, String path) {
    return isSystem(systemCode) && API_KEYS.contains(method + " " + path);
  }
}
