package com.cdwy.permission.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前请求的登录用户：{@link ApiAccessConfig} 的拦截器校验令牌后，以本类全限定名为键写入请求属性。
 *
 * @param id 用户 ID
 * @param administrator 是否为管理员账号；接口访问仍按 RBAC 授权，个别规则（如查看接入应用的审计日志）参考此标志
 */
public record CurrentUser(Long id, boolean administrator) {
  /** 从请求属性读取当前用户；请求未经过鉴权拦截器（例如登录接口）时返回 {@code null}。 */
  public static CurrentUser from(HttpServletRequest request) {
    return (CurrentUser) request.getAttribute(CurrentUser.class.getName());
  }
}
