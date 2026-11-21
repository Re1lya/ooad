package com.cdwy.permission.service.role;

import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.role.RoleUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色用例的结构化事务脚本实现（{@code app.backend-strategy=structured}，未配置时默认）：角色的增删改查与启停。
 *
 * <p>角色继承由 {@code RoleHierarchyService} 维护，用户与权限授权由 {@code AuthorizationService} 维护；{@link #require}、
 * {@link #view} 也供后者复用。写操作不记审计日志。
 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@Transactional(readOnly = true)
public class RoleService implements RoleUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final RoleRepository repository;
  private final com.cdwy.permission.repository.rbac.UserRoleRepository userRoles;
  private final com.cdwy.permission.repository.rbac.RolePermissionRepository rolePermissions;

  private final com.cdwy.permission.repository.rbac.RoleHierarchyRepository hierarchy;

  /** 按标识读取角色实体，授权用例也复用；不存在时抛 {@code ROLE_NOT_FOUND}（404）。 */
  public Role require(Long id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ROLE_NOT_FOUND", "角色不存在"));
  }

  /** 实体转换为与持久化无关的结果；列出用户的直接角色时也复用。 */
  public static RoleData.View view(Role value) {
    return new RoleData.View(
        value.getId(),
        value.getCode(),
        value.getName(),
        value.getDescription(),
        value.getEnabled());
  }

  /** 按标识升序列出全部角色，含已停用的和内置的权限管理员角色。 */
  public List<RoleData.View> list() {
    return repository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(RoleService::view)
        .toList();
  }

  /** 新建角色，默认启用；编码全局唯一，已存在时抛 {@code ROLE_CODE_EXISTS}（409），创建后不可修改。 */
  @Transactional
  public RoleData.View create(RoleData.Create input) {
    if (repository.existsByCode(input.code())) throw new ConflictException("ROLE_CODE_EXISTS", "编码已存在");
    Role value = new Role();
    value.setCode(input.code());
    value.setName(input.name());
    value.setDescription(input.description());
    return view(repository.save(value));
  }

  /** 修改名称与描述，编码不变；修改由 JPA 脏检查在事务提交时写回。 */
  @Transactional
  public RoleData.View update(Long id, RoleData.Update input) {
    Role value = require(id);
    value.setName(input.name());
    value.setDescription(input.description());
    return view(value);
  }

  /**
   * 启停角色，已有的分配、授权与继承关系都保留。停用的角色不再提供权限并阻断经过它的继承路径，也不能再被分配或用于新建继承。
   */
  @Transactional
  public RoleData.View status(Long id, boolean enabled) {
    Role value = require(id);
    value.setEnabled(enabled);
    return view(value);
  }

  /**
   * 删除角色：先按固定顺序锁定全部角色，与继承关系的修改串行化；仍是任一继承关系的一端时抛 {@code ROLE_HAS_INHERITANCE}，
   * 仍分配给用户或持有权限时抛 {@code ROLE_IN_USE}（均 409）。
   */
  @Transactional
  public void delete(Long id) {
    repository.lockHierarchyRoles();
    Role value = require(id);
    if (hierarchy.existsBySeniorRole_IdOrInheritedRole_Id(id, id))
      throw new ConflictException("ROLE_HAS_INHERITANCE", "仍有角色继承关系，请先解除关联");
    if (userRoles.existsByRole_Id(id) || rolePermissions.existsByRole_Id(id))
      throw new ConflictException("ROLE_IN_USE", "仍有授权或资源引用，请先解除关联");
    repository.delete(value);
  }
}
