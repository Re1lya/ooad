package com.cdwy.permission.service.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.service.port.data.UserResult;

public final class UserViews {
  private UserViews() {}

  public static UserResult view(UserAccount user) {
    UserResult result = CloneFactory.copy(new UserResult(), user);
    result.setKind(user.getPersonType().name());
    return result;
  }
}
