package com.cdwy.permission.controller.common;

/** API 层发现的请求问题（HTTP 400，业务码 {@code BAD_REQUEST}），用于 Bean Validation 无法表达的检查，例如上传的文件为空。 */
public class BadRequestException extends RuntimeException {
  /**
   * 创建请求异常。
   *
   * @param message 可直接展示的中文提示
   */
  public BadRequestException(String message) {
    super(message);
  }
}
