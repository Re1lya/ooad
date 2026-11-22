package com.cdwy.permission.repository.audit;

import com.cdwy.permission.entity.audit.AuditLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 审计日志仓储：接入应用的操作记录与本系统的角色继承变更，供审计服务写入；组合条件分页查询走 {@code JpaSpecificationExecutor}。 */
public interface AuditLogRepository
    extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
  /**
   * 按应用编码和 requestId 精确查找（区分大小写），用于同一应用内的幂等写入；空表示该请求尚未记录。
   * requestId 为空时不应调用：空值按 {@code is null} 匹配，会命中该应用所有未带 requestId 的记录。
   */
  Optional<AuditLog> findBySystemCodeAndRequestId(String systemCode, String requestId);
}
