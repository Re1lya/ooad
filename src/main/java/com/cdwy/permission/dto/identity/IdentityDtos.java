package com.cdwy.permission.dto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 接入应用（课程管理系统等）调用的统一身份与审计接口的请求/响应。
 *
 * <p>字段名与课程管理系统 {@code UnifiedIdentityClient}、{@code UnifiedAuditClient} 及其前端 api 模块的契约一致。
 */
public final class IdentityDtos {
  private IdentityDtos() {}

  /** POST /auth/login：systems 是令牌目标系统，必须包含当前应用编码。 */
  public record Login(
      @NotBlank @Size(max = 128) String username,
      @NotBlank @Size(max = 256) String password,
      @NotEmpty List<@NotBlank String> systems) {}

  /** POST /auth/login 的响应：不含用户资料，角色与权限由 /auth/me 查询。 */
  public record LoginResult(String accessToken, String tokenType, long expiresIn) {}

  /** GET /auth/me：当前用户、有效角色编码与应用内权限码（菜单、按钮、后端 API）。 */
  public record Current(
      String subject, List<String> roles, List<String> permissions, String name, String no) {}

  /** GET /auth/users/{userId}：统一身份用户。 */
  public record User(String userId, String no, String name) {}

  /** GET /auth/permissions/{permission}：只返回判断结果。 */
  public record PermissionCheck(boolean allowed) {}

  /** GET /auth/api-permissions/{operationId}：只返回判断结果，不返回权限码。 */
  public record ApiCheck(String operationId, boolean allowed) {}

  /** GET /auth/departments 的一项。 */
  public record Department(Long id, String code, String name, Long parentId, boolean hasChildren) {}

  /** GET /auth/departments/{departmentId}/users 的一项，不含电话等联系方式。 */
  public record DepartmentUser(
      String userId, String no, String name, Long departmentId, String departmentName) {}

  /** 页码从 1 开始的分页。 */
  public record Page<T>(int page, int pageSize, long total, List<T> items) {}

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
