package com.cdwy.permission.service.ddd.application.organization;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.organization.DepartmentUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddDepartmentService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddDepartmentService implements DepartmentUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final DepartmentDirectory departments;
  private final AccountRepository accounts;

  /** 读取部门。 */
  private DepartmentNode require(Long id) {
    return departments.find(id).orElseThrow(() -> new ResourceNotFoundException("部门不存在"));
  }

  /** 返回无持久化技术的只读视图。 */
  private DepartmentData.View view(DepartmentNode n) {
    return new DepartmentData.View(
        n.getId(), n.getName(), n.getParentId(), n.getManagerName(), n.getCode());
  }

  /** 获取当前部门图。 */
  private Map<Long, DepartmentNode> graph() {
    Map<Long, DepartmentNode> values = new LinkedHashMap<>();
    departments.all().forEach(n -> values.put(n.getId(), n));
    return values;
  }

  /** 领域对象维护父级防环、同级名称唯一与编码唯一。 */
  private void apply(DepartmentNode n, DepartmentData.Input input) {
    Map<Long, DepartmentNode> nodes = graph();
    n.relocate(input.name(), input.parentId(), nodes);
    n.assertUnique(input.code(), nodes.values(), input.parentId(), input.name());
  }

  /** 列出组织目录。 */
  public List<DepartmentData.View> list() {
    return departments.all().stream().map(this::view).toList();
  }

  /** 创建部门不推导经理权限。 */
  @Transactional
  public DepartmentData.View create(DepartmentData.Input input) {
    DepartmentNode n = new DepartmentNode();
    apply(n, input);
    DepartmentNode saved = departments.save(n);
    return view(saved.assignDefaultCode() ? departments.save(saved) : saved);
  }

  /** 移动部门仍须满足整图无环。 */
  @Transactional
  public DepartmentData.View update(Long id, DepartmentData.Input input) {
    DepartmentNode n = require(id);
    apply(n, input);
    return view(departments.save(n));
  }

  /** 子部门与员工引用均会阻止删除。 */
  @Transactional
  public void delete(Long id) {
    DepartmentNode n = require(id);
    n.assertDeletable(departments.hasChildren(id) || accounts.inDepartment(id));
    departments.delete(id);
  }
}
