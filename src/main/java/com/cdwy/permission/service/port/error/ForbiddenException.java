package com.cdwy.permission.service.port.error;

/** 已登录但没有权限访问（HTTP 403），业务错误码固定为 {@code FORBIDDEN}，与统一身份接口契约一致。 */
public class ForbiddenException extends RuntimeException {
  /**
   * 创建无权限异常。
   *
   * @param message 可直接展示的中文提示
   */
  public ForbiddenException(String message) {
    super(message);
  }
}
