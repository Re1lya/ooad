package com.cdwy.permission.service.port.error;

import lombok.Getter;

/** 与已有数据或当前状态冲突（HTTP 409），例如编码重复、对象仍被引用。 */
@Getter
public class ConflictException extends RuntimeException {
  private final String code;

  /**
   * 创建带业务错误码的冲突异常。
   *
   * @param code 业务错误码，例如 {@code USERNAME_EXISTS}
   * @param message 可直接展示的中文提示
   */
  public ConflictException(String code, String message) {
    super(message);
    this.code = code;
  }
}
