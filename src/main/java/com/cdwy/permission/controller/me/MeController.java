package com.cdwy.permission.controller.me;

import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.controller.auth.AuthVoMapper;
import com.cdwy.permission.controller.permission.PermissionVoMapper;
import com.cdwy.permission.controller.user.UserVoMapper;
import com.cdwy.permission.dto.auth.PasswordChangeDTO;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import com.cdwy.permission.service.port.user.UserUseCase;
import com.cdwy.permission.vo.permission.PermissionTreeVO;
import com.cdwy.permission.vo.permission.PermissionVO;
import com.cdwy.permission.vo.user.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户的自助接口：本人资料、有效权限、菜单与修改密码，只需登录即可访问。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class MeController {
  private final UserUseCase users;
  private final AuthenticationUseCase auth;
  private final AuthorizationUseCase authorization;
  private final UserVoMapper userMapper;
  private final PermissionVoMapper permissionMapper;
  private final AuthVoMapper authMapper;

  /** 本人资料。 */
  @GetMapping
  public UserVO me(HttpServletRequest request) {
    return userMapper.toVO(users.get(CurrentUser.from(request).id()));
  }

  /** 本人的全部有效菜单与按钮（含继承）。 */
  @GetMapping("/permissions")
  public List<PermissionVO> permissions(HttpServletRequest request) {
    return authorization.effectiveViews(CurrentUser.from(request).id()).stream()
        .map(permissionMapper::toVO)
        .toList();
  }

  /** 本人的有效菜单树；带 system 参数时只返回该应用的菜单，控制台导航用 system=rbac。 */
  @GetMapping("/menu")
  public List<PermissionTreeVO> menu(
      @RequestParam(required = false) @Size(max = 64, message = "最长 64 个字符") String system,
      HttpServletRequest request) {
    Long id = CurrentUser.from(request).id();
    return (system == null ? authorization.menu(id) : authorization.menu(id, system))
        .stream()
            .map(permissionMapper::toVO)
            .toList();
  }

  /** 修改本人密码。 */
  @PutMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(HttpServletRequest request, @Valid @RequestBody PasswordChangeDTO input) {
    auth.changePassword(CurrentUser.from(request).id(), authMapper.toCommand(input));
  }
}
