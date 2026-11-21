package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;

/** Account 领域对象：只维护业务状态和不变量，不依赖 JPA。 */
@cn.edu.xmu.clonefactory.CopyTo(com.cdwy.permission.service.port.data.UserResult.class)
@Getter
@Setter
public class Account {
  private Long id;
  private String username;
  private String passwordHash;
  private String displayName;
  private boolean enabled = true;
  private boolean administrator;
  private long credentialVersion;
  private String kind = EMPLOYEE;
  private java.time.Instant expiresAt;
  private Long departmentId;
  private String companyName;
  private String jobTitle;
  private String phone;
  private String sourceDepartment;

  public static final String EMPLOYEE = "EMPLOYEE", EXTERNAL = "EXTERNAL", TEMPORARY = "TEMPORARY";

  /** 凭证与业务授权每次根据账号当前状态判断。 */
  public boolean usable(java.time.Instant now) {
    return enabled && (expiresAt == null || expiresAt.isAfter(now));
  }

  /** 人员身份与有效期由对象维护，管理员不能被改为限期账号。 */
  public void profile(
      String name, Long department, String type, java.time.LocalDate day, AccessWindow window) {
    if (administrator && (!EMPLOYEE.equals(type) || day != null))
      throw new com.cdwy.permission.service.port.error.ForbiddenException("管理员不能改为有期限的人员身份");
    java.time.Instant expiry = window.expiry(type, day);
    displayName = name;
    departmentId = department;
    kind = type;
    expiresAt = expiry;
  }

  /** 停用或重新启用均使旧凭证失效；重复请求不重复增加版本。 */
  public void changeStatus(Long actor, boolean value) {
    if (!value && (java.util.Objects.equals(actor, id) || administrator))
      throw new com.cdwy.permission.service.port.error.ForbiddenException("不能停用本人或管理员");
    if (enabled != value) {
      enabled = value;
      credentialVersion++;
    }
  }

  /** 退出登录：递增凭证版本，令牌不需要服务端保存。 */
  public void logout() {
    credentialVersion++;
  }

  /** 修改密码后使旧 JWT 失效。 */
  public void resetPassword(String hash) {
    passwordHash = hash;
    credentialVersion++;
  }

  /** 删除保护不依赖页面是否显示按钮。 */
  public void assertDeletable(Long actor) {
    if (java.util.Objects.equals(actor, id) || administrator)
      throw new com.cdwy.permission.service.port.error.ForbiddenException("不能删除本人或管理员");
  }

  /** 来源同步保留凭证、有效期和现有授权，不推导个人角色。 */
  public void importSource(
      String name, Long department, String company, String source, String job, String telephone) {
    if (administrator)
      throw new com.cdwy.permission.service.port.error.ConflictException("导入账号与管理员登录名冲突");
    displayName = name;
    departmentId = department;
    companyName = company;
    sourceDepartment = source;
    jobTitle = job;
    phone = telephone;
  }

  /** CloneFactory 的布尔 get 访问器约定。 */
  public boolean getEnabled() {
    return enabled;
  }

  /** CloneFactory 的布尔 get 访问器约定。 */
  public boolean getAdministrator() {
    return administrator;
  }
}
