package com.cdwy.permission.service.organization;

import com.cdwy.permission.entity.organization.Department;
import com.cdwy.permission.repository.organization.DepartmentRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.organization.DepartmentUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化事务脚本：按查询、业务校验、写入和结果转换组织处理步骤。 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@Transactional(readOnly = true)
public class DepartmentService implements DepartmentUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final DepartmentRepository departments;
  private final UserAccountRepository users;

  private Department require(Long id) {
    return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "部门不存在"));
  }

  /** 实体转换为与持久化无关的结果；上级为空表示根部门。 */
  public static DepartmentData.View view(Department d) {
    return new DepartmentData.View(
        d.getId(),
        d.getName(),
        d.getParent() == null ? null : d.getParent().getId(),
        d.getCode());
  }

  /** 按标识升序返回全部部门的扁平列表，由调用方按上级组成树。 */
  public List<DepartmentData.View> list() {
    return departments.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(DepartmentService::view)
        .toList();
  }

  /** 步骤：上级存在且不成环 → 同级名称唯一 → 编码唯一 → 写入。 */
  private void apply(Department value, DepartmentData.Input input) {
    Department parent = input.parentId() == null ? null : require(input.parentId());
    for (Department p = parent; p != null; p = p.getParent())
      if (Objects.equals(p.getId(), value.getId())) throw new InvalidDataException("DEPARTMENT_CYCLE", "部门树不能形成循环");
    if (departments.siblingNamed(input.parentId(), input.name(), value.getId()))
      throw new ConflictException("DEPARTMENT_NAME_EXISTS", "同一上级下已有同名部门");
    if (input.code() != null) {
      String code = input.code();
      departments
          .findByCode(code)
          .filter(other -> !other.getId().equals(value.getId()))
          .ifPresent(
              other -> {
                throw new ConflictException("DEPARTMENT_CODE_EXISTS", "部门编码已存在");
              });
      value.setCode(code);
    }
    value.setName(input.name());
    value.setParent(parent);
  }

  /**
   * 新建部门：上级不存在抛 {@code DEPARTMENT_NOT_FOUND}（404），同一上级下重名抛 {@code DEPARTMENT_NAME_EXISTS}，编码已被占用抛
   * {@code DEPARTMENT_CODE_EXISTS}（均 409）；未填编码时先保存取得标识，再补默认编码（{@code D} 加四位标识）。
   */
  @Transactional
  public DepartmentData.View create(DepartmentData.Input input) {
    Department value = new Department();
    apply(value, input);
    departments.save(value);
    if (value.getCode() == null) value.setCode(DepartmentData.defaultCode(value.getId()));
    return view(value);
  }

  /**
   * 修改名称、上级与编码，校验同新建；新上级是自身或自身的后代时抛 {@code DEPARTMENT_CYCLE}（400）；未填编码时保留原编码。
   * 修改由 JPA 脏检查在事务提交时写回。
   */
  @Transactional
  public DepartmentData.View update(Long id, DepartmentData.Input input) {
    Department value = require(id);
    apply(value, input);
    return view(value);
  }

  /** 删除部门；仍有子部门或员工时抛 {@code DEPARTMENT_IN_USE}（409）。 */
  @Transactional
  public void delete(Long id) {
    Department value = require(id);
    if (departments.existsByParent_Id(id) || users.existsByDepartment_Id(id))
      throw new ConflictException("DEPARTMENT_IN_USE", "部门仍有子部门或员工");
    departments.delete(value);
  }
}
