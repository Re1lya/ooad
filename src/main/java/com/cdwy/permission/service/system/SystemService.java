package com.cdwy.permission.service.system;

import com.cdwy.permission.entity.system.BusinessSystem;
import com.cdwy.permission.repository.system.BusinessSystemRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.system.SystemUseCase;
import java.util.List;
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
public class SystemService implements SystemUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final BusinessSystemRepository repository;
  private final com.cdwy.permission.repository.permission.PermissionRepository permissions;
  private final com.cdwy.permission.repository.resource.ApiResourceRepository resources;

  /** 按标识读取应用实体，新建 API 资源与菜单、按钮时也复用；不存在时抛 {@code SYSTEM_NOT_FOUND}（404）。 */
  public BusinessSystem require(Long id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SYSTEM_NOT_FOUND", "应用不存在"));
  }

  /** 实体转换为与持久化无关的结果。 */
  public static SystemData.View view(BusinessSystem value) {
    return new SystemData.View(
        value.getId(), value.getCode(), value.getName(), value.getBaseUrl(), value.getEnabled());
  }

  /** 按标识升序列出全部应用，含已停用的和权限系统自身（rbac）。 */
  public List<SystemData.View> list() {
    return repository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(SystemService::view)
        .toList();
  }

  /**
   * 接入新应用，默认启用；编码全局唯一，已存在时抛 {@code SYSTEM_CODE_EXISTS}（409）。编码就是接入应用调用统一身份接口
   * {@code /api/apps/{systemCode}/auth/...} 时的标识，创建后不可修改。
   */
  @Transactional
  public SystemData.View create(SystemData.Create input) {
    if (repository.existsByCode(input.code())) throw new ConflictException("SYSTEM_CODE_EXISTS", "编码已存在");
    BusinessSystem value = new BusinessSystem();
    value.setCode(input.code());
    value.setName(input.name());
    value.setBaseUrl(input.baseUrl());
    return view(repository.save(value));
  }

  /** 修改名称与访问地址，编码不变；修改由 JPA 脏检查在事务提交时写回。 */
  @Transactional
  public SystemData.View update(Long id, SystemData.Update input) {
    BusinessSystem value = require(id);
    value.setName(input.name());
    value.setBaseUrl(input.baseUrl());
    return view(value);
  }

  /**
   * 启停应用，已有授权关系保留。停用后该应用的菜单、按钮与 API 资源在鉴权和统一身份接口中都实时视为无效，用户在该应用内没有任何权限。
   */
  @Transactional
  public SystemData.View status(Long id, boolean enabled) {
    BusinessSystem value = require(id);
    value.setEnabled(enabled);
    return view(value);
  }

  /** 删除应用；仍有菜单、按钮或 API 资源时抛 {@code SYSTEM_IN_USE}（409）。 */
  @Transactional
  public void delete(Long id) {
    BusinessSystem value = require(id);
    if (permissions.existsBySystem_Id(id) || resources.existsBySystem_Id(id))
      throw new ConflictException("SYSTEM_IN_USE", "仍有授权或资源引用，请先解除关联");
    repository.delete(value);
  }
}
