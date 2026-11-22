package com.cdwy.permission.dto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 接入应用写入审计记录的请求（POST /auth/audit-logs）。
 *
 * <p>同一应用内 requestId 相同的重复请求只记录一次；termId、courseId、classId 未填时按 objectType 从 objectId 推导。
 *
 * @param requestId 幂等键
 * @param type 操作类型，例如 ROSTER_CHANGED
 * @param objectType 对象类型，例如 course、class
 * @param objectId 对象标识
 * @param target 对象的可读名称
 * @param before 变更前快照
 * @param after 变更后快照
 * @param termId 所属学期
 * @param courseId 所属课程
 * @param classId 所属教学班
 */
public record IdentityAuditWriteDTO(
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
