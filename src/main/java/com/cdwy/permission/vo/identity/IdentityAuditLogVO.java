package com.cdwy.permission.vo.identity;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 一条审计记录。
 *
 * @param id 记录标识
 * @param systemCode 应用编码
 * @param at 发生时间
 * @param actor 操作人快照
 * @param type 操作类型
 * @param objectType 对象类型
 * @param objectId 对象标识
 * @param target 对象的可读名称
 * @param before 变更前快照
 * @param after 变更后快照
 * @param termId 所属学期
 * @param courseId 所属课程
 * @param classId 所属教学班
 */
public record IdentityAuditLogVO(
    Long id,
    String systemCode,
    OffsetDateTime at,
    IdentityAuditActorVO actor,
    String type,
    String objectType,
    String objectId,
    String target,
    Map<String, Object> before,
    Map<String, Object> after,
    String termId,
    String courseId,
    String classId) {}
