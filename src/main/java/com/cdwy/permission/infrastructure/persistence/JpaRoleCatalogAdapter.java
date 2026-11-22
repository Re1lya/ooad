package com.cdwy.permission.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.service.ddd.domain.model.AccessRole;
import com.cdwy.permission.service.ddd.domain.repository.RoleCatalog;
import com.cdwy.permission.service.port.error.ConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** RoleCatalog 的 JPA 适配器：实体不离开基础设施边界。 */
@Repository
@RequiredArgsConstructor
public class JpaRoleCatalogAdapter implements RoleCatalog {
  private final RoleRepository data;

  /** 按主键读取并拷贝为领域对象，不加锁；不存在时返回空 Optional。 */
  public Optional<AccessRole> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按主键升序读取全部角色（含已停用）并逐个拷贝，不分页。 */
  public List<AccessRole> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 实体转领域对象：字段同名同类型，全部由 CloneFactory 拷贝（含标识），没有关联或枚举需要转换。 */
  private AccessRole model(Role e) {
    AccessRole v = CloneFactory.copy(new AccessRole(), e);
    return v;
  }

  /**
   * 标识为空时新建实体，否则读出已有实体（不存在抛 {@code ROLE_NOT_FOUND}），再由 CloneFactory 覆盖，
   * 主键不参与拷贝；{@code saveAndFlush} 让编码唯一约束冲突当场暴露并翻译为 {@code DATA_CONFLICT}，
   * 当前事务随之标记为回滚。
   */
  public AccessRole save(AccessRole v) {
    Role e =
        v.getId() == null
            ? new Role()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "ROLE_NOT_FOUND", "角色不存在"));
    CloneFactory.copy(e, v);
    try {
      return model(data.saveAndFlush(e));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("DATA_CONFLICT", "数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /**
   * {@code deleteById} 先查后删，记录不存在时静默返回；不立即刷新，继承边、用户分配或权限授权造成的外键冲突
   * 要到后续刷新（通常是事务提交）时才抛出，不经本适配器翻译。
   */
  public void delete(Long id) {
    data.deleteById(id);
  }

  /** 所有角色按稳定顺序加锁，复用原有角色图事务保护。 */
  public void lockGraph() {
    data.lockHierarchyRoles();
  }
}
