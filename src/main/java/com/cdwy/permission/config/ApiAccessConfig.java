package com.cdwy.permission.config;

import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.error.ForbiddenException;
import com.cdwy.permission.service.port.rbac.AuthorizationUseCase;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class ApiAccessConfig implements WebMvcConfigurer {
  private final AuthenticationUseCase auth;
  private final AuthorizationUseCase authorization;

  public ApiAccessConfig(AuthenticationUseCase auth, AuthorizationUseCase authorization) {
    this.auth = auth;
    this.authorization = authorization;
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
                request.setAttribute(
                    CurrentUser.class.getName(), new CurrentUser(user.id(), user.administrator()));
                BusinessAccess rule = method.getMethodAnnotation(BusinessAccess.class);
                if (rule == null) rule = method.getBeanType().getAnnotation(BusinessAccess.class);
                String route = request.getRequestURI().substring(request.getContextPath().length());
                if (rule != null)
                  authorization.requireApi(user.id(), rule.value(), request.getMethod(), route);
                else if (!route.equals("/api/me")
                    && !route.startsWith("/api/me/")
                    && !user.administrator()) throw new ForbiddenException("此管理操作需要管理员身份");
                return true;
              }
            })
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login");
  }
}
