package com.cdwy.permission.controller.rbac;

import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.permission.PermissionDtos;
import com.cdwy.permission.dto.role.RoleDtos;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@com.cdwy.permission.config.BusinessAccess(com.cdwy.permission.service.auth.ConsoleCatalog.SYSTEM)
@RequestMapping("/api")
public class GrantController {
  private final AuthorizationUseCase service;

  public GrantController(AuthorizationUseCase service) {
    this.service = service;
  }

  @GetMapping("/users/{userId}/roles")
  public List<RoleDtos.View> roles(@PathVariable Long userId) {
    return service.roles(userId).stream().map(HttpMapping::view).toList();
  }

  @PutMapping("/users/{userId}/roles/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void grantRole(@PathVariable Long userId, @PathVariable Long roleId) {
    service.grantRole(userId, roleId);
  }

  @DeleteMapping("/users/{userId}/roles/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeRole(@PathVariable Long userId, @PathVariable Long roleId) {
    service.revokeRole(userId, roleId);
  }

  @GetMapping("/roles/{roleId}/permissions")
  public List<PermissionDtos.View> permissions(@PathVariable Long roleId) {
    return service.rolePermissions(roleId).stream().map(HttpMapping::view).toList();
  }

  @PutMapping("/roles/{roleId}/permissions/{permissionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void grantPermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
    service.grantPermission(roleId, permissionId);
  }

  @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokePermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
    service.revokePermission(roleId, permissionId);
  }
}
