package com.cdwy.permission.service.auth;

import com.cdwy.permission.entity.permission.Permission;
import com.cdwy.permission.entity.rbac.PermissionApi;
import com.cdwy.permission.entity.rbac.RolePermission;
import com.cdwy.permission.entity.rbac.UserRole;
import com.cdwy.permission.entity.resource.ApiResource;
import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.entity.system.BusinessSystem;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.permission.PermissionRepository;
import com.cdwy.permission.repository.rbac.PermissionApiRepository;
import com.cdwy.permission.repository.rbac.RolePermissionRepository;
import com.cdwy.permission.repository.rbac.UserRoleRepository;
import com.cdwy.permission.repository.resource.ApiResourceRepository;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.repository.system.BusinessSystemRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动时把权限系统自身登记为应用 rbac（{@link ConsoleCatalog}）：幂等地补齐应用、菜单、按钮、管理接口及其绑定，以及拥有全部菜单和按钮的
 * 内置管理员角色。这是部署时的初始数据，不提供 HTTP 接口；两种设计路线共用同一份数据。
 *
 * <p>不覆盖管理员修改过的名称。为防止误操作把所有人锁在控制台之外，每次启动还会：补齐内置管理员角色的授权与内置接口绑定；把被停用的内置应用、
 * 内置管理员角色、内置菜单按钮与管理接口恢复启用（管理接口本身也拒绝停用它们，这里兜住早先的数据或直接改库的情况）。
 */
@Service
@RequiredArgsConstructor
public class ConsoleRegistrationService {
  private static final Logger log = LoggerFactory.getLogger(ConsoleRegistrationService.class);

  private final BusinessSystemRepository systems;
  private final PermissionRepository permissions;
  private final ApiResourceRepository resources;
  private final PermissionApiRepository bindings;
  private final RoleRepository roles;
  private final RolePermissionRepository rolePermissions;
  private final UserRoleRepository userRoles;

  /** 补齐 rbac 应用的全部内置数据并恢复被停用的内置对象，返回内置管理员角色。 */
  @Transactional
  public Role ensure() {
    List<String> restored = new ArrayList<>();
    BusinessSystem app =
        systems
            .findByCode(ConsoleCatalog.SYSTEM)
            .orElseGet(
                () -> {
                  BusinessSystem value = new BusinessSystem();
                  value.setCode(ConsoleCatalog.SYSTEM);
                  value.setName(ConsoleCatalog.SYSTEM_NAME);
                  value.setBaseUrl("/ui/");
                  return systems.save(value);
                });
    if (!app.getEnabled()) {
      app.setEnabled(true);
      restored.add("应用 " + app.getCode());
    }
    Map<String, Permission> entries = new HashMap<>();
    for (ConsoleCatalog.Entry entry : ConsoleCatalog.ENTRIES) {
      Permission permission =
          permissions
              .findByCode(entry.code())
              .orElseGet(
                  () -> {
                    Permission value = new Permission();
                    value.setSystem(app);
                    value.setCode(entry.code());
                    value.setName(entry.name());
                    value.setType(entry.type());
                    value.setMenuPath(entry.menuPath());
                    value.setParent(entry.parent() == null ? null : entries.get(entry.parent()));
                    return permissions.save(value);
                  });
      if (!permission.getActive()) {
        permission.setActive(true);
        restored.add("菜单或按钮 " + permission.getCode());
      }
      entries.put(entry.code(), permission);
    }
    int created = 0;
    for (ConsoleCatalog.Api api : ConsoleCatalog.APIS) {
      ApiResource resource =
          resources
              .findBySystem_IdAndMethodAndPath(app.getId(), api.method(), api.path())
              .orElse(null);
      if (resource == null) {
        resource = new ApiResource();
        resource.setSystem(app);
        resource.setName(api.name());
        resource.setMethod(api.method());
        resource.setPath(api.path());
        resource = resources.save(resource);
        created++;
      } else if (!resource.getActive()) {
        resource.setActive(true);
        restored.add("接口 " + api.method() + " " + api.path());
      }
      for (String code : api.grantedBy()) {
        Permission permission = entries.get(code);
        if (!bindings.existsByPermission_IdAndResource_Id(permission.getId(), resource.getId()))
          bindings.save(new PermissionApi(permission, resource));
      }
    }
    Role admin =
        roles
            .findByCode(ConsoleCatalog.ADMIN_ROLE)
            .orElseGet(
                () -> {
                  Role value = new Role();
                  value.setCode(ConsoleCatalog.ADMIN_ROLE);
                  value.setName(ConsoleCatalog.ADMIN_ROLE_NAME);
                  value.setDescription("内置：拥有权限系统的全部菜单与按钮");
                  return roles.save(value);
                });
    if (!admin.getEnabled()) {
      admin.setEnabled(true);
      restored.add("角色 " + admin.getCode());
    }
    for (Permission permission : entries.values())
      if (!rolePermissions.existsByRole_IdAndPermission_Id(admin.getId(), permission.getId()))
        rolePermissions.save(new RolePermission(admin, permission));
    if (created > 0) log.info("已登记权限系统自身：{} 个菜单与按钮、{} 个新接口", entries.size(), created);
    if (!restored.isEmpty()) log.warn("内置对象处于停用状态，已在启动时恢复启用：{}", restored);
    return admin;
  }

  /** 把内置管理员角色分配给启动管理员（已分配时不重复）。 */
  @Transactional
  public void assignAdministrator(UserAccount user, Role admin) {
    if (!userRoles.existsByUser_IdAndRole_Id(user.getId(), admin.getId()))
      userRoles.save(new UserRole(user, admin));
  }
}
