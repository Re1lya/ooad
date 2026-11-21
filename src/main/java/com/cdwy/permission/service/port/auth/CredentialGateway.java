package com.cdwy.permission.service.port.auth;

import com.cdwy.permission.service.ddd.domain.model.AccessWindow;
import java.time.Instant;

/** 认证所需的密码散列、JWT 和时钟技术边界。 */
public interface CredentialGateway {
  /** JWT 验证后的身份与目标系统，角色权限不写入 Token。 */
  record Claims(Long id, long version, java.util.Set<String> audience) {}

  /** 生成密码散列。 */
  String encode(String password);

  /** 验证现有密码。 */
  boolean matches(String password, String hash);

  /** 按项目密码规则校验明文，不落日志。 */
  void validatePassword(String password);

  /** 按凭证版本号与目标系统签发。 */
  String issue(Long id, long version, java.util.Collection<String> audience);

  /** 验签与有效期校验。 */
  Claims verify(String header);

  /** Token 的声明有效时长。 */
  long expiresIn();

  /** 当前服务器时间。 */
  Instant now();

  /** 项目时区的人员期限策略。 */
  AccessWindow window();
}
