package com.cdwy.permission.infrastructure.logging;

import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 分层 logback 日志切面：Controller、Service（结构化服务、DDD 应用服务与共享技术服务）、基础设施适配器。
 *
 * <p>日志使用目标类自己的 Logger，因此可以按包分别调整级别：Controller 与 Service 默认 INFO，
 * 基础设施适配器默认 DEBUG。参数只输出数字、布尔、枚举和短字符串；请求体对象只输出类型名，
 * 涉及密码或登录的方法不输出任何参数。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LayerLoggingAspect {
  /** 字符串参数最多输出的字符数，超出部分截断。 */
  private static final int MAX_TEXT = 64;
  /** 每个请求都会调用的方法名（令牌校验、取当前时间），与基础设施层一样只在 DEBUG 输出。 */
  private static final java.util.Set<String> PER_REQUEST = java.util.Set.of("verify", "resolve", "now");

  /** 记录各层方法完成、耗时与失败原因。 */
  @Around(
      "(within(com.cdwy.permission.controller..*)"
          + " || within(com.cdwy.permission.service..*)"
          + " || within(com.cdwy.permission.infrastructure..*))"
          + " && !within(com.cdwy.permission.infrastructure.logging..*)"
          + " && !execution(* jakarta.servlet.Filter+.*(..))")
  public Object logLayerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
    Class<?> target = AopUtils.getTargetClass(joinPoint.getTarget());
    Logger log = LoggerFactory.getLogger(target);
    String layer = layerOf(target);
    String method = joinPoint.getSignature().getName();
    // 基础设施适配器与每个请求都会触发的令牌校验只在 DEBUG 输出，INFO 保留业务调用链。
    boolean infrastructure = "Infrastructure".equals(layer) || PER_REQUEST.contains(method);
    if (infrastructure ? !log.isDebugEnabled() : !log.isInfoEnabled()) return joinPoint.proceed();
    String[] names =
        joinPoint.getSignature() instanceof org.aspectj.lang.reflect.MethodSignature signature
            ? signature.getParameterNames()
            : null;
    String args = arguments(method, names, joinPoint.getArgs());
    long started = System.nanoTime();
    try {
      Object result = joinPoint.proceed();
      long elapsed = (System.nanoTime() - started) / 1_000_000;
      if (infrastructure) log.debug("[{}] {}({}) 完成，耗时 {} ms", layer, method, args, elapsed);
      else log.info("[{}] {}({}) 完成，耗时 {} ms", layer, method, args, elapsed);
      return result;
    } catch (Throwable error) {
      long elapsed = (System.nanoTime() - started) / 1_000_000;
      log.warn(
          "[{}] {}({}) 失败，耗时 {} ms：{} {}",
          layer,
          method,
          args,
          elapsed,
          error.getClass().getSimpleName(),
          error.getMessage());
      throw error;
    }
  }

  /** 按包名区分层次，便于在日志中按层筛选。 */
  static String layerOf(Class<?> target) {
    String name = target.getPackageName();
    if (name.startsWith("com.cdwy.permission.controller")) return "Controller";
    if (name.startsWith("com.cdwy.permission.service.ddd.application")) return "DDD-Application";
    if (name.startsWith("com.cdwy.permission.service")) return "Service";
    return "Infrastructure";
  }

  /**
   * 只输出不敏感的简单参数：密码、登录、令牌相关方法完全隐藏参数；其他方法中参数名含 password、secret、token、credential
   * 的参数单独打码（例如启动管理员的初始密码）。
   */
  static String arguments(String method, String[] names, Object[] values) {
    String lower = method.toLowerCase(Locale.ROOT);
    for (String sensitive : SENSITIVE_METHODS)
      if (lower.contains(sensitive)) return values.length == 0 ? "" : "***";
    java.util.List<String> parts = new java.util.ArrayList<>();
    for (int i = 0; i < values.length; i++)
      parts.add(
          names != null && i < names.length && sensitiveName(names[i])
              ? "***"
              : describe(values[i]));
    return String.join(", ", parts);
  }

  /** 参数名（不区分大小写）含 password、secret、token 或 credential 时视为敏感。 */
  private static boolean sensitiveName(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    return SENSITIVE_PARAMETERS.stream().anyMatch(lower::contains);
  }

  private static final java.util.List<String> SENSITIVE_PARAMETERS =
      java.util.List.of("password", "secret", "token", "credential");

  /** 方法名（不区分大小写）含其中任一词时，不输出任何参数。 */
  private static final java.util.List<String> SENSITIVE_METHODS =
      java.util.List.of(
          "password", "login", "resolve", "verify", "token", "issue", "secret", "credential");
  /** 形似 Bearer 认证头或 JWT 的字符串，输出时打码。 */
  private static final java.util.regex.Pattern TOKEN_LIKE =
      java.util.regex.Pattern.compile("(?i)^bearer\\s.*|^[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\..*");

  /** 换行等控制字符：写日志前替换掉，防止参数伪造日志行。 */
  private static final java.util.regex.Pattern CONTROL = java.util.regex.Pattern.compile("\\p{Cntrl}");

  /**
   * 单个参数的日志文本：数字、布尔、枚举原样输出；字符串形似令牌时打码，过长时截断，控制字符替换为 {@code ?}；请求对象输出
   * {@code request}，其余只输出类型名。
   */
  private static String describe(Object value) {
    if (value == null) return "null";
    if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>)
      return String.valueOf(value);
    if (value instanceof CharSequence text) {
      if (TOKEN_LIKE.matcher(text).matches()) return "***";
      String shown = text.length() <= MAX_TEXT ? text.toString() : text.subSequence(0, MAX_TEXT) + "…";
      return "\"" + CONTROL.matcher(shown).replaceAll("?") + "\"";
    }
    if (value instanceof jakarta.servlet.ServletRequest) return "request";
    return value.getClass().getSimpleName();
  }
}
