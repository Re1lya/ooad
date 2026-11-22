package com.cdwy.permission.dto.user;

import cn.edu.xmu.clonefactory.CopyTo;
import com.cdwy.permission.service.port.data.CreateUserCommand;
import com.cdwy.permission.service.port.data.PersonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 新建人员的请求。同名字段由 CloneFactory 复制到 {@link CreateUserCommand}，人员类型由映射器转换。
 *
 * <p>登录名唯一、密码强度、外部与临时人员的有效期规则由服务层检查。
 */
@Getter
@Setter
@CopyTo(CreateUserCommand.class)
public class UserCreateDTO {
  /** 登录名（工号或学号）。 */
  @NotBlank
  @Pattern(regexp = "[A-Za-z0-9_.-]{1,64}")
  private String username;

  /** 姓名。 */
  @NotBlank
  @Size(max = 128)
  private String displayName;

  /** 初始密码。 */
  @NotBlank private String password;

  /** 所属部门，可空。 */
  private Long departmentId;

  /** 人员类型，默认正式员工。 */
  @NotNull private PersonType personType = PersonType.EMPLOYEE;

  /** 外部与临时人员的有效期截止日。 */
  private LocalDate expiresOn;
}
