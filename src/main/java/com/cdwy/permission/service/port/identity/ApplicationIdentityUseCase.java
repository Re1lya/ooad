package com.cdwy.permission.service.port.identity;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.IdentityData;
import com.cdwy.permission.service.port.data.AuditData;

/**
 * 接入应用身份用例：课程管理系统等接入应用携带用户本人的 JWT，查询该用户在本应用内的有效角色与权限，以及统一身份目录。
 *
 * <p>权限仍只在权限系统维护：接入应用不保存角色、菜单、按钮和 API 授权，下一次请求即读取最新授权。
 */
public interface ApplicationIdentityUseCase extends BackendUseCase {
  /** 当前用户在指定应用内的有效角色与权限码；应用不存在返回 404，应用停用或账号失效时角色与权限为空。 */
  IdentityData.Current current(Long userId, String systemCode);

  /** 按统一身份用户标识（登录名）查询人员姓名与工号；不存在返回 404 IDENTITY_USER_NOT_FOUND。 */
  IdentityData.User lookup(String systemCode, String userId);

  /** 当前用户在该应用内是否拥有指定的前端菜单或按钮权限码（不含后端 API 权限码）。 */
  boolean permitted(Long userId, String systemCode, String permission);

  /** 当前用户能否调用该应用中 operationId 对应的接口；operationId 未登记返回 404 API_OPERATION_NOT_FOUND。 */
  IdentityData.ApiCheck apiPermission(Long userId, String systemCode, String operationId);

  /** 逐层查询部门：parentId 为空时为根部门，否则为其直接子部门；关键字匹配名称或编码；数据库分页。 */
  IdentityData.Page<IdentityData.Department> departments(
      String systemCode, Long parentId, IdentityData.Query query);

  /** 部门下的用户：关键字匹配工号、学号或姓名；数据库分页；部门不存在返回 404 IDENTITY_DEPARTMENT_NOT_FOUND。 */
  IdentityData.Page<IdentityData.DepartmentUser> departmentUsers(
      String systemCode, Long departmentId, IdentityData.Query query);

  /**
   * 查询本应用的审计记录。管理员，或在本应用内至少拥有一项有效权限的用户可以查询，否则返回 403；应用不存在返回 404。
   *
   * @param userId 当前用户
   * @param administrator 当前用户是否为首次管理员
   * @param systemCode 应用编码
   * @param query 查询条件
   * @return 按时间倒序的一页记录
   */
  AuditData.Page auditLogs(
      Long userId, boolean administrator, String systemCode, AuditData.Query query);

  /**
   * 以当前用户为操作人写入本应用的审计记录；应用不存在返回 404，同一 requestId 只记录一次。
   *
   * @param userId 当前用户
   * @param systemCode 应用编码
   * @param write 审计内容
   * @return 写入或已存在的记录
   */
  AuditData.Entry writeAuditLog(Long userId, String systemCode, AuditData.Write write);
}
