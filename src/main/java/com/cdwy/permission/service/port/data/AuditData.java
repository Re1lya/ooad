package com.cdwy.permission.service.port.data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 审计日志用例数据：字段与课程管理系统的统一身份审计接口契约一致。 */
public final class AuditData {
  private AuditData() {}

  /** 接入应用写入的一条审计记录；requestId 为幂等键。 */
  public record Write(
      String requestId,
      String type,
      String objectType,
      String objectId,
      String target,
      Map<String, Object> before,
      Map<String, Object> after,
      String termId,
      String courseId,
      String classId) {}

  /** 查询条件；page 从 1 开始。 */
  public record Query(
      String actor,
      Long actorId,
      String target,
      OffsetDateTime from,
      OffsetDateTime to,
      int page,
      int pageSize) {}

  /** 写入时的操作人快照。 */
  public record Actor(Long id, String name, List<String> roles, boolean deleted) {}

  /** 一条已保存的审计记录。 */
  public record Entry(
      Long id,
      String systemCode,
      OffsetDateTime at,
      Actor actor,
      String type,
      String objectType,
      String objectId,
      String target,
      Map<String, Object> before,
      Map<String, Object> after,
      String termId,
      String courseId,
      String classId) {}

  /** 分页结果。 */
  public record Page(int page, int pageSize, long total, List<Entry> items) {}
}
