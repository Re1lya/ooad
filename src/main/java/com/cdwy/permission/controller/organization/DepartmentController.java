package com.cdwy.permission.controller.organization;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.dto.organization.DepartmentDTO;
import com.cdwy.permission.service.port.organization.DepartmentUseCase;
import com.cdwy.permission.vo.organization.DepartmentVO;
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

/** 部门管理；部门数量有限，列表一次返回全部，供控制台组成部门树。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/departments")
public class DepartmentController {
  private final DepartmentUseCase service;
  private final DepartmentVoMapper mapper;

  /** 全部部门。 */
  @GetMapping
  public List<DepartmentVO> list() {
    return service.list().stream().map(mapper::toVO).toList();
  }

  /** 新建部门。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DepartmentVO create(@Valid @RequestBody DepartmentDTO input) {
    return mapper.toVO(service.create(mapper.toCommand(input)));
  }

  /** 修改部门名称、上级或编码。 */
  @PutMapping("/{id}")
  public DepartmentVO update(@PathVariable Long id, @Valid @RequestBody DepartmentDTO input) {
    return mapper.toVO(service.update(id, mapper.toCommand(input)));
  }

  /** 删除没有下级部门和员工的部门。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
