package com.cdwy.permission.infrastructure.security;

import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.service.port.auth.CurrentActorProvider;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 从 ApiAccessConfig 写入请求属性的 CurrentUser 读取操作人。 */
@Component
public class RequestCurrentActorProvider implements CurrentActorProvider {
  @Override
  public Optional<Long> currentUserId() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
      return Optional.empty();
    CurrentUser user = CurrentUser.from(attributes.getRequest());
    return user == null ? Optional.empty() : Optional.of(user.id());
  }
}
