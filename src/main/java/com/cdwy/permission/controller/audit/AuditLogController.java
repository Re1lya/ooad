package com.cdwy.permission.controller.audit;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.controller.identity.IdentityVoMapper;
import com.cdwy.permission.service.port.audit.AuditTrailUseCase;
import com.cdwy.permission.service.port.data.AuditData;
import com.cdwy.permission.vo.common.PageVO;
import com.cdwy.permission.vo.identity.IdentityAuditLogVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员查看审计记录：本系统的角色继承变更用应用编码 rbac，接入应用写入的记录用各自的应用编码。 */
@RestController
@BusinessAccess(BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/audit-logs")
public class AuditLogController {
  private final AuditTrailUseCase audit;
  private final IdentityVoMapper mapper;

  /** 按应用与操作对象分页查询，时间倒序，页码从 1 开始。 */
  @GetMapping
  public PageVO<IdentityAuditLogVO> list(
      @RequestParam(defaultValue = AuditTrailUseCase.SELF_SYSTEM)
          @Size(max = 64, message = "最长 64 个字符")
          String system,
      @RequestParam(required = false) @Size(max = 255, message = "最长 255 个字符") String target,
      @RequestParam(defaultValue = "1") @Min(value = 1, message = "须大于等于 1") int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "须在 1 到 100 之间")
          @Max(value = 100, message = "须在 1 到 100 之间")
          int pageSize) {
    return mapper.toVO(
        audit.query(system, new AuditData.Query(null, null, target, null, null, page, pageSize)));
  }
}
