package com.cdwy.permission.controller.rbac;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import com.cdwy.permission.service.port.rbac.HierarchyUseCase;
import com.cdwy.permission.vo.role.PermissionOriginVO;
import com.cdwy.permission.vo.role.RoleInheritanceVO;
import jakarta.servlet.http.HttpServletRequest;
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

/** RBAC1 角色继承与权限来源；管理身份由 API 拦截器统一校验，本人来源只取当前身份。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api")
public class RoleHierarchyController {
  private final HierarchyUseCase hierarchy;
  private final AuthorizationUseCase authorization;
  private final HierarchyVoMapper mapper;

  /** 全部直接继承关系。 */
  @GetMapping("/role-hierarchy")
  public List<RoleInheritanceVO> edges() {
    return hierarchy.edges().stream().map(mapper::toVO).toList();
  }

  /** 高级角色继承被继承角色的权限；成环时拒绝，重复请求幂等。 */
  @PutMapping("/roles/{seniorId}/inherits/{inheritedId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void inherit(@PathVariable Long seniorId, @PathVariable Long inheritedId) {
    hierarchy.inherit(seniorId, inheritedId);
  }

  /** 解除一条直接继承关系。 */
  @DeleteMapping("/roles/{seniorId}/inherits/{inheritedId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable Long seniorId, @PathVariable Long inheritedId) {
    hierarchy.remove(seniorId, inheritedId);
  }

  /** 预览角色的有效权限及来源。 */
  @GetMapping("/roles/{roleId}/effective-permissions")
  public List<PermissionOriginVO> role(@PathVariable Long roleId) {
    return authorization.roleOrigins(roleId).stream().map(mapper::toVO).toList();
  }

  /** 管理员查看用户的有效权限及来源。 */
  @GetMapping("/users/{userId}/permission-origins")
  public List<PermissionOriginVO> user(@PathVariable Long userId) {
    return authorization.origins(userId).stream().map(mapper::toVO).toList();
  }

  /** 本人的有效权限及来源。 */
  @GetMapping("/me/permission-origins")
  public List<PermissionOriginVO> me(HttpServletRequest request) {
    return authorization.origins(CurrentUser.from(request).id()).stream()
        .map(mapper::toVO)
        .toList();
  }
}
