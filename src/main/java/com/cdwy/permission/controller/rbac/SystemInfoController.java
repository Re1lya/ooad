package com.cdwy.permission.controller.rbac;

import com.cdwy.permission.service.port.rbac.RoleGraphUseCase;
import com.cdwy.permission.vo.rbac.SystemInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 实际装载模式与业务覆盖清单。 */
@RestController
@com.cdwy.permission.config.BusinessAccess(com.cdwy.permission.config.BusinessAccess.CONSOLE)
@RequiredArgsConstructor
public class SystemInfoController {
  private final RoleGraphUseCase graph;
  private final com.cdwy.permission.service.port.user.UserUseCase users;
  private final com.cdwy.permission.service.port.organization.DepartmentUseCase departments;
  private final com.cdwy.permission.service.port.system.SystemUseCase systems;
  private final com.cdwy.permission.service.port.role.RoleUseCase roles;
  private final com.cdwy.permission.service.port.resource.ResourceUseCase resources;
  private final com.cdwy.permission.service.port.permission.PermissionUseCase permissions;
  private final com.cdwy.permission.service.port.rbac.AuthorizationUseCase authorization;
  private final com.cdwy.permission.service.port.rbac.HierarchyUseCase hierarchy;
  private final com.cdwy.permission.service.port.auth.AuthenticationUseCase authentication;
  private final com.cdwy.permission.service.port.directory.DirectorySyncUseCase directorySync;
  private final com.cdwy.permission.service.port.identity.ApplicationIdentityUseCase identity;

  /** 来源为当前实际注入的端口实现。 */
  @GetMapping("/api/system-info")
  public SystemInfoVO info() {
    return new SystemInfoVO(
        graph.strategy(),
        "RBAC1",
        java.util.Map.ofEntries(
            java.util.Map.entry("users", users.strategy()),
            java.util.Map.entry("departments", departments.strategy()),
            java.util.Map.entry("systems", systems.strategy()),
            java.util.Map.entry("roles", roles.strategy()),
            java.util.Map.entry("resources", resources.strategy()),
            java.util.Map.entry("permissions", permissions.strategy()),
            java.util.Map.entry("authorization", authorization.strategy()),
            java.util.Map.entry("hierarchy", hierarchy.strategy()),
            java.util.Map.entry("authentication", authentication.strategy()),
            java.util.Map.entry("directorySync", directorySync.strategy()),
            java.util.Map.entry("identity", identity.strategy())));
  }
}
