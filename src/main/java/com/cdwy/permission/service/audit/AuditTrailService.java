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
import com.cdwy.permission.service.port.error.InvalidDataException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
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
  /** 审计对象描述的列宽。 */
  private static final int TARGET_LENGTH = 255;

  private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);
  /** before/after 序列化为 JSON 后允许的最大字符数，与列宽一致。 */
  private static final int MAX_JSON = 4000;
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final AuditLogRepository logs;
  private final UserAccountRepository users;
  private final UserRoleRepository userRoles;
  private final CurrentActorProvider actors;
  private final JsonMapper json;
  private final ZoneId zone;

  /** {@code zone} 取 {@code permission.policy.zone}（默认 Asia/Shanghai），只用于把记录时间换算为带时区偏移的结果。 */
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

  /**
   * 接口约定之外的规则：requestId 为空时不做幂等；对象描述超过 255 字时截断并以“…”结尾；before/after 序列化后超过 4000 字符抛
   * {@code AUDIT_SNAPSHOT_TOO_LARGE}（400）；termId、courseId、classId 未显式给出时，objectType 为 term、course、class 的记录取
   * objectId 填入对应字段；操作人姓名与直接角色编码在写入时快照，操作人为空时不记快照。
   */
  @Override
  @Transactional
  public AuditData.Entry write(String systemCode, Long actorId, AuditData.Write input) {
    if (input.requestId() != null) {
      Optional<AuditLog> existing = logs.findBySystemCodeAndRequestId(systemCode, input.requestId());
      if (existing.isPresent()) {
        log.info("审计记录幂等命中：应用 {} requestId {}", systemCode, input.requestId());
        return entry(existing.get());
      }
    }
    AuditLog value = new AuditLog();
    value.setSystemCode(systemCode);
    value.setRequestId(input.requestId());
    value.setOccurredAt(Instant.now());
    value.setType(input.type());
    value.setObjectType(input.objectType());
    value.setObjectId(input.objectId());
    value.setTarget(fit(input.target(), TARGET_LENGTH));
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

  /**
   * actor 按操作人姓名、target 按对象描述做不区分大小写的包含匹配（转义 LIKE 通配符），actorId 精确匹配，from、to 为闭区间；
   * 按时间、标识倒序。page 从 1 开始，由 API 层保证合法。
   */
  @Override
  public AuditData.Page query(String systemCode, AuditData.Query query) {
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
    Page<AuditLog> page =
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

  /** 以应用 rbac 写入；requestId 每次随机生成，因此不去重；非 HTTP 调用或未登录时操作人为空。目前由角色继承的建立与解除调用。 */
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

  /** 转换为用例结果：时间换算到项目时区，角色快照按逗号拆分；操作人账号已被删除时 {@code deleted} 为 true（逐条查询账号）。 */
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
    if (explicit != null && !explicit.isBlank()) return explicit;
    return type.equals(input.objectType()) ? input.objectId() : null;
  }

  /**
   * 对象描述按列宽截断。接入应用的请求已由 API 层限制长度；内部写入（例如“角色 A 继承 角色 B”）由两个最长
   * 128 字的角色名拼成，可能超出列宽，截断后仍可读。
   */
  private static String fit(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
  }

  /** 构造不区分大小写的包含匹配模式：先转小写，再用反斜杠转义 LIKE 的通配符 {@code %}、{@code _} 和反斜杠本身。 */
  private static String contains(String text) {
    String escaped =
        text.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }

  /** 序列化快照；超过 {@link #MAX_JSON} 个字符抛 {@code AUDIT_SNAPSHOT_TOO_LARGE}（400），不截断 JSON。 */
  private String toJson(Map<String, Object> value) {
    if (value == null) return null;
    String text = json.writeValueAsString(value);
    if (text.length() > MAX_JSON)
      throw new InvalidDataException("AUDIT_SNAPSHOT_TOO_LARGE", "before/after 内容过长，不能超过 " + MAX_JSON + " 个字符");
    return text;
  }

  private Map<String, Object> fromJson(String text) {
    return text == null ? null : json.readValue(text, MAP);
  }
}
