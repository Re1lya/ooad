package com.cdwy.permission.service.port.error;

import lombok.Getter;

/**
 * 业务数据不满足业务规则（HTTP 400），例如角色继承成环、按钮没有所属菜单。
 *
 * <p>字段长度、格式、取值范围等请求格式问题由 API 层的 Bean Validation 拦截，不使用本异常。
 */
@Getter
public class InvalidDataException extends RuntimeException {
  private final String code;

  /**
   * 创建带业务错误码的数据非法异常。
   *
   * @param code 业务错误码，例如 {@code ROLE_INHERITANCE_CYCLE}
   * @param message 可直接展示的中文提示
   */
  public InvalidDataException(String code, String message) {
    super(message);
    this.code = code;
  }
}
