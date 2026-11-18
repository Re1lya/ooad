package com.cdwy.permission.controller.identity;

import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.dto.identity.IdentityDtos;
import com.cdwy.permission.service.port.audit.AuditTrailUseCase;
import com.cdwy.permission.service.port.data.AuditData;
import com.cdwy.permission.service.port.error.ForbiddenException;
import com.cdwy.permission.service.port.identity.ApplicationIdentityUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 接入应用统一身份接口。接入应用把 {@code IDENTITY_BASE_URL} 配成 {@code http://<权限系统>/api/apps/<应用编码>}，
 * 即可按课程管理系统既有契约调用 /auth/me、/identity/users/{userId} 与 /auth/audit-logs。
 *
 * <p>所有接口都使用用户本人的 Bearer JWT；应用编码限定权限范围，接入应用不保存任何授权数据。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/apps/{systemCode}")
public class ApplicationIdentityController {
  private final ApplicationIdentityUseCase identity;
  private final AuditTrailUseCase audit;

  /** 当前用户在该应用内的有效角色与权限码；授权变更在下一次调用立即体现。 */
  @GetMapping("/auth/me")
  public IdentityDtos.Current me(@PathVariable String systemCode, HttpServletRequest request) {
    return IdentityMapping.view(identity.current(CurrentUser.from(request).id(), systemCode));
  }

  /** 按统一身份用户标识（登录名）查询姓名与工号，供接入应用登记教师等本地资料。 */
  @GetMapping("/identity/users/{userId}")
  public IdentityDtos.User user(@PathVariable String systemCode, @PathVariable String userId) {
    return IdentityMapping.view(identity.lookup(systemCode, userId));
  }

  /** 查询该应用的审计记录；调用者须为管理员或在该应用内至少拥有一项有效权限。 */
  @GetMapping("/auth/audit-logs")
  public IdentityDtos.AuditPage logs(
      @PathVariable String systemCode,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) Long actorId,
      @RequestParam(required = false) String target,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpServletRequest request) {
    CurrentUser user = CurrentUser.from(request);
    if (!user.administrator() && identity.current(user.id(), systemCode).permissions().isEmpty())
      throw new ForbiddenException("当前用户在该应用内没有任何有效权限，不能查看审计日志");
    return IdentityMapping.view(
        audit.query(
            systemCode, new AuditData.Query(actor, actorId, target, from, to, page, pageSize)));
  }

  /** 写入审计记录：操作人取令牌中的用户，应用不存在返回 404。 */
  @PostMapping("/auth/audit-logs")
  @ResponseStatus(HttpStatus.CREATED)
  public IdentityDtos.AuditEntry write(
      @PathVariable String systemCode,
      @Valid @RequestBody IdentityDtos.AuditWrite body,
      HttpServletRequest request) {
    Long userId = CurrentUser.from(request).id();
    identity.current(userId, systemCode);
    return IdentityMapping.view(audit.write(systemCode, userId, IdentityMapping.command(body)));
  }
}
