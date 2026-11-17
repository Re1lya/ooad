package com.cdwy.permission.controller.permission;

import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.common.CommonDtos.Activity;
import com.cdwy.permission.dto.permission.PermissionDtos;
import com.cdwy.permission.dto.resource.ResourceDtos;
import com.cdwy.permission.service.port.permission.PermissionUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {
  private final PermissionUseCase service;

  public PermissionController(PermissionUseCase service) {
    this.service = service;
  }

  @GetMapping
  public List<PermissionDtos.View> list(@RequestParam(required = false) Long systemId) {
    return service.list(systemId).stream().map(HttpMapping::view).toList();
  }

  @GetMapping("/tree")
  public List<PermissionDtos.Tree> tree(@RequestParam(required = false) Long systemId) {
    return service.tree(systemId).stream().map(HttpMapping::view).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PermissionDtos.View create(@Valid @RequestBody PermissionDtos.Input input) {
    return HttpMapping.view(service.create(HttpMapping.command(input)));
  }

  @PutMapping("/{id}")
  public PermissionDtos.View update(
      @PathVariable Long id, @Valid @RequestBody PermissionDtos.Update input) {
    return HttpMapping.view(service.update(id, HttpMapping.command(input)));
  }

  @PutMapping("/{id}/status")
  public PermissionDtos.View status(@PathVariable Long id, @Valid @RequestBody Activity input) {
    return HttpMapping.view(service.status(id, input.active()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }

  @GetMapping("/{id}/resources")
  public List<ResourceDtos.View> resources(@PathVariable Long id) {
    return service.resources(id).stream().map(HttpMapping::view).toList();
  }

  @PutMapping("/{id}/resources/{resourceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void bind(@PathVariable Long id, @PathVariable Long resourceId) {
    service.bind(id, resourceId);
  }

  @DeleteMapping("/{id}/resources/{resourceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unbind(@PathVariable Long id, @PathVariable Long resourceId) {
    service.unbind(id, resourceId);
  }
}
