package com.cdwy.permission.config;

import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.error.ForbiddenException;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/**
 * 接口访问规则：除登录外的 /api 请求先校验 Bearer JWT；
 *
 * <ul>
 *   <li>标注 {@link BusinessAccess} 的业务接口按 RBAC1 有效授权链匹配 API 资源；
 *   <li>/api/me 与 /api/apps/{应用编码}/... 只要求登录（接入应用以用户本人令牌查询身份、写审计）；
 *   <li>其余管理接口要求管理员身份。
 * </ul>
 */
@Configuration
public class ApiAccessConfig implements WebMvcConfigurer {
  private static final Logger log = LoggerFactory.getLogger(ApiAccessConfig.class);
  private final AuthenticationUseCase auth;
  private final AuthorizationUseCase authorization;

  public ApiAccessConfig(AuthenticationUseCase auth, AuthorizationUseCase authorization) {
    this.auth = auth;
    this.authorization = authorization;
  }

  /** 只需登录即可访问的个人与接入应用接口。 */
  static boolean selfService(String route) {
    return route.equals("/api/me") || route.startsWith("/api/me/") || route.startsWith("/api/apps/");
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("Cache-Control", "no-store");
                if (!(handler instanceof HandlerMethod method)) return true;
                com.cdwy.permission.service.port.data.AuthData.Identity user =
                    auth.resolve(request.getHeader("Authorization"));
                MDC.put("userId", String.valueOf(user.id()));
                request.setAttribute(
                    CurrentUser.class.getName(), new CurrentUser(user.id(), user.administrator()));
                BusinessAccess rule = method.getMethodAnnotation(BusinessAccess.class);
                if (rule == null) rule = method.getBeanType().getAnnotation(BusinessAccess.class);
                String route = request.getRequestURI().substring(request.getContextPath().length());
                if (rule != null)
                  authorization.requireApi(user.id(), rule.value(), request.getMethod(), route);
                else if (!selfService(route) && !user.administrator()) {
                  log.warn("拒绝非管理员访问管理接口 {} {}", request.getMethod(), route);
                  throw new ForbiddenException("此管理操作需要管理员身份");
                }
                return true;
              }
            })
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login");
  }
}
