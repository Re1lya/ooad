package com.cdwy.permission.controller.rbac;

import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.role.RoleHierarchyDtos;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import com.cdwy.permission.service.port.rbac.HierarchyUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** RBAC1 管理接口；管理身份由现有 API 拦截器统一校验，个人来源只取当前身份。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RoleHierarchyController {
  private final HierarchyUseCase hierarchy;
  private final AuthorizationUseCase authorization;

  /** 查询显式角色继承关系。 */
  @GetMapping("/role-hierarchy")
  public List<RoleHierarchyDtos.Edge> edges() {
    return hierarchy.edges().stream().map(HttpMapping::view).toList();
  }

  /** 高级角色获得被继承角色权限，重复请求幂等。 */
  @PutMapping("/roles/{seniorId}/inherits/{inheritedId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void inherit(@PathVariable Long seniorId, @PathVariable Long inheritedId) {
    hierarchy.inherit(seniorId, inheritedId);
  }

  /** 移除直接继承关系。 */
  @DeleteMapping("/roles/{seniorId}/inherits/{inheritedId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable Long seniorId, @PathVariable Long inheritedId) {
    hierarchy.remove(seniorId, inheritedId);
  }

  /** 预览指定角色的有效权限和来源。 */
  @GetMapping("/roles/{roleId}/effective-permissions")
  public List<RoleHierarchyDtos.PermissionOrigin> role(@PathVariable Long roleId) {
    return authorization.roleOrigins(roleId).stream().map(HttpMapping::view).toList();
  }

  /** 管理员查询用户权限来源。 */
  @GetMapping("/users/{userId}/permission-origins")
  public List<RoleHierarchyDtos.PermissionOrigin> user(@PathVariable Long userId) {
    return authorization.origins(userId).stream().map(HttpMapping::view).toList();
  }

  /** 用户读取本人有效权限来源。 */
  @GetMapping("/me/permission-origins")
  public List<RoleHierarchyDtos.PermissionOrigin> me(HttpServletRequest request) {
    return authorization.origins(CurrentUser.from(request).id()).stream()
        .map(HttpMapping::view)
        .toList();
  }
}
