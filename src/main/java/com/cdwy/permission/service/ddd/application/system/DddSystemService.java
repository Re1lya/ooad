package com.cdwy.permission.service.ddd.application.system;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.system.SystemUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddSystemService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddSystemService implements SystemUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final SystemCatalog systems;
  private final PermissionCatalog permissions;
  private final ResourceCatalog resources;

  /** 读取应用。 */
  private ManagedSystem require(Long id) {
    return systems.find(id).orElseThrow(() -> new ResourceNotFoundException("对象不存在"));
  }

  /** 明确构造不可变结果。 */
  private SystemData.View view(ManagedSystem n) {
    return new SystemData.View(n.getId(), n.getCode(), n.getName(), n.getBaseUrl(), n.getEnabled());
  }

  /** 应用目录。 */
  public List<SystemData.View> list() {
    return systems.all().stream().map(this::view).toList();
  }

  /** 新增应用且保持编码唯一。 */
  @Transactional
  public SystemData.View create(SystemData.Create input) {
    if (systems.all().stream().anyMatch(n -> n.getCode().equals(input.code())))
      throw new ConflictException("编码已存在");
    ManagedSystem n = new ManagedSystem();
    n.setCode(input.code());
    n.rename(input.name(), input.baseUrl());
    return view(systems.save(n));
  }

  /** 修改应用描述。 */
  @Transactional
  public SystemData.View update(Long id, SystemData.Update input) {
    ManagedSystem n = require(id);
    n.rename(input.name(), input.baseUrl());
    return view(systems.save(n));
  }

  /** 应用开关实时参与鉴权。 */
  @Transactional
  public SystemData.View status(Long id, boolean enabled) {
    ManagedSystem n = require(id);
    n.changeStatus(enabled);
    return view(systems.save(n));
  }

  /** 有资源时不能删除应用。 */
  @Transactional
  public void delete(Long id) {
    ManagedSystem n = require(id);
    n.assertDeletable(permissions.inSystem(id) || resources.inSystem(id));
    systems.delete(id);
  }
}
