package com.cdwy.permission.controller.auth;

import com.cdwy.permission.controller.user.UserVoMapper;
import com.cdwy.permission.dto.auth.LoginDTO;
import com.cdwy.permission.dto.auth.PasswordChangeDTO;
import com.cdwy.permission.service.port.data.AuthData;
import com.cdwy.permission.vo.auth.TokenVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 控制台登录与修改密码的 DTO → 命令、结果 → VO 映射。 */
@Component
@RequiredArgsConstructor
public class AuthVoMapper {
  private final UserVoMapper users;

  /** 登录请求转为登录命令。 */
  public AuthData.Login toCommand(LoginDTO dto) {
    return new AuthData.Login(dto.username(), dto.password());
  }

  /** 修改密码请求转为命令。 */
  public AuthData.PasswordChange toCommand(PasswordChangeDTO dto) {
    return new AuthData.PasswordChange(dto.currentPassword(), dto.newPassword());
  }

  /** 登录结果转为令牌 VO。 */
  public TokenVO toVO(AuthData.Token token) {
    return new TokenVO(
        token.accessToken(), token.tokenType(), token.expiresIn(), users.toVO(token.user()));
  }
}
