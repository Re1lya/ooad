package com.cdwy.permission.service.port.error;

import lombok.Getter;

/** 要操作的对象不存在（HTTP 404）。{@code code} 是稳定的业务错误码，例如 {@code USER_NOT_FOUND}。 */
@Getter
public class ResourceNotFoundException extends RuntimeException {
  private final String code;

  /**
   * 创建带业务错误码的不存在异常。
   *
   * @param code 业务错误码
   * @param message 可直接展示的中文提示
   */
  public ResourceNotFoundException(String code, String message) {
    super(message);
    this.code = code;
  }
}
