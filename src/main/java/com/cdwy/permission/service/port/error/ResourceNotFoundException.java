package com.cdwy.permission.service.port.error;

/** 对象不存在（HTTP 404）。{@code code} 是给接入应用看的业务错误码，默认 NOT_FOUND。 */
public class ResourceNotFoundException extends RuntimeException {
  private final String code;

  public ResourceNotFoundException(String message) {
    this("NOT_FOUND", message);
  }

  public ResourceNotFoundException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
