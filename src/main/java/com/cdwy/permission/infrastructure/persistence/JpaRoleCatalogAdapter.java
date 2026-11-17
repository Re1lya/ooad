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

  /** 根据标识读取领域快照。 */
  public Optional<AccessRole> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按稳定顺序读取，供管理与导入合并。 */
  public List<AccessRole> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 同类型字段交由 CloneFactory，关联及枚举显式转换。 */
  private AccessRole model(Role e) {
    AccessRole v = CloneFactory.copy(new AccessRole(), e);
    return v;
  }

  /** 身份不由调用方替换；当前事务捕获唯一约束并翻译为业务冲突。 */
  public AccessRole save(AccessRole v) {
    Role e =
        v.getId() == null
            ? new Role()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "对象不存在"));
    CloneFactory.copy(e, v);
    try {
      return model(data.saveAndFlush(e));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /** 删除前由领域服务检查业务引用，数据库保留最终约束。 */
  public void delete(Long id) {
    data.deleteById(id);
  }

  /** 所有角色按稳定顺序加锁，复用原有角色图事务保护。 */
  public void lockGraph() {
    data.lockHierarchyRoles();
  }
}
