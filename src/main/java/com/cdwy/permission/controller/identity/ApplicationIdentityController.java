package com.cdwy.permission.controller.identity;

import com.cdwy.permission.config.AppAccess;
import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.dto.identity.IdentityDtos;
import com.cdwy.permission.service.port.audit.AuditTrailUseCase;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.data.AuditData;
import com.cdwy.permission.service.port.data.AuthData;
import com.cdwy.permission.service.port.data.IdentityData;
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
 * 接入应用统一身份接口。接入应用把统一身份基地址（课程系统的 {@code IDENTITY_BASE_URL} / {@code
 * VITE_IDENTITY_BASE_URL}）配成 {@code http://<权限系统>/api/apps/<应用编码>}，即可按其契约调用 /auth/... 接口。
 *
 * <ul>
 *   <li>登录不需要令牌，签发的令牌 {@code aud} 为请求中的 systems；其余接口要求令牌 {@code aud} 包含当前应用编码；
 *   <li>/auth/me、/auth/permissions、/auth/api-permissions 只看本人在本应用内的有效授权；
 *   <li>人员、部门目录接口另按本应用登记的 API 资源授权（{@link AppAccess}）；
 *   <li>接入应用不保存任何授权数据，授权变更在下一次调用立即体现。
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/apps/{systemCode}")
public class ApplicationIdentityController {
  private final ApplicationIdentityUseCase identity;
  private final AuthenticationUseCase auth;
  private final AuditTrailUseCase audit;

  /** 用学号或工号与密码登录；密码只用于校验，不落日志。 */
  @PostMapping("/auth/login")
  public IdentityDtos.LoginResult login(
      @PathVariable String systemCode, @Valid @RequestBody IdentityDtos.Login body) {
    AuthData.Token token =
        auth.loginTo(
            new AuthData.AppLogin(body.username(), body.password(), body.systems(), systemCode));
    return new IdentityDtos.LoginResult(token.accessToken(), token.tokenType(), token.expiresIn());
  }

  /** 退出登录：本人已签发的令牌全部失效（本系统不保存令牌，靠凭证版本实现）。 */
  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request) {
    auth.logout(CurrentUser.from(request).id());
  }

  /** 当前用户在该应用内的有效角色与权限码；授权变更在下一次调用立即体现。 */
  @GetMapping("/auth/me")
  public IdentityDtos.Current me(@PathVariable String systemCode, HttpServletRequest request) {
    return IdentityMapping.view(identity.current(CurrentUser.from(request).id(), systemCode));
  }

  /** 是否拥有指定的前端菜单或按钮权限码；接入应用前端每次切换路由都会调用。 */
  @GetMapping("/auth/permissions/{permission}")
  public IdentityDtos.PermissionCheck permission(
      @PathVariable String systemCode,
      @PathVariable String permission,
      HttpServletRequest request) {
    return new IdentityDtos.PermissionCheck(
        identity.permitted(CurrentUser.from(request).id(), systemCode, permission));
  }

  /** 能否调用 operationId 对应的接口。 */
  @GetMapping("/auth/api-permissions/{operationId}")
  public IdentityDtos.ApiCheck api(
      @PathVariable String systemCode,
      @PathVariable String operationId,
      HttpServletRequest request) {
    IdentityData.ApiCheck check =
        identity.apiPermission(CurrentUser.from(request).id(), systemCode, operationId);
    return new IdentityDtos.ApiCheck(check.operationId(), check.allowed());
  }

  /** 逐层查询部门（不传 parentId 为根部门）。 */
  @AppAccess
  @GetMapping("/auth/departments")
  public IdentityDtos.Page<IdentityDtos.Department> departments(
      @PathVariable String systemCode,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long parentId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    if (parentId != null && parentId < 1) throw new IllegalArgumentException("parentId 须大于等于 1");
    return IdentityMapping.departments(
        identity.departments(
            systemCode, parentId, new IdentityData.Query(keyword, page, pageSize)));
  }

  /** 查询部门下的用户（按工号、学号或姓名）。 */
  @AppAccess
  @GetMapping("/auth/departments/{departmentId}/users")
  public IdentityDtos.Page<IdentityDtos.DepartmentUser> departmentUsers(
      @PathVariable String systemCode,
      @PathVariable Long departmentId,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return IdentityMapping.departmentUsers(
        identity.departmentUsers(
            systemCode, departmentId, new IdentityData.Query(keyword, page, pageSize)));
  }

  /** 按统一身份用户标识（登录名）查询姓名与工号，供接入应用登记教师等本地资料。 */
  @AppAccess
  @GetMapping("/auth/users/{userId}")
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
