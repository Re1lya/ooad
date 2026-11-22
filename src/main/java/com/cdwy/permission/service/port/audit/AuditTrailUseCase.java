package com.cdwy.permission.service.port.audit;

import com.cdwy.permission.service.port.data.AuditData;
import java.util.Map;

/**
 * 审计日志端口：两种设计模式共用的横切技术能力（与 JWT、路径匹配一样不属于 RBAC 业务模型）。
 *
 * <p>接入应用经 /api/apps/{code}/auth/audit-logs 写入与查询；本系统的角色继承变更通过 {@link #record} 写入。
 */
public interface AuditTrailUseCase {
  /** 本系统自身事件使用的应用编码。 */
  String SELF_SYSTEM = "rbac";

  /** 写入接入应用的审计记录；同一应用内 requestId 重复时返回已有记录。 */
  AuditData.Entry write(String systemCode, Long actorId, AuditData.Write input);

  /** 按应用查询审计记录，时间倒序分页。 */
  AuditData.Page query(String systemCode, AuditData.Query query);

  /** 记录本系统事件，操作人取当前请求的登录用户。 */
  void record(
      String type,
      String objectType,
      String objectId,
      String target,
      Map<String, Object> before,
      Map<String, Object> after);
}
