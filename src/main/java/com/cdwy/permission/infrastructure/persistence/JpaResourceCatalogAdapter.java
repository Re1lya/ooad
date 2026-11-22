package com.cdwy.permission.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.resource.ApiResource;
import com.cdwy.permission.repository.resource.ApiResourceRepository;
import com.cdwy.permission.service.ddd.domain.model.ProtectedResource;
import com.cdwy.permission.service.ddd.domain.repository.ResourceCatalog;
import com.cdwy.permission.service.port.error.ConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** ResourceCatalog 的 JPA 适配器：实体不离开基础设施边界。 */
@Repository
@RequiredArgsConstructor
public class JpaResourceCatalogAdapter implements ResourceCatalog {
  private final ApiResourceRepository data;
  private final com.cdwy.permission.repository.system.BusinessSystemRepository systems;

  /** 按主键读取并拷贝为领域对象，不加锁；不存在时返回空 Optional。 */
  public Optional<ProtectedResource> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按主键升序读取所有应用的全部 API 资源（含已停用）并逐个拷贝，不分页。 */
  public List<ProtectedResource> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 实体转领域对象：同名同类型字段由 CloneFactory 拷贝，所属应用只保留标识。 */
  private ProtectedResource model(ApiResource e) {
    ProtectedResource v = CloneFactory.copy(new ProtectedResource(), e);
    v.setSystemId(e.getSystem().getId());
    return v;
  }

  /**
   * 标识为空时新建实体，否则读出已有实体（不存在抛 {@code API_RESOURCE_NOT_FOUND}），再由 CloneFactory 覆盖，
   * 主键不参与拷贝；应用只取引用、不额外查询。{@code saveAndFlush} 让同应用内路由与 operationId 唯一约束
   * 和应用外键冲突当场暴露并翻译为 {@code DATA_CONFLICT}，当前事务随之标记为回滚。
   */
  public ProtectedResource save(ProtectedResource v) {
    ApiResource e =
        v.getId() == null
            ? new ApiResource()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "API_RESOURCE_NOT_FOUND", "API 资源不存在"));
    CloneFactory.copy(e, v);
    e.setSystem(systems.getReferenceById(v.getSystemId()));
    try {
      return model(data.saveAndFlush(e));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("DATA_CONFLICT", "数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /**
   * {@code deleteById} 先查后删，记录不存在时静默返回；不立即刷新，仍存在的 API 绑定造成的外键冲突
   * 要到后续刷新（通常是事务提交）时才抛出，不经本适配器翻译。
   */
  public void delete(Long id) {
    data.deleteById(id);
  }

  /** 应用删除的反向引用检查。 */
  public boolean inSystem(Long id) {
    return data.existsBySystem_Id(id);
  }
}
