package com.cdwy.permission.service.port.data;

import java.util.List;

/** 接入应用身份用例的不可变结果：与 HTTP 表现及数据库技术无关。 */
public final class IdentityData {
  private IdentityData() {}

  /**
   * 某接入应用看到的当前用户。
   *
   * @param subject 统一身份用户标识（登录名）
   * @param roles 有效角色编码：直接分配角色及沿继承链可达的启用角色
   * @param permissions 该应用内的有效菜单、按钮权限码与其绑定的后端 API 权限码
   */
  public record Current(String subject, List<String> roles, List<String> permissions) {}

  /** 按统一身份用户标识查询到的人员；no 为工号或学号，本系统取登录名。 */
  public record User(String userId, String no, String name) {}
}
