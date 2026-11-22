package com.cdwy.permission.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.system.BusinessSystem;
import com.cdwy.permission.repository.system.BusinessSystemRepository;
import com.cdwy.permission.service.ddd.domain.model.ManagedSystem;
import com.cdwy.permission.service.ddd.domain.repository.SystemCatalog;
import com.cdwy.permission.service.port.error.ConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** SystemCatalog 的 JPA 适配器：实体不离开基础设施边界。 */
@Repository
@RequiredArgsConstructor
public class JpaSystemCatalogAdapter implements SystemCatalog {
  private final BusinessSystemRepository data;

  /** 按主键读取并拷贝为领域对象，不加锁；不存在时返回空 Optional。 */
  public Optional<ManagedSystem> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按主键升序读取全部接入应用（含已停用）并逐个拷贝，不分页。 */
  public List<ManagedSystem> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 实体转领域对象：字段同名同类型，全部由 CloneFactory 拷贝（含标识），没有关联或枚举需要转换。 */
  private ManagedSystem model(BusinessSystem e) {
    ManagedSystem v = CloneFactory.copy(new ManagedSystem(), e);
    return v;
  }

  /**
   * 标识为空时新建实体，否则读出已有实体（不存在抛 {@code SYSTEM_NOT_FOUND}），再由 CloneFactory 覆盖，
   * 主键不参与拷贝；{@code saveAndFlush} 让编码唯一约束冲突当场暴露并翻译为 {@code DATA_CONFLICT}，
   * 当前事务随之标记为回滚。
   */
  public ManagedSystem save(ManagedSystem v) {
    BusinessSystem e =
        v.getId() == null
            ? new BusinessSystem()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "SYSTEM_NOT_FOUND", "应用不存在"));
    CloneFactory.copy(e, v);
    try {
      return model(data.saveAndFlush(e));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("DATA_CONFLICT", "数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /**
   * {@code deleteById} 先查后删，记录不存在时静默返回；不立即刷新，仍引用该应用的前端权限或 API 资源造成的外键冲突
   * 要到后续刷新（通常是事务提交）时才抛出，不经本适配器翻译。
   */
  public void delete(Long id) {
    data.deleteById(id);
  }
}
