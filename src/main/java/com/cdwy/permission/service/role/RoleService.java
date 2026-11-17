package com.cdwy.permission.service.role;

import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.role.RoleUseCase;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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

  public RoleService(
      RoleRepository repository,
      com.cdwy.permission.repository.rbac.UserRoleRepository userRoles,
      com.cdwy.permission.repository.rbac.RolePermissionRepository rolePermissions,
      com.cdwy.permission.repository.rbac.RoleHierarchyRepository hierarchy) {
    this.repository = repository;
    this.hierarchy = hierarchy;
    this.userRoles = userRoles;
    this.rolePermissions = rolePermissions;
  }

  public Role require(Long id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("对象不存在"));
  }

  public static RoleData.View view(Role value) {
    return new RoleData.View(
        value.getId(),
        value.getCode(),
        value.getName(),
        value.getDescription(),
        value.getEnabled());
  }

  public List<RoleData.View> list() {
    return repository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(RoleService::view)
        .toList();
  }

  @Transactional
  public RoleData.View create(RoleData.Create input) {
    if (repository.existsByCode(input.code())) throw new ConflictException("编码已存在");
    Role value = new Role();
    value.setCode(input.code());
    value.setName(input.name());
    value.setDescription(input.description());
    return view(repository.save(value));
  }

  @Transactional
  public RoleData.View update(Long id, RoleData.Update input) {
    Role value = require(id);
    value.setName(input.name());
    value.setDescription(input.description());
    return view(value);
  }

  @Transactional
  public RoleData.View status(Long id, boolean enabled) {
    Role value = require(id);
    value.setEnabled(enabled);
    return view(value);
  }

  @Transactional
  public void delete(Long id) {
    repository.lockHierarchyRoles();
    Role value = require(id);
    if (hierarchy.existsBySeniorRole_IdOrInheritedRole_Id(id, id))
      throw new ConflictException("仍有角色继承关系，请先解除关联");
    if (userRoles.existsByRole_Id(id) || rolePermissions.existsByRole_Id(id))
      throw new ConflictException("仍有授权或资源引用，请先解除关联");
    repository.delete(value);
  }
}
