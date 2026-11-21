package com.cdwy.permission.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.permission.Permission;
import com.cdwy.permission.repository.permission.PermissionRepository;
import com.cdwy.permission.service.ddd.domain.model.FrontendPermission;
import com.cdwy.permission.service.ddd.domain.repository.PermissionCatalog;
import com.cdwy.permission.service.port.error.ConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** PermissionCatalog 的 JPA 适配器：实体不离开基础设施边界。 */
@Repository
@RequiredArgsConstructor
public class JpaPermissionCatalogAdapter implements PermissionCatalog {
  private final PermissionRepository data;
  private final com.cdwy.permission.repository.system.BusinessSystemRepository systems;

  /** 按主键读取并拷贝为领域对象，不加锁；不存在时返回空 Optional。 */
  public Optional<FrontendPermission> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按主键升序读取所有应用的全部前端权限（含已停用）并逐个拷贝，不分页。 */
  public List<FrontendPermission> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 实体转领域对象：同名同类型字段由 CloneFactory 拷贝，类型枚举转为名称字符串，所属应用和上级菜单只保留标识。 */
  private FrontendPermission model(Permission e) {
    FrontendPermission v = CloneFactory.copy(new FrontendPermission(), e);
    v.setKind(e.getType().name());
    v.setSystemId(e.getSystem().getId());
    v.setParentId(e.getParent() == null ? null : e.getParent().getId());
    return v;
  }

  /**
   * 标识为空时新建实体，否则读出已有实体（不存在抛 {@code PERMISSION_NOT_FOUND}），再由 CloneFactory 覆盖，
   * 主键不参与拷贝；类型按枚举名转换，应用和上级只取引用、不额外查询。{@code saveAndFlush} 让编码唯一约束
   * 和外键冲突当场暴露并翻译为 {@code DATA_CONFLICT}，当前事务随之标记为回滚。
   */
  public FrontendPermission save(FrontendPermission v) {
    Permission e =
        v.getId() == null
            ? new Permission()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "PERMISSION_NOT_FOUND", "前端权限不存在"));
    CloneFactory.copy(e, v);
    e.setType(com.cdwy.permission.service.port.data.PermissionType.valueOf(v.getKind()));
    e.setSystem(systems.getReferenceById(v.getSystemId()));
    e.setParent(v.getParentId() == null ? null : data.getReferenceById(v.getParentId()));
    try {
      return model(data.saveAndFlush(e));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("DATA_CONFLICT", "数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /**
   * {@code deleteById} 先查后删，记录不存在时静默返回；不立即刷新，子权限、角色授权或 API 绑定造成的外键冲突
   * 要到后续刷新（通常是事务提交）时才抛出，不经本适配器翻译。
   */
  public void delete(Long id) {
    data.deleteById(id);
  }

  /** 子权限反向引用检查。 */
  public boolean hasChildren(Long id) {
    return data.existsByParent_Id(id);
  }

  /** 应用删除的反向引用检查。 */
  public boolean inSystem(Long id) {
    return data.existsBySystem_Id(id);
  }
}
