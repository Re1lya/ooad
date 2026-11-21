package com.cdwy.permission.service.resource;

import com.cdwy.permission.entity.resource.ApiResource;
import com.cdwy.permission.repository.rbac.PermissionApiRepository;
import com.cdwy.permission.repository.resource.ApiResourceRepository;
import com.cdwy.permission.service.auth.ConsoleCatalog;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.resource.ResourceUseCase;
import com.cdwy.permission.service.system.SystemService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.PathPatternParser;

/** 结构化事务脚本：按查询、业务校验、写入和结果转换组织处理步骤。 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@Transactional(readOnly = true)
public class ApiResourceService implements ResourceUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final ApiResourceRepository resources;
  private final PermissionApiRepository bindings;
  private final SystemService systems;

  /** 按标识读取 API 资源实体，前端权限的绑定用例也复用；不存在时抛 {@code API_RESOURCE_NOT_FOUND}（404）。 */
  public ApiResource require(Long id) {
    return resources.findById(id).orElseThrow(() -> new ResourceNotFoundException("API_RESOURCE_NOT_FOUND", "API 资源不存在"));
  }

  /** 实体转换为与持久化无关的结果；列出菜单或按钮绑定的接口时也复用。 */
  public static ResourceData.View view(ApiResource r) {
    return new ResourceData.View(
        r.getId(),
        r.getSystem().getId(),
        r.getName(),
        r.getMethod(),
        r.getPath(),
        r.getActive(),
        r.getCode(),
        r.getOperationId());
  }

  /** 按标识升序列出所有应用的 API 资源，含已停用的。 */
  public List<ResourceData.View> list() {
    return resources.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(ApiResourceService::view)
        .toList();
  }

  /**
   * 登记 API 资源，新资源默认启用。路径须能按 Spring {@code PathPattern} 解析，否则 HTTP 400；同一应用内“方法 + 路径”已登记抛
   * {@code API_RESOURCE_EXISTS}，operationId 已登记抛 {@code API_OPERATION_EXISTS}（均 409）；应用不存在抛
   * {@code SYSTEM_NOT_FOUND}（404）。
   */
  @Transactional
  public ResourceData.View create(ResourceData.Create input) {
    PathPatternParser.defaultInstance.parse(input.path());
    if (resources.existsBySystem_IdAndMethodAndPath(input.systemId(), input.method(), input.path()))
      throw new ConflictException("API_RESOURCE_EXISTS", "API 资源已存在");
    if (input.operationId() != null
        && resources.existsBySystem_IdAndOperationId(input.systemId(), input.operationId()))
      throw new ConflictException("API_OPERATION_EXISTS", "该应用已登记相同 operationId 的接口");
    ApiResource r = new ApiResource();
    r.setSystem(systems.require(input.systemId()));
    r.setMethod(input.method());
    r.setPath(input.path());
    r.setName(input.name());
    r.setCode(input.code());
    r.setOperationId(input.operationId());
    return view(resources.save(r));
  }

  /** 只改显示名称：方法、路径、权限码和 operationId 登记后不可改；修改由 JPA 脏检查在事务提交时写回。 */
  @Transactional
  public ResourceData.View update(Long id, ResourceData.Update input) {
    ApiResource value = require(id);
    value.setName(input.name());
    return view(value);
  }

  /**
   * 启停资源，绑定关系保留。停用后接口鉴权仍优先匹配到它并直接拒绝，不会退回到更宽泛的资源；接入应用查询时它既不提供权限码，也判为不可调用。
   */
  @Transactional
  public ResourceData.View status(Long id, boolean active) {
    ApiResource value = require(id);
    if (!active) protectBuiltIn(value);
    value.setActive(active);
    return view(value);
  }

  /** 删除资源；仍绑定任何菜单或按钮时抛 {@code API_RESOURCE_IN_USE}（409），须先解绑。 */
  @Transactional
  public void delete(Long id) {
    ApiResource value = require(id);
    protectBuiltIn(value);
    if (bindings.existsByResource_Id(id)) throw new ConflictException("API_RESOURCE_IN_USE", "API 资源仍绑定前端权限");
    resources.delete(value);
  }

  /** 控制台的内置管理接口：停用或删除后所有人（包括管理员）都调用不了，抛 {@code CONSOLE_BUILT_IN}（409）。 */
  private static void protectBuiltIn(ApiResource value) {
    if (ConsoleCatalog.isApi(value.getSystem().getCode(), value.getMethod(), value.getPath()))
      throw new ConflictException("CONSOLE_BUILT_IN", "权限管理系统的内置管理接口不能停用或删除");
  }
}
