package com.cdwy.permission.service.port.data;

import lombok.Getter;
import lombok.Setter;

/** 控制器完成校验后传入的用户创建命令。 */
@Getter
@Setter
public class CreateUserCommand {
  private String username;
  private String displayName;
  private String password;
  private Long departmentId;
  private String kind = "EMPLOYEE";
  private java.time.LocalDate expiresOn;
}
