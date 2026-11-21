package com.cdwy.permission.config;

import com.cdwy.permission.service.auth.AdminBootstrapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;

/**
 * 启动初始化：Spring 容器启动完成后补齐权限系统自身的内置授权数据，并按配置确保首次管理员存在。
 *
 * <p>管理员取自配置项 {@code permission.bootstrap.username} 与 {@code permission.bootstrap.password}，两项都为空时只补齐内置数据。
 * 配置不合法、同名账号不是管理员或已存在其他管理员时抛出异常，应用启动失败；已存在的管理员不会按配置修改密码。
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
  private final AdminBootstrapService service;
  private final String username;
  private final String password;

  /** 注入 {@link AdminBootstrapService} 与启动管理员配置；未配置时两项均为空字符串。 */
  public AdminBootstrap(
      AdminBootstrapService service,
      @Value("${permission.bootstrap.username:}") String username,
      @Value("${permission.bootstrap.password:}") String password) {
    this.service = service;
    this.username = username;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    service.initialize(username, password);
  }
}
