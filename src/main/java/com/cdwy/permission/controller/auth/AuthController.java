package com.cdwy.permission.controller.auth;

import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.auth.AuthDtos;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthenticationUseCase service;

  public AuthController(AuthenticationUseCase service) {
    this.service = service;
  }

  @PostMapping("/login")
  public AuthDtos.Token login(@Valid @RequestBody AuthDtos.Login input) {
    return HttpMapping.view(service.login(HttpMapping.command(input)));
  }
}
