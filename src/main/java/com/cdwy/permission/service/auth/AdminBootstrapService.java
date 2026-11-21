package com.cdwy.permission.service.auth;

import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.user.PasswordPolicy;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次管理员是部署启动配置，不提供初始化 HTTP API。
 *
 * <p>每次启动先补齐权限系统自身的内置授权数据（{@link ConsoleRegistrationService}），再确保启动管理员存在并拥有内置管理员角色：
 * 控制台的菜单和管理接口都按 RBAC 授权，管理员也不例外。
 */
@Service
@RequiredArgsConstructor
public class AdminBootstrapService {
  private final UserAccountRepository users;
  private final PasswordEncoder encoder;
  private final ConsoleRegistrationService console;

  /**
   * 启动时由 {@code AdminBootstrap} 调用，参数取 {@code permission.bootstrap.username/password}；整个过程在一个事务内，可重复执行：
   * 先补齐 rbac 应用的内置授权数据，再创建或确认首次管理员，并分配内置管理员角色。
   *
   * <p>两项都为空时只补齐内置数据。登录名须为 1～64 位字母、数字或 {@code _.-}，否则抛 {@code IllegalArgumentException}；
   * 密码按项目密码规则校验（{@code PASSWORD_POLICY_VIOLATION}）。同名账号已存在但不是管理员、或已有其他首次管理员时抛
   * {@code IllegalStateException}，宁可启动失败也不提升普通账号；已存在的管理员不会被改密码。
   */
  @Transactional
  public void initialize(String username, String password) {
    Role admin = console.ensure();
    if (username.isBlank() && password.isBlank()) return;
    if (!username.matches("[A-Za-z0-9_.-]{1,64}"))
      throw new IllegalArgumentException("管理员登录名格式不合法");
    PasswordPolicy.validate(password);
    Optional<UserAccount> existing = users.findByUsername(username);
    UserAccount user;
    if (existing.isPresent()) {
      if (!existing.get().getAdministrator()) throw new IllegalStateException("禁止将普通账号自动提升为管理员");
      user = existing.get();
    } else {
      if (users.existsByAdministratorTrue()) throw new IllegalStateException("已存在首次管理员");
      user = new UserAccount();
      user.setUsername(username);
      user.setDisplayName("系统管理员");
      user.setAdministrator(true);
      user.setPasswordHash(encoder.encode(password));
      user = users.save(user);
    }
    console.assignAdministrator(user, admin);
  }
}
