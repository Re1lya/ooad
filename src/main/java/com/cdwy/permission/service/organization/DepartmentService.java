package com.cdwy.permission.service.organization;

import com.cdwy.permission.entity.organization.Department;
import com.cdwy.permission.repository.organization.DepartmentRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.organization.DepartmentUseCase;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化事务脚本：按查询、业务校验、写入和结果转换组织处理步骤。 */
@Service
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

  public DepartmentService(DepartmentRepository departments, UserAccountRepository users) {
    this.departments = departments;
    this.users = users;
  }

  private Department require(Long id) {
    return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("部门不存在"));
  }

  public static DepartmentData.View view(Department d) {
    return new DepartmentData.View(
        d.getId(),
        d.getName(),
        d.getParent() == null ? null : d.getParent().getId(),
        d.getManagerName());
  }

  public List<DepartmentData.View> list() {
    return departments.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(DepartmentService::view)
        .toList();
  }

  private void apply(Department value, DepartmentData.Input input) {
    if (departments.existsByName(input.name()) && !input.name().equals(value.getName()))
      throw new ConflictException("部门名称已存在");
    Department parent = input.parentId() == null ? null : require(input.parentId());
    for (Department p = parent; p != null; p = p.getParent())
      if (Objects.equals(p.getId(), value.getId())) throw new IllegalArgumentException("部门树不能形成循环");
    value.setName(input.name());
    value.setParent(parent);
  }

  @Transactional
  public DepartmentData.View create(DepartmentData.Input input) {
    Department value = new Department();
    apply(value, input);
    return view(departments.save(value));
  }

  @Transactional
  public DepartmentData.View update(Long id, DepartmentData.Input input) {
    Department value = require(id);
    apply(value, input);
    return view(value);
  }

  @Transactional
  public void delete(Long id) {
    Department value = require(id);
    if (departments.existsByParent_Id(id) || users.existsByDepartment_Id(id))
      throw new ConflictException("部门仍有子部门或员工");
    departments.delete(value);
  }
}
