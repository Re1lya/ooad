package com.cdwy.permission.controller.resource;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.dto.common.ActivityDTO;
import com.cdwy.permission.dto.resource.ApiResourceCreateDTO;
import com.cdwy.permission.dto.resource.ApiResourceUpdateDTO;
import com.cdwy.permission.service.port.resource.ResourceUseCase;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API 资源登记；资源数量有限，列表一次返回全部。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/resources")
public class ApiResourceController {
  private final ResourceUseCase service;
  private final ApiResourceVoMapper mapper;

  /** 全部 API 资源。 */
  @GetMapping
  public List<ApiResourceVO> list() {
    return service.list().stream().map(mapper::toVO).toList();
  }

  /** 登记 API 资源。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResourceVO create(@Valid @RequestBody ApiResourceCreateDTO input) {
    return mapper.toVO(service.create(mapper.toCommand(input)));
  }

  /** 修改名称。 */
  @PutMapping("/{id}")
  public ApiResourceVO update(@PathVariable Long id, @Valid @RequestBody ApiResourceUpdateDTO input) {
    return mapper.toVO(service.update(id, mapper.toCommand(input)));
  }

  /** 使资源生效或失效；失效后该接口不可调用。 */
  @PutMapping("/{id}/status")
  public ApiResourceVO status(@PathVariable Long id, @Valid @RequestBody ActivityDTO input) {
    return mapper.toVO(service.status(id, input.active()));
  }

  /** 删除未绑定菜单或按钮的资源。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
