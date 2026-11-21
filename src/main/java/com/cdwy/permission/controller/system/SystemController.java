package com.cdwy.permission.controller.system;

import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.common.CommonDtos.Status;
import com.cdwy.permission.dto.system.SystemDtos;
import com.cdwy.permission.service.port.system.SystemUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@com.cdwy.permission.config.BusinessAccess(com.cdwy.permission.service.auth.ConsoleCatalog.SYSTEM)
@RequestMapping("/api/systems")
public class SystemController {
  private final SystemUseCase service;

  public SystemController(SystemUseCase service) {
    this.service = service;
  }

  @GetMapping
  public List<SystemDtos.View> list() {
    return service.list().stream().map(HttpMapping::view).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SystemDtos.View create(@Valid @RequestBody SystemDtos.Create input) {
    return HttpMapping.view(service.create(HttpMapping.command(input)));
  }

  @PutMapping("/{id}")
  public SystemDtos.View update(
      @PathVariable Long id, @Valid @RequestBody SystemDtos.Update input) {
    return HttpMapping.view(service.update(id, HttpMapping.command(input)));
  }

  @PutMapping("/{id}/status")
  public SystemDtos.View status(@PathVariable Long id, @Valid @RequestBody Status input) {
    return HttpMapping.view(service.status(id, input.enabled()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
