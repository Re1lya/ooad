package com.cdwy.permission.service.port.auth;

import java.util.Optional;

/** 当前请求的登录用户：由 Web 基础设施提供，服务层不直接读取 HTTP 请求。 */
public interface CurrentActorProvider {
  /** 当前登录用户 ID；非 HTTP 调用或未登录时为空。 */
  Optional<Long> currentUserId();
}
