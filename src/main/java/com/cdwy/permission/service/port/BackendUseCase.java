package com.cdwy.permission.service.port;

/** 可切换业务用例的实际实现标识。 */
public interface BackendUseCase {
  /** 返回实际生效的实现。 */
  String strategy();
}
