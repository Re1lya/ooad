package com.cdwy.permission.service.ddd.application.resource;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.resource.ResourceUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddResourceService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddResourceService implements ResourceUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final ResourceCatalog resources;
  private final SystemCatalog systems;
  private final AssignmentRepository assignments;
  private final com.cdwy.permission.service.port.rbac.ResourcePathMatcher matcher;

  /** 读取 API 资源。 */
  private ProtectedResource require(Long id) {
    return resources.find(id).orElseThrow(() -> new ResourceNotFoundException("API_RESOURCE_NOT_FOUND", "API 资源不存在"));
  }

  /** 资源结果。 */
  private ResourceData.View view(ProtectedResource n) {
    return new ResourceData.View(
        n.getId(),
        n.getSystemId(),
        n.getName(),
        n.getMethod(),
        n.getPath(),
        n.getActive(),
        n.getCode(),
        n.getOperationId());
  }

  /** 全部注册资源。 */
  public List<ResourceData.View> list() {
    return resources.all().stream().map(this::view).toList();
  }

  /** 检查资源唯一性和路径规则。 */
  @Transactional
  public ResourceData.View create(ResourceData.Create input) {
    ProtectedResource n = new ProtectedResource();
    n.setSystemId(input.systemId());
    n.setName(input.name());
    n.setMethod(input.method());
    n.setPath(input.path());
    n.setCode(input.code());
    n.setOperationId(input.operationId());
    n.assertPath();
    n.assertCode();
    n.assertOperationId();
    matcher.validate(input.path());
    if (resources.all().stream()
        .anyMatch(
            v ->
                v.getSystemId().equals(input.systemId())
                    && v.getMethod().equals(input.method())
                    && v.getPath().equals(input.path()))) throw new ConflictException("API_RESOURCE_EXISTS", "API 资源已存在");
    if (input.operationId() != null
        && resources.all().stream()
            .anyMatch(
                v ->
                    v.getSystemId().equals(input.systemId())
                        && input.operationId().equals(v.getOperationId())))
      throw new ConflictException("API_OPERATION_EXISTS", "该应用已登记相同 operationId 的接口");
    systems.find(input.systemId()).orElseThrow(() -> new ResourceNotFoundException("SYSTEM_NOT_FOUND", "应用不存在"));
    return view(resources.save(n));
  }

  /** 修改显示名称。 */
  @Transactional
  public ResourceData.View update(Long id, ResourceData.Update input) {
    ProtectedResource n = require(id);
    n.rename(input.name());
    return view(resources.save(n));
  }

  /** 停用仍保留资源标识防止宽泛规则绕过。 */
  @Transactional
  public ResourceData.View status(Long id, boolean active) {
    ProtectedResource n = require(id);
    n.changeStatus(active);
    return view(resources.save(n));
  }

  /** 有绑定时拒绝删除。 */
  @Transactional
  public void delete(Long id) {
    ProtectedResource n = require(id);
    n.assertDeletable(assignments.bindings().stream().anyMatch(e -> e.right().equals(id)));
    resources.delete(id);
  }
}
