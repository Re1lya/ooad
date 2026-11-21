package com.cdwy.permission.controller.audit;

import com.cdwy.permission.controller.identity.IdentityMapping;
import com.cdwy.permission.dto.identity.IdentityDtos;
import com.cdwy.permission.service.port.audit.AuditTrailUseCase;
import com.cdwy.permission.service.port.data.AuditData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理员查看审计记录（本系统角色继承变更使用应用编码 rbac，接入应用使用各自编码）。 */
@RestController
@com.cdwy.permission.config.BusinessAccess(com.cdwy.permission.config.BusinessAccess.CONSOLE)
@RequiredArgsConstructor
@RequestMapping("/api/audit-logs")
public class AuditLogController {
  private final AuditTrailUseCase audit;

  @GetMapping
  public IdentityDtos.AuditPage list(
      @RequestParam(defaultValue = AuditTrailUseCase.SELF_SYSTEM) String system,
      @RequestParam(required = false) String target,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return IdentityMapping.view(
        audit.query(system, new AuditData.Query(null, null, target, null, null, page, pageSize)));
  }
}
