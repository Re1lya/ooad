package com.cdwy.permission.service.auth;

import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.user.PasswordPolicy;
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
public class AdminBootstrapService {
  private final UserAccountRepository users;
  private final PasswordEncoder encoder;
  private final ConsoleRegistrationService console;

  public AdminBootstrapService(
      UserAccountRepository users, PasswordEncoder encoder, ConsoleRegistrationService console) {
    this.users = users;
    this.encoder = encoder;
    this.console = console;
  }

  @Transactional
  public void initialize(String username, String password) {
    Role admin = console.ensure();
    if (username.isBlank() && password.isBlank()) return;
    if (!username.matches("[A-Za-z0-9_.-]{1,64}"))
      throw new IllegalArgumentException("管理员登录名格式不合法");
    PasswordPolicy.validate(password);
    var existing = users.findByUsername(username);
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
