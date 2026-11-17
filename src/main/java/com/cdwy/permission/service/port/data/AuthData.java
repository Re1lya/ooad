package com.cdwy.permission.service.port.data;

/** AuthData 业务用例的不可变输入与结果。 */
public final class AuthData {
  private AuthData() {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Login(String username, String password) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Token(String accessToken, String tokenType, long expiresIn, UserResult user) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record PasswordChange(String currentPassword, String newPassword) {}

  /** 与 HTTP 表现及数据库技术无关的用例数据。 */
  public record Identity(Long id, boolean administrator) {}
}
