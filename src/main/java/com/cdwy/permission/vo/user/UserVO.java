package com.cdwy.permission.vo.user;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.cdwy.permission.service.port.data.PersonType;
import com.cdwy.permission.service.port.data.UserResult;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 人员资料，不含密码散列和凭证版本。同名字段由 CloneFactory 从 {@link UserResult} 复制，人员类型由映射器转换。 */
@Getter
@Setter
@CopyFrom(UserResult.class)
public class UserVO {
  /** 标识。 */
  private Long id;

  /** 登录名（工号或学号）。 */
  private String username;

  /** 姓名。 */
  private String displayName;

  /** 是否启用。 */
  private boolean enabled;

  /** 是否为权限系统的首次管理员。 */
  private boolean administrator;

  /** 所属部门。 */
  private Long departmentId;

  /** 人员类型。 */
  private PersonType personType;

  /** 外部与临时人员的有效期截止时刻。 */
  private Instant expiresAt;

  /** 员工信息同步带来的分公司名称。 */
  private String companyName;

  /** 员工信息同步带来的岗位。 */
  private String jobTitle;

  /** 员工信息同步带来的电话。 */
  private String phone;

  /** 员工信息表中的部门名称。 */
  private String sourceDepartment;
}
