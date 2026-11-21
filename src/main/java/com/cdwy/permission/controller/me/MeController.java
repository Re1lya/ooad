package com.cdwy.permission.controller.me;

import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.controller.common.HttpMapping;
import com.cdwy.permission.dto.auth.AuthDtos;
import com.cdwy.permission.dto.permission.PermissionDtos;
import com.cdwy.permission.dto.user.UserView;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import com.cdwy.permission.service.port.user.UserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class MeController {
  private final UserUseCase users;
  private final AuthenticationUseCase auth;
  private final AuthorizationUseCase authorization;

  public MeController(
      UserUseCase users, AuthenticationUseCase auth, AuthorizationUseCase authorization) {
    this.users = users;
    this.auth = auth;
    this.authorization = authorization;
  }

  @GetMapping
  public UserView me(HttpServletRequest request) {
    return HttpMapping.view(users.get(CurrentUser.from(request).id()));
  }

  @GetMapping("/permissions")
  public List<PermissionDtos.View> permissions(HttpServletRequest request) {
    return authorization.effectiveViews(CurrentUser.from(request).id()).stream()
        .map(HttpMapping::view)
        .toList();
  }

  /** 本人的有效菜单树；带 system 参数时只返回该应用的菜单，控制台导航用 system=rbac。 */
  @GetMapping("/menu")
  public List<PermissionDtos.Tree> menu(
      @RequestParam(required = false) String system, HttpServletRequest request) {
    Long id = CurrentUser.from(request).id();
    return (system == null ? authorization.menu(id) : authorization.menu(id, system))
        .stream()
            .map(HttpMapping::view)
            .toList();
  }

  @PutMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(
      HttpServletRequest request, @Valid @RequestBody AuthDtos.PasswordChange input) {
    auth.changePassword(CurrentUser.from(request).id(), HttpMapping.command(input));
  }
}
