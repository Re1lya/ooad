package com.cdwy.permission.config;

import com.cdwy.permission.service.auth.JwtTokenService;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.error.ForbiddenException;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/**
 * 接口访问规则：除登录外的 /api 请求先校验 Bearer JWT，并要求令牌的 {@code aud} 包含被调用的系统（/api/apps/{应用编码}/...
 * 为该应用，其余为权限系统自身 rbac）；
 *
 * <ul>
 *   <li>/api/me 与 /api/apps/{应用编码}/... 只要求登录（接入应用以用户本人令牌查询身份、写审计）；标注 {@link AppAccess}
 *       的统一身份目录接口另按该应用的 API 资源授权；
 *   <li>其余接口须标注 {@link BusinessAccess}，按 RBAC1 有效授权链匹配该应用的 API 资源：权限系统自己的管理接口登记在应用
 *       rbac 下（见 ConsoleCatalog），管理员也通过内置角色获得授权；
 *   <li>没有登记访问规则的接口一律拒绝。
 * </ul>
 *
 * <p>接入应用的浏览器直连接口（/api/apps/**）按配置项 {@code permission.cors.allowed-origins} 放行跨域来源。
 */
@Configuration
public class ApiAccessConfig implements WebMvcConfigurer {
  private static final Logger log = LoggerFactory.getLogger(ApiAccessConfig.class);
  private final AuthenticationUseCase auth;
  private final AuthorizationUseCase authorization;
  private final String[] allowedOrigins;

  public ApiAccessConfig(
      AuthenticationUseCase auth,
      AuthorizationUseCase authorization,
      @Value("${permission.cors.allowed-origins:}") String allowedOrigins) {
    this.auth = auth;
    this.authorization = authorization;
    this.allowedOrigins =
        java.util.Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toArray(String[]::new);
  }

  /** 令牌目标系统：/api/apps/{应用编码}/... 为该应用，其余接口为权限系统自身。 */
  static String audience(String route) {
    if (!route.startsWith("/api/apps/")) return JwtTokenService.CONSOLE_AUDIENCE;
    String rest = route.substring("/api/apps/".length());
    int slash = rest.indexOf('/');
    return slash < 0 ? rest : rest.substring(0, slash);
  }

  /** 课程前端等接入应用在浏览器里直连统一身份接口；未配置来源时不开放跨域。 */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (allowedOrigins.length == 0) return;
    registry
        .addMapping("/api/apps/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type", "Accept")
        .maxAge(1800);
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
                String route = request.getRequestURI().substring(request.getContextPath().length());
                com.cdwy.permission.service.port.data.AuthData.Identity user =
                    auth.resolve(request.getHeader("Authorization"), audience(route));
                MDC.put("userId", String.valueOf(user.id()));
                request.setAttribute(
                    CurrentUser.class.getName(), new CurrentUser(user.id(), user.administrator()));
                BusinessAccess rule = method.getMethodAnnotation(BusinessAccess.class);
                if (rule == null) rule = method.getBeanType().getAnnotation(BusinessAccess.class);
                if (method.hasMethodAnnotation(AppAccess.class)) {
                  String app = audience(route);
                  authorization.requireApi(
                      user.id(),
                      app,
                      request.getMethod(),
                      route.substring(("/api/apps/" + app).length()));
                } else if (selfService(route)) {
                  // 个人接口与接入应用身份接口只要求登录。
                } else if (rule != null)
                  authorization.requireApi(user.id(), rule.value(), request.getMethod(), route);
                else {
                  log.warn("拒绝访问未登记访问规则的接口 {} {}", request.getMethod(), route);
                  throw new ForbiddenException("此接口未登记访问规则");
                }
                return true;
              }
            })
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login", "/api/apps/*/auth/login");
  }
}
