package com.cdwy.permission.controller.auth;

import com.cdwy.permission.dto.auth.LoginDTO;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.vo.auth.TokenVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理控制台登录；签发的令牌只能访问权限系统自身的接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthenticationUseCase service;
  private final AuthVoMapper mapper;

  /** 用登录名与密码登录。 */
  @PostMapping("/login")
  public TokenVO login(@Valid @RequestBody LoginDTO input) {
    return mapper.toVO(service.login(mapper.toCommand(input)));
  }
}
