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

  /** 员工信息同步新建人员时使用的密码散列占位值：不是 BCrypt 格式，任何密码都无法匹配。 */
  public static final String UNPROVISIONED = "!UNPROVISIONED:";

  /** 员工信息同步新建的人员：停用、没有可用密码；管理员设置密码并启用后才能登录。 */
  public static Account unprovisioned(String username) {
    Account account = new Account();
    account.username = username;
    account.enabled = false;
    account.passwordHash = UNPROVISIONED + java.util.UUID.randomUUID();
    account.kind = EMPLOYEE;
    return account;
  }

  /**
   * 员工信息同步：只更新来源字段，保留凭证、启用状态、期限与授权；管理员账号不参与同步。
   *
   * @return 是否有字段发生变化
   */
  public boolean syncSource(
      String name, Long department, String company, String source, String job, String telephone) {
    if (administrator)
      throw new com.cdwy.permission.service.port.error.ConflictException(
          "用户名 " + username + " 是管理员账号，员工信息不能覆盖它");
    boolean changed =
        !java.util.Objects.equals(displayName, name)
            || !java.util.Objects.equals(departmentId, department)
            || !java.util.Objects.equals(companyName, company)
            || !java.util.Objects.equals(sourceDepartment, source)
            || !java.util.Objects.equals(jobTitle, job)
            || !java.util.Objects.equals(phone, telephone);
    displayName = name;
    departmentId = department;
    companyName = company;
    sourceDepartment = source;
    jobTitle = job;
    phone = telephone;
    return changed;
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
