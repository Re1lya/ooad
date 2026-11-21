package com.cdwy.permission.service.user;

import java.nio.charset.StandardCharsets;
import com.cdwy.permission.service.port.error.InvalidDataException;

/** 密码强度策略：结构化用户服务、DDD 路线的凭证适配器和启动管理员初始化共用同一规则；无状态工具类。 */
public final class PasswordPolicy {
  private PasswordPolicy() {}

  /**
   * 校验密码强度，不合格抛 {@code PASSWORD_POLICY_VIOLATION}：不能为空白，至少 8 个字符，UTF-8 编码不超过 72 字节
   * （BCrypt 的输入上限）；大写、小写、数字、符号四类至少占三类，ASCII 字母和数字以外的字符（含空格、中文）都算符号。
   */
  public static void validate(String password) {
    if (password == null
        || password.isBlank()
        || password.length() < 8
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new InvalidDataException("PASSWORD_POLICY_VIOLATION", "密码至少 8 个字符，UTF-8 编码不超过 72 字节");
    }
    int kinds = 0;
    if (password.matches(".*[A-Z].*")) kinds++;
    if (password.matches(".*[a-z].*")) kinds++;
    if (password.matches(".*[0-9].*")) kinds++;
    if (password.matches(".*[^A-Za-z0-9].*")) kinds++;
    if (kinds < 3) throw new InvalidDataException("PASSWORD_POLICY_VIOLATION", "密码需包含大写、小写、数字、符号中的至少三类");
  }
}
