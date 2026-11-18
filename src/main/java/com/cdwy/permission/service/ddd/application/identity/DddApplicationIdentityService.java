package com.cdwy.permission.service.ddd.application.identity;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository.Link;
import com.cdwy.permission.service.port.auth.CredentialGateway;
import com.cdwy.permission.service.port.data.IdentityData;
import com.cdwy.permission.service.port.data.PermissionData;
import com.cdwy.permission.service.port.error.ResourceNotFoundException;
import com.cdwy.permission.service.port.identity.ApplicationIdentityUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DDD 应用服务：只负责加载聚合与编排，规则由领域对象承担。
 *
 * <ul>
 *   <li>{@link AuthorizationSnapshot}：展开角色继承、计算有效角色与应用内有效权限（信息专家）；
 *   <li>{@link ManagedSystem}：自身是否启用；{@link Account}：账号是否可用；
 *   <li>{@link ProtectedResource#authorityCode()}：资源自己判断能否提供后端权限码。
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddApplicationIdentityService implements ApplicationIdentityUseCase {
  private static final Logger log = LoggerFactory.getLogger(DddApplicationIdentityService.class);

  private final AccountRepository accounts;
  private final RoleCatalog roles;
  private final PermissionCatalog permissions;
  private final SystemCatalog systems;
  private final ResourceCatalog resources;
  private final AssignmentRepository assignments;
  private final CredentialGateway credentials;

  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  /** 加载快照后交给领域对象计算；应用停用或账号失效时不返回任何角色与权限。 */
  @Override
  public IdentityData.Current current(Long userId, String systemCode) {
    ManagedSystem app = system(systemCode);
    Account account =
        accounts.find(userId).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    if (!app.getEnabled() || !account.usable(credentials.now()))
      return new IdentityData.Current(account.getUsername(), List.of(), List.of());

    AuthorizationSnapshot snapshot =
        new AuthorizationSnapshot(
            roles.all(),
            permissions.all(),
            systems.all(),
            assignments.hierarchy(),
            assignments.rolePermissions());
    List<Long> assigned =
        assignments.userRoles().stream()
            .filter(link -> link.left().equals(userId))
            .map(Link::right)
            .toList();

    List<String> roleCodes =
        snapshot.effectiveRoles(assigned).stream().map(AccessRole::getCode).toList();
    List<PermissionData.View> granted = snapshot.grantedIn(app.getId(), assigned);
    Set<Long> grantedIds = new HashSet<>();
    Set<String> codes = new LinkedHashSet<>();
    for (PermissionData.View view : granted) {
      grantedIds.add(view.id());
      codes.add(view.code());
    }
    List<Link> bindings = assignments.bindings();
    for (ProtectedResource resource : resources.all())
      if (resource.getSystemId().equals(app.getId())
          && bindings.stream()
              .anyMatch(b -> b.right().equals(resource.getId()) && grantedIds.contains(b.left())))
        resource.authorityCode().ifPresent(codes::add);
    log.info(
        "接入应用 {} 查询身份：用户 {} 角色 {} 个、权限码 {} 个",
        systemCode,
        account.getUsername(),
        roleCodes.size(),
        codes.size());
    return new IdentityData.Current(account.getUsername(), roleCodes, List.copyOf(codes));
  }

  /** 按登录名查询人员，工号沿用登录名。 */
  @Override
  public IdentityData.User lookup(String systemCode, String userId) {
    system(systemCode);
    Account account =
        accounts.username(userId).orElseThrow(() -> new ResourceNotFoundException("统一身份用户不存在"));
    return new IdentityData.User(
        account.getUsername(), account.getUsername(), account.getDisplayName());
  }

  private ManagedSystem system(String code) {
    return systems.all().stream()
        .filter(app -> app.getCode().equals(code))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("接入应用不存在"));
  }
}
