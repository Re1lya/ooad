package com.cdwy.permission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 统一权限管理系统（RBAC）的 Spring Boot 启动类。
 *
 * <p>{@code @ConfigurationPropertiesScan} 让 {@link com.cdwy.permission.config.JwtSettings}、
 * {@link com.cdwy.permission.config.AccessPolicy} 等配置记录无需单独注册即可绑定配置。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NifiedPermissionSystemApplication {
  /** 启动应用；后端实现路线由配置项 {@code app.backend-strategy} 选择（{@code structured} 或 {@code ddd}，未配置时为前者）。 */
  public static void main(String[] args) {
    SpringApplication.run(NifiedPermissionSystemApplication.class, args);
  }
}
