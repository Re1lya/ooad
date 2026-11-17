package com.cdwy.permission.infrastructure.persistence;

import com.cdwy.permission.entity.rbac.*;
import com.cdwy.permission.repository.permission.PermissionRepository;
import com.cdwy.permission.repository.rbac.*;
import com.cdwy.permission.repository.resource.ApiResourceRepository;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 授权关联适配器：只返回两端标识，保存时还原实体关联。 */
@Repository
@RequiredArgsConstructor
public class JpaAssignmentAdapter implements AssignmentRepository {
  private final UserRoleRepository users;
  private final RolePermissionRepository roles;
  private final PermissionApiRepository apis;
  private final RoleHierarchyRepository graph;
  private final UserAccountRepository accounts;
  private final RoleRepository roleData;
  private final PermissionRepository permissions;
  private final ApiResourceRepository resources;

  /** 全部用户到角色关系快照。 */
  public List<Link> userRoles() {
    return users.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(e -> new Link(e.getUser().getId(), e.getRole().getId()))
        .toList();
  }

  /** 全部角色到权限关系快照。 */
  public List<Link> rolePermissions() {
    return roles.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(e -> new Link(e.getRole().getId(), e.getPermission().getId()))
        .toList();
  }

  /** 全部前端权限到 API 关系快照。 */
  public List<Link> bindings() {
    return apis.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(e -> new Link(e.getPermission().getId(), e.getResource().getId()))
        .toList();
  }

  /** 显式继承边，不展开传递闭包。 */
  public List<Link> hierarchy() {
    return graph.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(e -> new Link(e.getSeniorRole().getId(), e.getInheritedRole().getId()))
        .toList();
  }

  /** 唯一约束处转译，保持稳定错误契约。 */
  private void write(Runnable action) {
    try {
      action.run();
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw new com.cdwy.permission.service.port.error.ConflictException("数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /** 幂等授权用户角色。 */
  public void addUserRole(Long user, Long role) {
    if (!users.existsByUser_IdAndRole_Id(user, role))
      write(
          () ->
              users.saveAndFlush(
                  new UserRole(accounts.getReferenceById(user), roleData.getReferenceById(role))));
  }

  /** 幂等撤销用户角色。 */
  public void removeUserRole(Long user, Long role) {
    users.deleteByUser_IdAndRole_Id(user, role);
  }

  /** 删除账号时解除其角色。 */
  public void removeUserRoles(Long user) {
    users.deleteAllByUser_Id(user);
  }

  /** 幂等授予角色权限。 */
  public void addRolePermission(Long role, Long permission) {
    if (!roles.existsByRole_IdAndPermission_Id(role, permission))
      write(
          () ->
              roles.saveAndFlush(
                  new RolePermission(
                      roleData.getReferenceById(role), permissions.getReferenceById(permission))));
  }

  /** 撤回当前显式授权。 */
  public void removeRolePermission(Long role, Long permission) {
    roles.deleteByRole_IdAndPermission_Id(role, permission);
  }

  /** 幂等绑定同应用资源。 */
  public void bind(Long permission, Long resource) {
    if (!apis.existsByPermission_IdAndResource_Id(permission, resource))
      write(
          () ->
              apis.saveAndFlush(
                  new PermissionApi(
                      permissions.getReferenceById(permission),
                      resources.getReferenceById(resource))));
  }

  /** 解除显式 API 绑定。 */
  public void unbind(Long permission, Long resource) {
    apis.deleteByPermission_IdAndResource_Id(permission, resource);
  }

  /** 领域无环验证后保存显式边。 */
  public void inherit(Long senior, Long inherited) {
    if (!graph.existsBySeniorRole_IdAndInheritedRole_Id(senior, inherited))
      write(
          () ->
              graph.saveAndFlush(
                  new RoleHierarchy(
                      roleData.getReferenceById(senior), roleData.getReferenceById(inherited))));
  }

  /** 解除单条继承边。 */
  public void removeInheritance(Long senior, Long inherited) {
    graph.deleteBySeniorRole_IdAndInheritedRole_Id(senior, inherited);
  }
}
