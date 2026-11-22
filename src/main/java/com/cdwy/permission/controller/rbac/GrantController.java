package com.cdwy.permission.controller.rbac;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.controller.permission.PermissionVoMapper;
import com.cdwy.permission.controller.role.RoleVoMapper;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import com.cdwy.permission.vo.permission.PermissionVO;
import com.cdwy.permission.vo.role.RoleVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 授权：给用户分配角色、给角色授予菜单与按钮；重复授权与重复撤销都幂等。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api")
public class GrantController {
  private final AuthorizationUseCase service;
  private final RoleVoMapper roleMapper;
  private final PermissionVoMapper permissionMapper;

  /** 用户直接分配的角色。 */
  @GetMapping("/users/{userId}/roles")
  public List<RoleVO> roles(@PathVariable Long userId) {
    return service.roles(userId).stream().map(roleMapper::toVO).toList();
  }

  /** 给用户分配角色。 */
  @PutMapping("/users/{userId}/roles/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void grantRole(@PathVariable Long userId, @PathVariable Long roleId) {
    service.grantRole(userId, roleId);
  }

  /** 取消用户的角色。 */
  @DeleteMapping("/users/{userId}/roles/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeRole(@PathVariable Long userId, @PathVariable Long roleId) {
    service.revokeRole(userId, roleId);
  }

  /** 角色直接拥有的菜单与按钮。 */
  @GetMapping("/roles/{roleId}/permissions")
  public List<PermissionVO> permissions(@PathVariable Long roleId) {
    return service.rolePermissions(roleId).stream().map(permissionMapper::toVO).toList();
  }

  /** 给角色授予菜单或按钮。 */
  @PutMapping("/roles/{roleId}/permissions/{permissionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void grantPermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
    service.grantPermission(roleId, permissionId);
  }

  /** 撤销角色的菜单或按钮。 */
  @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokePermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
    service.revokePermission(roleId, permissionId);
  }
}
