package com.cdwy.permission.controller.system;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.dto.common.StatusDTO;
import com.cdwy.permission.dto.system.SystemCreateDTO;
import com.cdwy.permission.dto.system.SystemUpdateDTO;
import com.cdwy.permission.service.port.system.SystemUseCase;
import com.cdwy.permission.vo.system.SystemVO;
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

/** 接入应用管理；应用数量很少，列表一次返回全部。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/systems")
public class SystemController {
  private final SystemUseCase service;
  private final SystemVoMapper mapper;

  /** 全部接入应用。 */
  @GetMapping
  public List<SystemVO> list() {
    return service.list().stream().map(mapper::toVO).toList();
  }

  /** 接入应用。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SystemVO create(@Valid @RequestBody SystemCreateDTO input) {
    return mapper.toVO(service.create(mapper.toCommand(input)));
  }

  /** 修改名称与访问地址。 */
  @PutMapping("/{id}")
  public SystemVO update(@PathVariable Long id, @Valid @RequestBody SystemUpdateDTO input) {
    return mapper.toVO(service.update(id, mapper.toCommand(input)));
  }

  /** 启用或停用；停用后其权限不再生效。 */
  @PutMapping("/{id}/status")
  public SystemVO status(@PathVariable Long id, @Valid @RequestBody StatusDTO input) {
    return mapper.toVO(service.status(id, input.enabled()));
  }

  /** 删除没有菜单、按钮和 API 资源的应用。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
