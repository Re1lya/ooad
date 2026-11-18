package com.cdwy.permission.service.audit;

import com.cdwy.permission.entity.audit.AuditLog;
import com.cdwy.permission.entity.rbac.UserRole;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.audit.AuditLogRepository;
import com.cdwy.permission.repository.rbac.UserRoleRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.audit.AuditTrailUseCase;
import com.cdwy.permission.service.port.auth.CurrentActorProvider;
import com.cdwy.permission.service.port.data.AuditData;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 审计日志：两种设计模式共用的技术服务（与 JWT 签发、路径匹配同属横切能力，不进入核心 ER）。
 *
 * <p>接入应用以用户本人令牌写入，操作人姓名与角色在写入时做快照；同一应用内 requestId 幂等。
 */
@Service
@Transactional(readOnly = true)
public class AuditTrailService implements AuditTrailUseCase {
  private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);
  private static final int MAX_JSON = 4000;
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final AuditLogRepository logs;
  private final UserAccountRepository users;
  private final UserRoleRepository userRoles;
  private final CurrentActorProvider actors;
  private final JsonMapper json;
  private final ZoneId zone;

  public AuditTrailService(
      AuditLogRepository logs,
      UserAccountRepository users,
      UserRoleRepository userRoles,
      CurrentActorProvider actors,
      JsonMapper json,
      @Value("${permission.policy.zone:Asia/Shanghai}") String zone) {
    this.logs = logs;
    this.users = users;
    this.userRoles = userRoles;
    this.actors = actors;
    this.json = json;
    this.zone = ZoneId.of(zone);
  }

  @Override
  @Transactional
  public AuditData.Entry write(String systemCode, Long actorId, AuditData.Write input) {
    if (input.type() == null || input.type().isBlank())
      throw new IllegalArgumentException("审计类型不能为空");
    if (input.requestId() != null) {
      Optional<AuditLog> existing = logs.findBySystemCodeAndRequestId(systemCode, input.requestId());
      if (existing.isPresent()) {
        log.info("审计记录幂等命中：应用 {} requestId {}", systemCode, input.requestId());
        return entry(existing.get());
      }
    }
    AuditLog value = new AuditLog();
    value.setSystemCode(systemCode);
    value.setRequestId(limit(input.requestId(), 64, "requestId"));
    value.setOccurredAt(Instant.now());
    value.setType(limit(input.type(), 64, "type"));
    value.setObjectType(limit(input.objectType(), 64, "objectType"));
    value.setObjectId(limit(input.objectId(), 128, "objectId"));
    value.setTarget(limit(input.target(), 255, "target"));
    value.setBeforeJson(toJson(input.before()));
    value.setAfterJson(toJson(input.after()));
    value.setTermId(scope(input.termId(), "term", input));
    value.setCourseId(scope(input.courseId(), "course", input));
    value.setClassId(scope(input.classId(), "class", input));
    snapshotActor(value, actorId);
    AuditLog saved = logs.save(value);
    log.info(
        "写入审计记录：应用 {} 类型 {} 对象 {} 操作人 {}",
        systemCode,
        saved.getType(),
        saved.getObjectId(),
        saved.getActorName());
    return entry(saved);
  }

  @Override
  public AuditData.Page query(String systemCode, AuditData.Query query) {
    if (query.page() < 1 || query.pageSize() < 1 || query.pageSize() > 100)
      throw new IllegalArgumentException("page 从 1 开始，pageSize 取 1 到 100");
    Specification<AuditLog> filter =
        (root, criteria, builder) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(builder.equal(root.get("systemCode"), systemCode));
          if (query.actorId() != null)
            predicates.add(builder.equal(root.get("actorId"), query.actorId()));
          if (query.actor() != null && !query.actor().isBlank())
            predicates.add(
                builder.like(
                    builder.lower(root.get("actorName")), contains(query.actor()), '\\'));
          if (query.target() != null && !query.target().isBlank())
            predicates.add(
                builder.like(builder.lower(root.get("target")), contains(query.target()), '\\'));
          if (query.from() != null)
            predicates.add(
                builder.greaterThanOrEqualTo(root.get("occurredAt"), query.from().toInstant()));
          if (query.to() != null)
            predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), query.to().toInstant()));
          return builder.and(predicates.toArray(Predicate[]::new));
        };
    var page =
        logs.findAll(
            filter,
            PageRequest.of(
                query.page() - 1,
                query.pageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
    return new AuditData.Page(
        query.page(),
        query.pageSize(),
        page.getTotalElements(),
        page.getContent().stream().map(this::entry).toList());
  }

  @Override
  @Transactional
  public void record(
      String type,
      String objectType,
      String objectId,
      String target,
      Map<String, Object> before,
      Map<String, Object> after) {
    write(
        SELF_SYSTEM,
        actors.currentUserId().orElse(null),
        new AuditData.Write(
            UUID.randomUUID().toString(),
            type,
            objectType,
            objectId,
            target,
            before,
            after,
            null,
            null,
            null));
  }

  /** 操作人姓名和直接角色在写入时保存快照，之后改名或撤角色不改写历史。 */
  private void snapshotActor(AuditLog value, Long actorId) {
    value.setActorId(actorId);
    if (actorId == null) return;
    users.findById(actorId).map(UserAccount::getDisplayName).ifPresent(value::setActorName);
    String roles =
        String.join(
            ",",
            userRoles.findAllByUser_Id(actorId).stream()
                .map(UserRole::getRole)
                .map(com.cdwy.permission.entity.role.Role::getCode)
                .toList());
    value.setActorRoles(roles.length() > 255 ? roles.substring(0, 255) : roles);
  }

  private AuditData.Entry entry(AuditLog value) {
    AuditData.Actor actor =
        value.getActorId() == null
            ? null
            : new AuditData.Actor(
                value.getActorId(),
                value.getActorName(),
                value.getActorRoles() == null || value.getActorRoles().isBlank()
                    ? List.of()
                    : List.of(value.getActorRoles().split(",")),
                !users.existsById(value.getActorId()));
    return new AuditData.Entry(
        value.getId(),
        value.getSystemCode(),
        value.getOccurredAt().atZone(zone).toOffsetDateTime(),
        actor,
        value.getType(),
        value.getObjectType(),
        value.getObjectId(),
        value.getTarget(),
        fromJson(value.getBeforeJson()),
        fromJson(value.getAfterJson()),
        value.getTermId(),
        value.getCourseId(),
        value.getClassId());
  }

  /** 课程系统按 objectType 区分学期、课程、教学班；显式传入时优先使用显式值。 */
  private static String scope(String explicit, String type, AuditData.Write input) {
    if (explicit != null && !explicit.isBlank()) return limit(explicit, 128, type + "Id");
    return type.equals(input.objectType()) ? limit(input.objectId(), 128, "objectId") : null;
  }

  private static String limit(String value, int max, String field) {
    if (value != null && value.length() > max)
      throw new IllegalArgumentException(field + " 不能超过 " + max + " 个字符");
    return value;
  }

  private static String contains(String text) {
    String escaped =
        text.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private String toJson(Map<String, Object> value) {
    if (value == null) return null;
    String text = json.writeValueAsString(value);
    if (text.length() > MAX_JSON)
      throw new IllegalArgumentException("before/after 内容过长，不能超过 " + MAX_JSON + " 个字符");
    return text;
  }

  private Map<String, Object> fromJson(String text) {
    return text == null ? null : json.readValue(text, MAP);
  }
}
