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

  /**
   * 接入应用登录：{@code target} 是被调用的应用编码（统一身份基地址中的编码），{@code systems} 是请求声明的令牌目标系统，
   * 必须包含 target，且都是已登记的应用。
   */
  public record AppLogin(String username, String password, java.util.List<String> systems, String target) {}
}
