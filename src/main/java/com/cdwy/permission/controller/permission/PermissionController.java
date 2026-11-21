package com.cdwy.permission.controller.permission;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.controller.resource.ApiResourceVoMapper;
import com.cdwy.permission.dto.common.ActivityDTO;
import com.cdwy.permission.dto.permission.PermissionCreateDTO;
import com.cdwy.permission.dto.permission.PermissionUpdateDTO;
import com.cdwy.permission.service.port.permission.PermissionUseCase;
import com.cdwy.permission.vo.permission.PermissionTreeVO;
import com.cdwy.permission.vo.permission.PermissionVO;
import com.cdwy.permission.vo.resource.ApiResourceVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 菜单与按钮管理，以及菜单、按钮与 API 资源的绑定。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/permissions")
public class PermissionController {
  private final PermissionUseCase service;
  private final PermissionVoMapper mapper;
  private final ApiResourceVoMapper resourceMapper;

  /** 菜单与按钮列表；带 systemId 时只返回该应用的。 */
  @GetMapping
  public List<PermissionVO> list(@RequestParam(required = false) Long systemId) {
    return service.list(systemId).stream().map(mapper::toVO).toList();
  }

  /** 菜单树；带 systemId 时只返回该应用的。 */
  @GetMapping("/tree")
  public List<PermissionTreeVO> tree(@RequestParam(required = false) Long systemId) {
    return service.tree(systemId).stream().map(mapper::toVO).toList();
  }

  /** 新建菜单或按钮。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PermissionVO create(@Valid @RequestBody PermissionCreateDTO input) {
    return mapper.toVO(service.create(mapper.toCommand(input)));
  }

  /** 修改名称、类型、菜单地址或上级。 */
  @PutMapping("/{id}")
  public PermissionVO update(@PathVariable Long id, @Valid @RequestBody PermissionUpdateDTO input) {
    return mapper.toVO(service.update(id, mapper.toCommand(input)));
  }

  /** 使菜单或按钮生效或失效；失效后不再计入任何人的权限。 */
  @PutMapping("/{id}/status")
  public PermissionVO status(@PathVariable Long id, @Valid @RequestBody ActivityDTO input) {
    return mapper.toVO(service.status(id, input.active()));
  }

  /** 删除没有下级、授权和 API 绑定的菜单或按钮。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }

  /** 菜单或按钮绑定的 API 资源。 */
  @GetMapping("/{id}/resources")
  public List<ApiResourceVO> resources(@PathVariable Long id) {
    return service.resources(id).stream().map(resourceMapper::toVO).toList();
  }

  /** 绑定同一应用的 API 资源，重复绑定幂等。 */
  @PutMapping("/{id}/resources/{resourceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void bind(@PathVariable Long id, @PathVariable Long resourceId) {
    service.bind(id, resourceId);
  }

  /** 解除绑定，保留 API 资源登记。 */
  @DeleteMapping("/{id}/resources/{resourceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unbind(@PathVariable Long id, @PathVariable Long resourceId) {
    service.unbind(id, resourceId);
  }
}
