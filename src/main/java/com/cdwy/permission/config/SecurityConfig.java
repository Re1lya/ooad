package com.cdwy.permission.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置：提供 BCrypt 密码编码器，并关闭会话、CSRF、表单登录等默认机制。
 *
 * <p>Spring Security 层放行所有请求，令牌校验与 RBAC 授权由 {@link ApiAccessConfig} 注册的拦截器完成。
 */
@Configuration
public class SecurityConfig {
  /** 账号密码使用 BCrypt 散列。 */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** 无状态过滤链：不创建服务端会话，不使用 Cookie 认证，所有请求交给后续拦截器鉴权。 */
  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    // Same-origin bearer APIs; no cookie authentication or server-side login session.
    return http.csrf(c -> c.disable())
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .logout(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(c -> c.anyRequest().permitAll())
        .build();
  }
}
