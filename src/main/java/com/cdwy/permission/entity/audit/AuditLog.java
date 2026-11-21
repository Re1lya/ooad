package com.cdwy.permission.entity.audit;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 审计日志（技术记录，不属于核心业务 ER）：接入应用写入的关键操作与本系统的角色继承变更。
 *
 * <p>不与用户、应用建立外键：操作人与应用以快照字段保存，删除账号或应用后日志仍可追溯。
 */
@Entity
@Table(
    name = "sys_audit_log",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_audit_request",
            columnNames = {"system_code", "request_id"}))
@Getter
@Setter
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  /** 写入日志的应用编码；本系统自身的记录为 {@code rbac}。 */
  @Column(name = "system_code", nullable = false, length = 64)
  private String systemCode;

  /** 写入方提供的请求号，可为空；同一应用内唯一，重复提交时直接返回已有记录。 */
  @Column(name = "request_id", length = 64)
  private String requestId;

  /** 服务端写入时刻。 */
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  /** 操作人用户 ID 快照，不建外键；账号删除后仍保留。 */
  @Column(name = "actor_id")
  private Long actorId;

  /** 写入时操作人姓名的快照，之后改名不改写历史。 */
  @Column(name = "actor_name", length = 128)
  private String actorName;

  /** 写入时操作人直接角色编码的快照，逗号分隔，超过 255 个字符截断。 */
  @Column(name = "actor_roles", length = 255)
  private String actorRoles;

  /** 操作类型，由写入方定义，例如 {@code ROLE_INHERITANCE_ADDED}。 */
  @Column(nullable = false, length = 64)
  private String type;

  /** 被操作对象的类型，由写入方定义，例如 {@code role}、{@code course}。 */
  @Column(name = "object_type", length = 64)
  private String objectType;

  /** 被操作对象在写入方系统中的标识。 */
  @Column(name = "object_id", length = 128)
  private String objectId;

  /** 对象描述，超过 255 个字符时截断并以省略号结尾。 */
  @Column(length = 255)
  private String target;

  /** 变更前快照的 JSON 文本；超过 4000 个字符时拒绝写入。 */
  @Column(name = "before_json", length = 4000)
  private String beforeJson;

  /** 变更后快照的 JSON 文本；超过 4000 个字符时拒绝写入。 */
  @Column(name = "after_json", length = 4000)
  private String afterJson;

  /** 课程系统的学期标识；未显式传入且对象类型为 {@code term} 时取对象标识。 */
  @Column(name = "term_id", length = 128)
  private String termId;

  /** 课程标识；未显式传入且对象类型为 {@code course} 时取对象标识。 */
  @Column(name = "course_id", length = 128)
  private String courseId;

  /** 教学班标识；未显式传入且对象类型为 {@code class} 时取对象标识。 */
  @Column(name = "class_id", length = 128)
  private String classId;

}
