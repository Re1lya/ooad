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

  /** 根据标识读取领域快照。 */
  public Optional<DepartmentNode> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按稳定顺序读取，供管理与导入合并。 */
  public List<DepartmentNode> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 同类型字段交由 CloneFactory，关联及枚举显式转换。 */
  private DepartmentNode model(Department e) {
    DepartmentNode v = CloneFactory.copy(new DepartmentNode(), e);
    v.setParentId(e.getParent() == null ? null : e.getParent().getId());
    return v;
  }

  /** 身份不由调用方替换；当前事务捕获唯一约束并翻译为业务冲突。 */
  public DepartmentNode save(DepartmentNode v) {
    Department e =
        v.getId() == null
            ? new Department()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "对象不存在"));
    CloneFactory.copy(e, v);
    e.setParent(v.getParentId() == null ? null : data.getReferenceById(v.getParentId()));
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

  /** 组织树引用检查。 */
  public boolean hasChildren(Long id) {
    return data.existsByParent_Id(id);
  }
}
