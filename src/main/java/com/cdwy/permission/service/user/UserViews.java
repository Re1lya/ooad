package com.cdwy.permission.service.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.service.port.data.UserResult;

public final class UserViews {
  private UserViews() {}

  public static UserResult view(UserAccount user) {
    UserResult result = CloneFactory.copy(new UserResult(), user);
    result.setKind(user.getPersonType().name());
    // 实体保存的是部门关联而不是 departmentId 字段，CloneFactory 按字段名复制不到，这里显式取。
    result.setDepartmentId(user.getDepartmentId());
    return result;
  }
}
