package com.cdwy.permission.service.identity;

import com.cdwy.permission.entity.permission.Permission;
import com.cdwy.permission.entity.rbac.PermissionApi;
import com.cdwy.permission.entity.rbac.UserRole;
import com.cdwy.permission.entity.resource.ApiResource;
import com.cdwy.permission.entity.system.BusinessSystem;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.rbac.PermissionApiRepository;
import com.cdwy.permission.repository.rbac.UserRoleRepository;
import com.cdwy.permission.repository.system.BusinessSystemRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.data.IdentityData;
import com.cdwy.permission.service.port.error.ResourceNotFoundException;
import com.cdwy.permission.service.port.identity.ApplicationIdentityUseCase;
import com.cdwy.permission.service.rbac.AuthorizationService;
import com.cdwy.permission.service.rbac.RoleHierarchyService;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 结构化事务脚本：按“读应用 → 读用户 → 展开角色继承 → 汇总应用内权限码 → 追加绑定 API 权限码”的步骤计算接入应用身份。
 *
 * <p>数据（JPA 实体）与处理过程分离，步骤集中在服务方法中，直接使用 Spring Data Repository。
 */
@Service
@ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationIdentityService implements ApplicationIdentityUseCase {
  private static final Logger log = LoggerFactory.getLogger(ApplicationIdentityService.class);

  private final BusinessSystemRepository systems;
  private final UserAccountRepository users;
  private final UserRoleRepository userRoles;
  private final PermissionApiRepository bindings;
  private final RoleHierarchyService hierarchy;
  private final AuthorizationService authorization;

  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  /** 步骤 1-5：应用 → 用户 → 有效角色 → 应用内前端权限码 → 绑定的后端 API 权限码。 */
  @Override
  public IdentityData.Current current(Long userId, String systemCode) {
    BusinessSystem app = requireSystem(systemCode);
    UserAccount user =
        users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));

    // 步骤 3：直接分配角色 + 沿继承链可达的启用角色；停用角色阻断经过它的路径。
    Set<String> roles = new LinkedHashSet<>();
    Map<Long, List<com.cdwy.permission.entity.role.Role>> graph = hierarchy.graph();
    for (UserRole grant : userRoles.findAllByUser_Id(userId))
      for (RoleHierarchyService.ResolvedRole resolved : hierarchy.resolve(grant.getRole(), graph))
        roles.add(resolved.role().getCode());

    // 步骤 4、5：只汇总本应用的有效前端权限，以及它们绑定的启用且登记了权限码的 API 资源。
    Set<String> codes = new LinkedHashSet<>();
    if (app.getEnabled() && user.usable(Instant.now())) {
      List<Permission> granted =
          authorization.effective(userId).stream()
              .filter(p -> p.getSystem().getId().equals(app.getId()))
              .toList();
      granted.forEach(p -> codes.add(p.getCode()));
      for (Permission permission : granted)
        for (PermissionApi binding : bindings.findAllByPermission_Id(permission.getId())) {
          ApiResource resource = binding.getResource();
          if (resource.getActive()
              && resource.getCode() != null
              && resource.getSystem().getId().equals(app.getId())) codes.add(resource.getCode());
        }
    } else roles.clear();
    log.info(
        "接入应用 {} 查询身份：用户 {} 角色 {} 个、权限码 {} 个",
        systemCode,
        user.getUsername(),
        roles.size(),
        codes.size());
    return new IdentityData.Current(user.getUsername(), List.copyOf(roles), List.copyOf(codes));
  }

  /** 应用存在时按登录名查询人员；工号沿用登录名。 */
  @Override
  public IdentityData.User lookup(String systemCode, String userId) {
    requireSystem(systemCode);
    UserAccount user =
        users
            .findByUsername(userId)
            .orElseThrow(() -> new ResourceNotFoundException("统一身份用户不存在"));
    return new IdentityData.User(user.getUsername(), user.getUsername(), user.getDisplayName());
  }

  private BusinessSystem requireSystem(String code) {
    return systems.findByCode(code).orElseThrow(() -> new ResourceNotFoundException("接入应用不存在"));
  }
}
