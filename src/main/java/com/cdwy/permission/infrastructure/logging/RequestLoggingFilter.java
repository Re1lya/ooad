package com.cdwy.permission.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求日志与 MDC：为每个 /api 请求分配请求号，并记录前端通过 {@code X-Client-Menu} 报告的当前菜单。
 *
 * <p>前端每次切换菜单都会带着新菜单名调用后端接口，因此“菜单切换 → 后端 logback 日志”可以按 menu= 直接对照。
 * 请求号同时写入响应头 {@code X-Request-Id}，便于从浏览器开发者工具回查日志。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {
  /** 前端报告当前菜单的请求头。 */
  public static final String MENU_HEADER = "X-Client-Menu";
  /** 回写请求号的响应头。 */
  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final Pattern SAFE_MENU = Pattern.compile("[A-Za-z0-9_:/-]{1,48}");
  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  /** 只记录接口请求；静态资源和健康检查不产生业务日志。 */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
  }

  /**
   * 生成 8 位请求号写入 MDC（{@code requestId}、{@code menu}）和响应头 {@code X-Request-Id}，记录请求开始与结束（状态、耗时）；
   * 结束时清空整个 MDC，包括拦截器写入的 {@code userId}，避免线程复用时串号。
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    String menu = menu(request.getHeader(MENU_HEADER));
    MDC.put("requestId", requestId);
    MDC.put("menu", menu);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    long started = System.nanoTime();
    log.info("请求开始 {} {} 菜单={}", request.getMethod(), request.getRequestURI(), menu);
    try {
      chain.doFilter(request, response);
    } finally {
      long elapsed = (System.nanoTime() - started) / 1_000_000;
      log.info(
          "请求结束 {} {} 状态={} 耗时={} ms",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          elapsed);
      MDC.clear();
    }
  }

  /** 只接受简单菜单标识，防止日志注入。 */
  static String menu(String value) {
    if (value == null || value.isBlank()) return "-";
    String trimmed = value.trim();
    return SAFE_MENU.matcher(trimmed).matches() ? trimmed : "invalid";
  }
}
