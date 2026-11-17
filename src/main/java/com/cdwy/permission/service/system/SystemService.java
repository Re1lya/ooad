package com.cdwy.permission.service.system;

import com.cdwy.permission.entity.system.BusinessSystem;
import com.cdwy.permission.repository.system.BusinessSystemRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.system.SystemUseCase;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化事务脚本：按查询、业务校验、写入和结果转换组织处理步骤。 */
@Service
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

  public SystemService(
      BusinessSystemRepository repository,
      com.cdwy.permission.repository.permission.PermissionRepository permissions,
      com.cdwy.permission.repository.resource.ApiResourceRepository resources) {
    this.repository = repository;
    this.permissions = permissions;
    this.resources = resources;
  }

  public BusinessSystem require(Long id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("对象不存在"));
  }

  public static SystemData.View view(BusinessSystem value) {
    return new SystemData.View(
        value.getId(), value.getCode(), value.getName(), value.getBaseUrl(), value.getEnabled());
  }

  public List<SystemData.View> list() {
    return repository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(SystemService::view)
        .toList();
  }

  @Transactional
  public SystemData.View create(SystemData.Create input) {
    if (repository.existsByCode(input.code())) throw new ConflictException("编码已存在");
    BusinessSystem value = new BusinessSystem();
    value.setCode(input.code());
    value.setName(input.name());
    value.setBaseUrl(input.baseUrl());
    return view(repository.save(value));
  }

  @Transactional
  public SystemData.View update(Long id, SystemData.Update input) {
    BusinessSystem value = require(id);
    value.setName(input.name());
    value.setBaseUrl(input.baseUrl());
    return view(value);
  }

  @Transactional
  public SystemData.View status(Long id, boolean enabled) {
    BusinessSystem value = require(id);
    value.setEnabled(enabled);
    return view(value);
  }

  @Transactional
  public void delete(Long id) {
    BusinessSystem value = require(id);
    if (permissions.existsBySystem_Id(id) || resources.existsBySystem_Id(id))
      throw new ConflictException("仍有授权或资源引用，请先解除关联");
    repository.delete(value);
  }
}
