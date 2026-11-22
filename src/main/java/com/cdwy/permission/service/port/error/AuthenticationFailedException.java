package com.cdwy.permission.service.port.error;

/** 未登录、凭证错误或令牌失效（HTTP 401），业务错误码固定为 {@code UNAUTHORIZED}，与统一身份接口契约一致。 */
public class AuthenticationFailedException extends RuntimeException {
  /**
   * 创建认证失败异常。
   *
   * @param message 可直接展示的中文提示，不说明账号是否存在
   */
  public AuthenticationFailedException(String message) {
    super(message);
  }
}
