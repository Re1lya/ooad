package com.cdwy.permission.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.organization.Department;
import com.cdwy.permission.repository.organization.DepartmentRepository;
import com.cdwy.permission.service.ddd.domain.model.DepartmentNode;
import com.cdwy.permission.service.ddd.domain.repository.DepartmentDirectory;
import com.cdwy.permission.service.port.error.ConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** DepartmentDirectory 的 JPA 适配器：实体不离开基础设施边界。 */
@Repository
@RequiredArgsConstructor
public class JpaDepartmentDirectoryAdapter implements DepartmentDirectory {
  private final DepartmentRepository data;

  /** 按主键读取并拷贝为领域对象，不加锁；不存在时返回空 Optional。 */
  public Optional<DepartmentNode> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按主键升序读取全部部门并逐个拷贝，不分页；整树防环和唯一性校验都需要完整的部门树。 */
  public List<DepartmentNode> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 实体转领域对象：标识、名称和编码由 CloneFactory 拷贝，上级部门只保留标识，根部门为 {@code null}。 */
  private DepartmentNode model(Department e) {
    DepartmentNode v = CloneFactory.copy(new DepartmentNode(), e);
    v.setParentId(e.getParent() == null ? null : e.getParent().getId());
    return v;
  }

  /**
   * 标识为空时新建实体，否则读出已有实体（不存在抛 {@code DEPARTMENT_NOT_FOUND}），再由 CloneFactory
   * 覆盖名称和编码，主键不参与拷贝；上级只取引用、不额外查询。{@code saveAndFlush} 让同级名称、编码唯一约束
   * 和上级外键冲突当场暴露并翻译为 {@code DATA_CONFLICT}，当前事务随之标记为回滚。
   */
  public DepartmentNode save(DepartmentNode v) {
    Department e =
        v.getId() == null
            ? new Department()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "DEPARTMENT_NOT_FOUND", "部门不存在"));
    CloneFactory.copy(e, v);
    e.setParent(v.getParentId() == null ? null : data.getReferenceById(v.getParentId()));
    try {
      return model(data.saveAndFlush(e));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("DATA_CONFLICT", "数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /**
   * {@code deleteById} 先查后删，记录不存在时静默返回；不立即刷新，子部门或账号引用造成的外键冲突
   * 要到后续刷新（通常是事务提交）时才抛出，不经本适配器翻译。
   */
  public void delete(Long id) {
    data.deleteById(id);
  }

  /** 组织树引用检查。 */
  public boolean hasChildren(Long id) {
    return data.existsByParent_Id(id);
  }

  /** 数据库分页，按编码排序。 */
  public com.cdwy.permission.service.port.data.UserData.Page<DepartmentNode> level(
      Long parentId, String keyword, int page, int pageSize) {
    org.springframework.data.domain.Page<Department> rows =
        data.level(
            parentId,
            com.cdwy.permission.repository.LikePattern.of(keyword),
            org.springframework.data.domain.PageRequest.of(
                page - 1, pageSize, org.springframework.data.domain.Sort.by("code", "id")));
    return new com.cdwy.permission.service.port.data.UserData.Page<>(
        page, pageSize, rows.getTotalElements(), rows.map(this::model).getContent());
  }

  /** 一次查询判断一页部门是否有子部门。 */
  public java.util.Set<Long> withChildren(java.util.Collection<Long> ids) {
    return ids.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>(data.parentsAmong(ids));
  }
}
