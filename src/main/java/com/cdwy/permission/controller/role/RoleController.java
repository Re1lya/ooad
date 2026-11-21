package com.cdwy.permission.controller.role;

import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.common.CommonDtos.Status;
import com.cdwy.permission.dto.role.RoleDtos;
import com.cdwy.permission.service.port.role.RoleUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@com.cdwy.permission.config.BusinessAccess(com.cdwy.permission.config.BusinessAccess.CONSOLE)
@RequestMapping("/api/roles")
public class RoleController {
  private final RoleUseCase service;

  public RoleController(RoleUseCase service) {
    this.service = service;
  }

  @GetMapping
  public List<RoleDtos.View> list() {
    return service.list().stream().map(HttpMapping::view).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoleDtos.View create(@Valid @RequestBody RoleDtos.Create input) {
    return HttpMapping.view(service.create(HttpMapping.command(input)));
  }

  @PutMapping("/{id}")
  public RoleDtos.View update(@PathVariable Long id, @Valid @RequestBody RoleDtos.Update input) {
    return HttpMapping.view(service.update(id, HttpMapping.command(input)));
  }

  @PutMapping("/{id}/status")
  public RoleDtos.View status(@PathVariable Long id, @Valid @RequestBody Status input) {
    return HttpMapping.view(service.status(id, input.enabled()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
