package com.cdwy.permission.controller.role;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.dto.common.StatusDTO;
import com.cdwy.permission.dto.role.RoleCreateDTO;
import com.cdwy.permission.dto.role.RoleUpdateDTO;
import com.cdwy.permission.service.port.role.RoleUseCase;
import com.cdwy.permission.vo.role.RoleVO;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 角色管理；角色数量有限，列表一次返回全部。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/roles")
public class RoleController {
  private final RoleUseCase service;
  private final RoleVoMapper mapper;

  /** 全部角色。 */
  @GetMapping
  public List<RoleVO> list() {
    return service.list().stream().map(mapper::toVO).toList();
  }

  /** 新建角色。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoleVO create(@Valid @RequestBody RoleCreateDTO input) {
    return mapper.toVO(service.create(mapper.toCommand(input)));
  }

  /** 修改名称与说明。 */
  @PutMapping("/{id}")
  public RoleVO update(@PathVariable Long id, @Valid @RequestBody RoleUpdateDTO input) {
    return mapper.toVO(service.update(id, mapper.toCommand(input)));
  }

  /** 启用或停用；停用的角色阻断经过它的继承路径。 */
  @PutMapping("/{id}/status")
  public RoleVO status(@PathVariable Long id, @Valid @RequestBody StatusDTO input) {
    return mapper.toVO(service.status(id, input.enabled()));
  }

  /** 删除没有授权、继承和用户的角色。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
