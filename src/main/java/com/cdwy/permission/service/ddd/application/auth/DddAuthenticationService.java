package com.cdwy.permission.service.ddd.application.auth;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddAuthenticationService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddAuthenticationService implements AuthenticationUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final AccountRepository accounts;
  private final SystemCatalog systems;
  private final com.cdwy.permission.service.port.auth.CredentialGateway credentials;

  /** 控制台登录：令牌目标系统为权限系统自身。 */
  public AuthData.Token login(AuthData.Login input) {
    return token(
        authenticate(input.username(), input.password()),
        java.util.List.of(com.cdwy.permission.service.auth.JwtTokenService.CONSOLE_AUDIENCE));
  }

  /** 接入应用登录：由 {@link ManagedSystem#audience} 校验目标系统，账号对象校验可用状态。 */
  public AuthData.Token loginTo(AuthData.AppLogin input) {
    java.util.List<String> audience =
        ManagedSystem.audience(input.systems(), input.target(), systems.all());
    return token(authenticate(input.username(), input.password()), audience);
  }

  /** 登录同时检查可用状态，失败不泄露账号是否存在。 */
  private Account authenticate(String username, String password) {
    return accounts
        .username(username)
        .filter(a -> a.usable(credentials.now()))
        .filter(a -> credentials.matches(password, a.getPasswordHash()))
        .orElseThrow(() -> new AuthenticationFailedException("账号或密码错误，或账号已失效"));
  }

  /** 按账号当前凭证版本与目标系统签发令牌，并把账号按同名字段复制为登录结果中的用户信息（不含密码散列与凭证版本）。 */
  private AuthData.Token token(Account account, java.util.Collection<String> audience) {
    UserResult result = cn.edu.xmu.clonefactory.util.CloneFactory.copy(new UserResult(), account);
    result.setKind(account.getKind());
    return new AuthData.Token(
        credentials.issue(account.getId(), account.getCredentialVersion(), audience),
        "Bearer",
        credentials.expiresIn(),
        result);
  }

  /** 每次请求重新读取账号状态和凭证版本，并核对令牌目标系统。 */
  public AuthData.Identity resolve(String header, String audience) {
    com.cdwy.permission.service.port.auth.CredentialGateway.Claims claims =
        credentials.verify(header);
    if (!claims.audience().contains(audience))
      throw new AuthenticationFailedException("令牌不适用于当前系统，请重新登录");
    Account a =
        accounts
            .find(claims.id())
            .filter(n -> n.usable(credentials.now()))
            .filter(n -> n.getCredentialVersion() == claims.version())
            .orElseThrow(() -> new AuthenticationFailedException("账号或凭证已失效，请重新登录"));
    return new AuthData.Identity(a.getId(), a.getAdministrator());
  }

  /** 退出登录由账号对象递增凭证版本，已签发的令牌随即失效。 */
  @Transactional
  public void logout(Long id) {
    Account a = accounts.lock(id).orElseThrow(() -> new AuthenticationFailedException("账号不存在"));
    a.logout();
    accounts.save(a);
  }

  /** 原密码验证及新密码保存处于同一账号写锁事务。 */
  @Transactional
  public void changePassword(Long id, AuthData.PasswordChange input) {
    Account a = accounts.lock(id).orElseThrow(() -> new AuthenticationFailedException("账号不存在"));
    if (!credentials.matches(input.currentPassword(), a.getPasswordHash()))
      throw new AuthenticationFailedException("原密码错误");
    credentials.validatePassword(input.newPassword());
    a.resetPassword(credentials.encode(input.newPassword()));
    accounts.save(a);
  }
}
