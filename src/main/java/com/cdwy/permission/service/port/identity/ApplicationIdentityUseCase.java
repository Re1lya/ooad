package com.cdwy.permission.service.port.identity;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.IdentityData;

/**
 * 接入应用身份用例：课程管理系统等接入应用携带用户本人的 JWT，查询该用户在本应用内的有效角色与权限码。
 *
 * <p>权限仍只在权限系统维护：接入应用不保存角色、菜单、按钮和 API 授权，下一次请求即读取最新授权。
 */
public interface ApplicationIdentityUseCase extends BackendUseCase {
  /** 当前用户在指定应用内的有效角色与权限码；应用不存在返回 404，应用停用时权限为空。 */
  IdentityData.Current current(Long userId, String systemCode);

  /** 按统一身份用户标识（登录名）查询人员姓名与工号。 */
  IdentityData.User lookup(String systemCode, String userId);
}
