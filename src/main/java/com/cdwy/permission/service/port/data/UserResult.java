package com.cdwy.permission.service.port.data;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户用例与登录结果中的用户信息：两种设计路线都由 CloneFactory 从账号实体或领域对象按同名字段复制，控制器再转换为 VO。
 *
 * <p>不含密码散列与凭证版本。{@code kind} 是 {@link PersonType} 的名称；{@code expiresAt} 为空表示不限期；
 * {@code companyName}、{@code jobTitle}、{@code phone}、{@code sourceDepartment} 来自员工信息同步，未经同步的用户为空。
 */
@Getter
@Setter
public class UserResult {

  private Long id;

  private String username;

  private String displayName;

  private boolean enabled;

  private boolean administrator;

  private Long departmentId;

  private String kind;

  private java.time.Instant expiresAt;

  private String companyName;

  private String jobTitle;

  private String phone;

  private String sourceDepartment;
}
