package com.cdwy.permission.controller.organization;

import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.organization.DepartmentDtos;
import com.cdwy.permission.service.port.organization.DepartmentUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
  private final DepartmentUseCase service;

  public DepartmentController(DepartmentUseCase service) {
    this.service = service;
  }

  @GetMapping
  public List<DepartmentDtos.View> list() {
    return service.list().stream().map(HttpMapping::view).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DepartmentDtos.View create(@Valid @RequestBody DepartmentDtos.Input input) {
    return HttpMapping.view(service.create(HttpMapping.command(input)));
  }

  @PutMapping("/{id}")
  public DepartmentDtos.View update(
      @PathVariable Long id, @Valid @RequestBody DepartmentDtos.Input input) {
    return HttpMapping.view(service.update(id, HttpMapping.command(input)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
