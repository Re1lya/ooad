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
  private final com.cdwy.permission.service.port.auth.CredentialGateway credentials;

  /** 登录同时检查可用状态，失败不泄露账号是否存在。 */
  public AuthData.Token login(AuthData.Login input) {
    Account account =
        accounts
            .username(input.username())
            .filter(a -> a.usable(credentials.now()))
            .filter(a -> credentials.matches(input.password(), a.getPasswordHash()))
            .orElseThrow(() -> new AuthenticationFailedException("账号或密码错误，或账号已失效"));
    UserResult result = cn.edu.xmu.clonefactory.util.CloneFactory.copy(new UserResult(), account);
    result.setKind(account.getKind());
    return new AuthData.Token(
        credentials.issue(account.getId(), account.getCredentialVersion()),
        "Bearer",
        credentials.expiresIn(),
        result);
  }

  /** 每次请求重新读取账号状态和凭证版本。 */
  public AuthData.Identity resolve(String header) {
    com.cdwy.permission.service.port.auth.CredentialGateway.Claims claims =
        credentials.verify(header);
    Account a =
        accounts
            .find(claims.id())
            .filter(n -> n.usable(credentials.now()))
            .filter(n -> n.getCredentialVersion() == claims.version())
            .orElseThrow(() -> new AuthenticationFailedException("账号或凭证已失效，请重新登录"));
    return new AuthData.Identity(a.getId(), a.getAdministrator());
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
