package com.cdwy.permission.repository.audit;

import com.cdwy.permission.entity.audit.AuditLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository
    extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
  Optional<AuditLog> findBySystemCodeAndRequestId(String systemCode, String requestId);
}
