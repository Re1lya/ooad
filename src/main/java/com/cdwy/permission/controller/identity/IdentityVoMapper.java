package com.cdwy.permission.controller.identity;

import com.cdwy.permission.dto.identity.IdentityAuditWriteDTO;
import com.cdwy.permission.dto.identity.IdentityLoginDTO;
import com.cdwy.permission.service.port.data.AuditData;
import com.cdwy.permission.service.port.data.AuthData;
import com.cdwy.permission.service.port.data.IdentityData;
import com.cdwy.permission.vo.common.PageVO;
import com.cdwy.permission.vo.identity.ApiAuthorityCheckVO;
import com.cdwy.permission.vo.identity.IdentityAuditActorVO;
import com.cdwy.permission.vo.identity.IdentityAuditLogVO;
import com.cdwy.permission.vo.identity.IdentityCurrentUserVO;
import com.cdwy.permission.vo.identity.IdentityDepartmentUserVO;
import com.cdwy.permission.vo.identity.IdentityDepartmentVO;
import com.cdwy.permission.vo.identity.IdentityUserVO;
import com.cdwy.permission.vo.identity.LoginVO;
import com.cdwy.permission.vo.identity.PermissionCheckVO;
import org.springframework.stereotype.Component;

/**
 * 统一身份与审计接口的 DTO → 命令、结果 → VO 映射。
 *
 * <p>VO 的字段名与课程项目管理系统的统一身份契约（Apifox「统一身份鉴权」「统一身份审计」）一致。
 */
@Component
public class IdentityVoMapper {
  /** 接入应用的登录请求转为登录命令；target 为接口地址中的应用编码。 */
  public AuthData.AppLogin toCommand(IdentityLoginDTO dto, String target) {
    return new AuthData.AppLogin(dto.username(), dto.password(), dto.systems(), target);
  }

  /** 审计写入请求转为命令。 */
  public AuditData.Write toCommand(IdentityAuditWriteDTO dto) {
    return new AuditData.Write(
        dto.requestId(),
        dto.type(),
        dto.objectType(),
        dto.objectId(),
        dto.target(),
        dto.before(),
        dto.after(),
        dto.termId(),
        dto.courseId(),
        dto.classId());
  }

  /** 签发结果转为登录 VO，不含用户资料。 */
  public LoginVO toVO(AuthData.Token token) {
    return new LoginVO(token.accessToken(), token.tokenType(), token.expiresIn());
  }

  /** 当前身份转为 VO。 */
  public IdentityCurrentUserVO toVO(IdentityData.Current current) {
    return new IdentityCurrentUserVO(
        current.subject(), current.roles(), current.permissions(), current.name(), current.no());
  }

  /** 统一身份用户转为 VO。 */
  public IdentityUserVO toVO(IdentityData.User user) {
    return new IdentityUserVO(user.userId(), user.no(), user.name());
  }

  /** 菜单或按钮权限的判断结果转为 VO。 */
  public PermissionCheckVO toPermissionCheckVO(boolean allowed) {
    return new PermissionCheckVO(allowed);
  }

  /** 接口权限的判断结果转为 VO。 */
  public ApiAuthorityCheckVO toVO(IdentityData.ApiCheck check) {
    return new ApiAuthorityCheckVO(check.operationId(), check.allowed());
  }

  /** 一层部门的分页结果转为 VO。 */
  public PageVO<IdentityDepartmentVO> toDepartmentPageVO(
      IdentityData.Page<IdentityData.Department> page) {
    return new PageVO<>(
        page.page(),
        page.pageSize(),
        page.total(),
        page.items().stream()
            .map(d -> new IdentityDepartmentVO(d.id(), d.code(), d.name(), d.parentId(), d.hasChildren()))
            .toList());
  }

  /** 部门人员的分页结果转为 VO。 */
  public PageVO<IdentityDepartmentUserVO> toDepartmentUserPageVO(
      IdentityData.Page<IdentityData.DepartmentUser> page) {
    return new PageVO<>(
        page.page(),
        page.pageSize(),
        page.total(),
        page.items().stream()
            .map(
                u ->
                    new IdentityDepartmentUserVO(
                        u.userId(), u.no(), u.name(), u.departmentId(), u.departmentName()))
            .toList());
  }

  /** 一条审计记录转为 VO。 */
  public IdentityAuditLogVO toVO(AuditData.Entry entry) {
    IdentityAuditActorVO actor =
        entry.actor() == null
            ? null
            : new IdentityAuditActorVO(
                entry.actor().id(),
                entry.actor().name(),
                entry.actor().roles(),
                entry.actor().deleted());
    return new IdentityAuditLogVO(
        entry.id(),
        entry.systemCode(),
        entry.at(),
        actor,
        entry.type(),
        entry.objectType(),
        entry.objectId(),
        entry.target(),
        entry.before(),
        entry.after(),
        entry.termId(),
        entry.courseId(),
        entry.classId());
  }

  /** 审计记录分页结果转为 VO。 */
  public PageVO<IdentityAuditLogVO> toVO(AuditData.Page page) {
    return new PageVO<>(
        page.page(), page.pageSize(), page.total(), page.items().stream().map(this::toVO).toList());
  }
}
