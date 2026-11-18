package com.cdwy.permission.entity.audit;

import jakarta.persistence.*;
import java.time.Instant;

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
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "system_code", nullable = false, length = 64)
  private String systemCode;

  @Column(name = "request_id", length = 64)
  private String requestId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(name = "actor_name", length = 128)
  private String actorName;

  @Column(name = "actor_roles", length = 255)
  private String actorRoles;

  @Column(nullable = false, length = 64)
  private String type;

  @Column(name = "object_type", length = 64)
  private String objectType;

  @Column(name = "object_id", length = 128)
  private String objectId;

  @Column(length = 255)
  private String target;

  @Column(name = "before_json", length = 4000)
  private String beforeJson;

  @Column(name = "after_json", length = 4000)
  private String afterJson;

  @Column(name = "term_id", length = 128)
  private String termId;

  @Column(name = "course_id", length = 128)
  private String courseId;

  @Column(name = "class_id", length = 128)
  private String classId;

  public AuditLog() {}

  public Long getId() {
    return id;
  }

  public String getSystemCode() {
    return systemCode;
  }

  public void setSystemCode(String value) {
    systemCode = value;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String value) {
    requestId = value;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant value) {
    occurredAt = value;
  }

  public Long getActorId() {
    return actorId;
  }

  public void setActorId(Long value) {
    actorId = value;
  }

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    actorName = value;
  }

  public String getActorRoles() {
    return actorRoles;
  }

  public void setActorRoles(String value) {
    actorRoles = value;
  }

  public String getType() {
    return type;
  }

  public void setType(String value) {
    type = value;
  }

  public String getObjectType() {
    return objectType;
  }

  public void setObjectType(String value) {
    objectType = value;
  }

  public String getObjectId() {
    return objectId;
  }

  public void setObjectId(String value) {
    objectId = value;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String value) {
    target = value;
  }

  public String getBeforeJson() {
    return beforeJson;
  }

  public void setBeforeJson(String value) {
    beforeJson = value;
  }

  public String getAfterJson() {
    return afterJson;
  }

  public void setAfterJson(String value) {
    afterJson = value;
  }

  public String getTermId() {
    return termId;
  }

  public void setTermId(String value) {
    termId = value;
  }

  public String getCourseId() {
    return courseId;
  }

  public void setCourseId(String value) {
    courseId = value;
  }

  public String getClassId() {
    return classId;
  }

  public void setClassId(String value) {
    classId = value;
  }
}
