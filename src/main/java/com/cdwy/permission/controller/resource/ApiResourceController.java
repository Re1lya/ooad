package com.cdwy.permission.controller.resource;

import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.common.CommonDtos.Activity;
import com.cdwy.permission.dto.resource.ResourceDtos;
import com.cdwy.permission.service.port.resource.ResourceUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@com.cdwy.permission.config.BusinessAccess(com.cdwy.permission.config.BusinessAccess.CONSOLE)
@RequestMapping("/api/resources")
public class ApiResourceController {
  private final ResourceUseCase service;

  public ApiResourceController(ResourceUseCase service) {
    this.service = service;
  }

  @GetMapping
  public List<ResourceDtos.View> list() {
    return service.list().stream().map(HttpMapping::view).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResourceDtos.View create(@Valid @RequestBody ResourceDtos.Create input) {
    return HttpMapping.view(service.create(HttpMapping.command(input)));
  }

  @PutMapping("/{id}")
  public ResourceDtos.View update(
      @PathVariable Long id, @Valid @RequestBody ResourceDtos.Update input) {
    return HttpMapping.view(service.update(id, HttpMapping.command(input)));
  }

  @PutMapping("/{id}/status")
  public ResourceDtos.View status(@PathVariable Long id, @Valid @RequestBody Activity input) {
    return HttpMapping.view(service.status(id, input.active()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
