package com.cdwy.permission.service.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.service.port.data.UserResult;

/** 结构化路线的账号结果转换，供用户服务和认证服务（登录响应）共用；结果不含密码散列和凭证版本。 */
public final class UserViews {
  private UserViews() {}

  /** 账号实体转为用例结果：同名字段由 CloneFactory 复制，人员类型输出为枚举名 {@code kind}，部门输出为标识（无部门为 null）。 */
  public static UserResult view(UserAccount user) {
    UserResult result = CloneFactory.copy(new UserResult(), user);
    result.setKind(user.getPersonType().name());
    // 实体保存的是部门关联而不是 departmentId 字段，CloneFactory 按字段名复制不到，这里显式取。
    result.setDepartmentId(user.getDepartmentId());
    return result;
  }
}
