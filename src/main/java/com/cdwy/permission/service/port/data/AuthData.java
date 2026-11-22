package com.cdwy.permission.service.port.data;

/** 认证用例（{@code AuthenticationUseCase}）的输入与结果：登录、令牌解析和修改本人密码。 */
public final class AuthData {
  private AuthData() {}

  /**
   * 控制台登录（{@code AuthenticationUseCase#login}）的输入。
   *
   * @param username 登录名（工号或学号），精确匹配
   * @param password 明文密码，只与散列比对，不落日志
   */
  public record Login(String username, String password) {}

  /**
   * 控制台登录与接入应用登录的结果。
   *
   * @param accessToken 签发的 JWT，只携带用户标识、凭证版本与目标系统（{@code aud}），不含角色和权限
   * @param tokenType 令牌类型，固定为 {@code Bearer}
   * @param expiresIn 有效时长（秒），取配置 {@code permission.jwt.expiration-seconds}
   * @param user 登录用户的资料，不含密码散列与凭证版本
   */
  public record Token(String accessToken, String tokenType, long expiresIn, UserResult user) {}

  /**
   * 修改本人密码（{@code AuthenticationUseCase#changePassword}）的输入。
   *
   * @param currentPassword 原密码，与当前散列不符时认证失败（401）
   * @param newPassword 新密码，须符合项目密码规则
   */
  public record PasswordChange(String currentPassword, String newPassword) {}

  /**
   * 令牌解析（{@code AuthenticationUseCase#resolve}）的结果，由 API 拦截器作为当前登录用户写入请求。
   *
   * @param id 用户标识
   * @param administrator 是否为首次管理员账号；接口授权仍按 RBAC，个别规则（如查看接入应用的审计日志）参考此标志
   */
  public record Identity(Long id, boolean administrator) {}

  /**
   * 接入应用登录：{@code target} 是被调用的应用编码（统一身份基地址中的编码），{@code systems} 是请求声明的令牌目标系统，
   * 必须包含 target，且都是已登记的应用。
   */
  public record AppLogin(String username, String password, java.util.List<String> systems, String target) {}
}
