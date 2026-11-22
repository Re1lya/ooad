package com.cdwy.permission.controller.common;

import com.cdwy.permission.service.port.error.AuthenticationFailedException;
import com.cdwy.permission.service.port.error.ConflictException;
import com.cdwy.permission.service.port.error.ForbiddenException;
import com.cdwy.permission.service.port.error.InvalidDataException;
import com.cdwy.permission.service.port.error.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 把异常翻译成统一错误体 {@code {code, message}}：{@code code} 是稳定的业务错误码，{@code message} 可直接展示。
 *
 * <p>业务异常在服务层抛出时已带业务码，这里只按异常类型确定 HTTP 状态，不按表名或消息内容分支。请求格式问题统一为
 * {@code BAD_REQUEST}，认证与授权失败为 {@code UNAUTHORIZED}、{@code FORBIDDEN}，与统一身份接口契约一致。
 */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final String BAD_REQUEST = "BAD_REQUEST";

  /** 对象不存在：沿用服务层给出的业务码。 */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage());
  }

  /** 与已有数据或当前状态冲突。 */
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, String>> conflict(ConflictException exception) {
    return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
  }

  /** 业务数据不满足业务规则。 */
  @ExceptionHandler(InvalidDataException.class)
  public ResponseEntity<Map<String, String>> invalidData(InvalidDataException exception) {
    return response(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage());
  }

  /** 已登录但没有权限。 */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, String>> forbidden(ForbiddenException exception) {
    return response(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
  }

  /** 未登录或凭证失效。 */
  @ExceptionHandler(AuthenticationFailedException.class)
  public ResponseEntity<Map<String, String>> unauthorized(AuthenticationFailedException exception) {
    return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage());
  }

  /** 请求体 DTO 的 Bean Validation 失败：返回第一个字段错误。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> invalidBody(MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + "：" + error.getDefaultMessage())
            .orElse("请求参数不合法");
    return response(HttpStatus.BAD_REQUEST, BAD_REQUEST, message);
  }

  /** 查询参数、路径参数或表单字段的 Bean Validation 失败：返回第一个参数错误。 */
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<Map<String, String>> invalidParameter(
      HandlerMethodValidationException exception) {
    String message =
        exception.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                result.getMethodParameter().getParameterName()
                                    + "："
                                    + error.getDefaultMessage()))
            .findFirst()
            .orElse("请求参数不合法");
    return response(HttpStatus.BAD_REQUEST, BAD_REQUEST, message);
  }

  /** API 层发现的请求问题。 */
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<Map<String, String>> badRequest(BadRequestException exception) {
    return response(HttpStatus.BAD_REQUEST, BAD_REQUEST, exception.getMessage());
  }

  /** JSON 无法解析、参数类型不符或缺少必填参数。 */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    MissingServletRequestPartException.class
  })
  public ResponseEntity<Map<String, String>> unreadable(Exception exception) {
    return response(HttpStatus.BAD_REQUEST, BAD_REQUEST, "请求格式不正确，请检查字段类型、必填参数和 JSON");
  }

  /** 框架或第三方库抛出的参数异常。 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException exception) {
    return response(HttpStatus.BAD_REQUEST, BAD_REQUEST, exception.getMessage());
  }

  /** 并发写入时数据库唯一约束或外键兜底；业务上的重复与引用已在服务层先行检查并给出具体业务码。 */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> constraint(DataIntegrityViolationException exception) {
    return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "数据重复或关联约束冲突，请刷新后重试");
  }

  /** 组装统一错误体，键依次为 {@code code}、{@code message}。 */
  private ResponseEntity<Map<String, String>> response(
      HttpStatus status, String code, String message) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("message", message);
    return ResponseEntity.status(status).body(body);
  }
}
