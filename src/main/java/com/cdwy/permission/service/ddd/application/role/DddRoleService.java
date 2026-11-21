package com.cdwy.permission.service.ddd.application.role;

import com.cdwy.permission.service.auth.ConsoleCatalog;
import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.role.RoleUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddRoleService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddRoleService implements RoleUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final RoleCatalog roles;
  private final AssignmentRepository assignments;

  /** 读取业务角色。 */
  private AccessRole require(Long id) {
    return roles.find(id).orElseThrow(() -> new ResourceNotFoundException("ROLE_NOT_FOUND", "角色不存在"));
  }

  /** 角色结果。 */
  private RoleData.View view(AccessRole n) {
    return new RoleData.View(
        n.getId(), n.getCode(), n.getName(), n.getDescription(), n.getEnabled());
  }

  /** 角色目录。 */
  public List<RoleData.View> list() {
    return roles.all().stream().map(this::view).toList();
  }

  /** 新建角色不等于系统管理员身份。 */
  @Transactional
  public RoleData.View create(RoleData.Create input) {
    if (roles.all().stream().anyMatch(n -> n.getCode().equals(input.code())))
      throw new ConflictException("ROLE_CODE_EXISTS", "编码已存在");
    AccessRole n = new AccessRole();
    n.setCode(input.code());
    n.rename(input.name(), input.description());
    return view(roles.save(n));
  }

  /** 修改角色资料。 */
  @Transactional
  public RoleData.View update(Long id, RoleData.Update input) {
    AccessRole n = require(id);
    n.rename(input.name(), input.description());
    return view(roles.save(n));
  }

  /** 停用保留显式关系，由领域图阻断继承。 */
  @Transactional
  public RoleData.View status(Long id, boolean enabled) {
    AccessRole n = require(id);
    n.changeStatus(enabled, ConsoleCatalog.isAdminRole(n.getCode()));
    return view(roles.save(n));
  }

  /** 与继承修改共享图写锁。 */
  @Transactional
  public void delete(Long id) {
    roles.lockGraph();
    AccessRole n = require(id);
    boolean graph =
        assignments.hierarchy().stream().anyMatch(e -> e.left().equals(id) || e.right().equals(id));
    boolean granted =
        assignments.userRoles().stream().anyMatch(e -> e.right().equals(id))
            || assignments.rolePermissions().stream().anyMatch(e -> e.left().equals(id));
    n.assertDeletable(ConsoleCatalog.isAdminRole(n.getCode()), graph, granted);
    roles.delete(id);
  }
}
