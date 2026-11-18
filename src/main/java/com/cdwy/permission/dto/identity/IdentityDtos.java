package com.cdwy.permission.dto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 接入应用（课程管理系统等）调用的统一身份与审计接口的请求/响应。
 *
 * <p>字段名与课程管理系统 {@code UnifiedIdentityClient}、{@code UnifiedAuditClient} 的契约一致。
 */
public final class IdentityDtos {
  private IdentityDtos() {}

  /** GET /auth/me：当前用户、有效角色编码与应用内权限码（菜单、按钮、后端 API）。 */
  public record Current(String subject, List<String> roles, List<String> permissions) {}

  /** GET /identity/users/{userId}：统一身份用户。 */
  public record User(String userId, String no, String name) {}

  /** POST /auth/audit-logs：requestId 为幂等键。 */
  public record AuditWrite(
      @Size(max = 64) String requestId,
      @NotBlank @Size(max = 64) String type,
      @Size(max = 64) String objectType,
      @Size(max = 128) String objectId,
      @Size(max = 255) String target,
      Map<String, Object> before,
      Map<String, Object> after,
      @Size(max = 128) String termId,
      @Size(max = 128) String courseId,
      @Size(max = 128) String classId) {}

  /** 审计记录中的操作人快照。 */
  public record AuditActor(Long id, String name, List<String> roles, boolean deleted) {}

  /** 一条审计记录。 */
  public record AuditEntry(
      Long id,
      String systemCode,
      OffsetDateTime at,
      AuditActor actor,
      String type,
      String objectType,
      String objectId,
      String target,
      Map<String, Object> before,
      Map<String, Object> after,
      String termId,
      String courseId,
      String classId) {}

  /** 审计记录分页。 */
  public record AuditPage(int page, int pageSize, long total, List<AuditEntry> items) {}
}
