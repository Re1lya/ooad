package com.cdwy.permission.controller.identity;

import com.cdwy.permission.config.AppAccess;
import com.cdwy.permission.config.CurrentUser;
import com.cdwy.permission.dto.identity.IdentityAuditWriteDTO;
import com.cdwy.permission.dto.identity.IdentityLoginDTO;
import com.cdwy.permission.service.port.auth.AuthenticationUseCase;
import com.cdwy.permission.service.port.data.AuditData;
import com.cdwy.permission.service.port.data.IdentityData;
import com.cdwy.permission.service.port.identity.ApplicationIdentityUseCase;
import com.cdwy.permission.vo.common.PageVO;
import com.cdwy.permission.vo.identity.ApiAuthorityCheckVO;
import com.cdwy.permission.vo.identity.IdentityAuditLogVO;
import com.cdwy.permission.vo.identity.IdentityCurrentUserVO;
import com.cdwy.permission.vo.identity.IdentityDepartmentUserVO;
import com.cdwy.permission.vo.identity.IdentityDepartmentVO;
import com.cdwy.permission.vo.identity.IdentityUserVO;
import com.cdwy.permission.vo.identity.LoginVO;
import com.cdwy.permission.vo.identity.PermissionCheckVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
  private final IdentityVoMapper mapper;

  /** 用学号或工号与密码登录；密码只用于校验，不落日志。 */
  @PostMapping("/auth/login")
  public LoginVO login(@PathVariable String systemCode, @Valid @RequestBody IdentityLoginDTO body) {
    return mapper.toVO(auth.loginTo(mapper.toCommand(body, systemCode)));
  }

  /** 退出登录：本人已签发的令牌全部失效（本系统不保存令牌，靠凭证版本实现）。 */
  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request) {
    auth.logout(CurrentUser.from(request).id());
  }

  /** 当前用户在该应用内的有效角色与权限码；授权变更在下一次调用立即体现。 */
  @GetMapping("/auth/me")
  public IdentityCurrentUserVO me(@PathVariable String systemCode, HttpServletRequest request) {
    return mapper.toVO(identity.current(CurrentUser.from(request).id(), systemCode));
  }

  /** 是否拥有指定的前端菜单或按钮权限码；接入应用前端每次切换路由都会调用。 */
  @GetMapping("/auth/permissions/{permission}")
  public PermissionCheckVO permission(
      @PathVariable String systemCode,
      @PathVariable @Size(max = 128, message = "最长 128 个字符") String permission,
      HttpServletRequest request) {
    return mapper.toPermissionCheckVO(
        identity.permitted(CurrentUser.from(request).id(), systemCode, permission));
  }

  /** 能否调用 operationId 对应的接口。 */
  @GetMapping("/auth/api-permissions/{operationId}")
  public ApiAuthorityCheckVO api(
      @PathVariable String systemCode,
      @PathVariable @Size(max = 128, message = "最长 128 个字符") String operationId,
      HttpServletRequest request) {
    return mapper.toVO(
        identity.apiPermission(CurrentUser.from(request).id(), systemCode, operationId));
  }

  /** 逐层查询部门（不传 parentId 为根部门）。 */
  @AppAccess
  @GetMapping("/auth/departments")
  public PageVO<IdentityDepartmentVO> departments(
      @PathVariable String systemCode,
      @RequestParam(required = false) @Size(max = 100, message = "最长 100 个字符") String keyword,
      @RequestParam(required = false) @Min(value = 1, message = "须大于等于 1") Long parentId,
      @RequestParam(defaultValue = "1") @Min(value = 1, message = "须大于等于 1") int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "须在 1 到 100 之间")
          @Max(value = 100, message = "须在 1 到 100 之间")
          int pageSize) {
    return mapper.toDepartmentPageVO(
        identity.departments(systemCode, parentId, new IdentityData.Query(keyword, page, pageSize)));
  }

  /** 查询部门下的用户（按工号、学号或姓名）。 */
  @AppAccess
  @GetMapping("/auth/departments/{departmentId}/users")
  public PageVO<IdentityDepartmentUserVO> departmentUsers(
      @PathVariable String systemCode,
      @PathVariable Long departmentId,
      @RequestParam(required = false) @Size(max = 100, message = "最长 100 个字符") String keyword,
      @RequestParam(defaultValue = "1") @Min(value = 1, message = "须大于等于 1") int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "须在 1 到 100 之间")
          @Max(value = 100, message = "须在 1 到 100 之间")
          int pageSize) {
    return mapper.toDepartmentUserPageVO(
        identity.departmentUsers(
            systemCode, departmentId, new IdentityData.Query(keyword, page, pageSize)));
  }

  /** 按统一身份用户标识（工号或学号）查询姓名，供接入应用登记教师等本地资料。 */
  @AppAccess
  @GetMapping("/auth/users/{userId}")
  public IdentityUserVO user(
      @PathVariable String systemCode,
      @PathVariable @Size(max = 64, message = "最长 64 个字符") String userId) {
    return mapper.toVO(identity.lookup(systemCode, userId));
  }

  /** 查询该应用的审计记录；调用者须为管理员或在该应用内至少拥有一项有效权限。 */
  @GetMapping("/auth/audit-logs")
  public PageVO<IdentityAuditLogVO> logs(
      @PathVariable String systemCode,
      @RequestParam(required = false) @Size(max = 128, message = "最长 128 个字符") String actor,
      @RequestParam(required = false) Long actorId,
      @RequestParam(required = false) @Size(max = 255, message = "最长 255 个字符") String target,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      @RequestParam(defaultValue = "1") @Min(value = 1, message = "须大于等于 1") int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "须在 1 到 100 之间")
          @Max(value = 100, message = "须在 1 到 100 之间")
          int pageSize,
      HttpServletRequest request) {
    CurrentUser user = CurrentUser.from(request);
    return mapper.toVO(
        identity.auditLogs(
            user.id(),
            user.administrator(),
            systemCode,
            new AuditData.Query(actor, actorId, target, from, to, page, pageSize)));
  }

  /** 写入审计记录：操作人取令牌中的用户，应用不存在返回 404。 */
  @PostMapping("/auth/audit-logs")
  @ResponseStatus(HttpStatus.CREATED)
  public IdentityAuditLogVO write(
      @PathVariable String systemCode,
      @Valid @RequestBody IdentityAuditWriteDTO body,
      HttpServletRequest request) {
    return mapper.toVO(
        identity.writeAuditLog(CurrentUser.from(request).id(), systemCode, mapper.toCommand(body)));
  }
}
