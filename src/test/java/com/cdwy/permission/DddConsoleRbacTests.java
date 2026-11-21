package com.cdwy.permission;

import com.cdwy.permission.support.RequestTransactionBoundary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** FR-38：权限系统自身的 RBAC 授权在 DDD 模式下重跑，HTTP 契约与数据库不变。 */
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(
    properties = {
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "app.backend-strategy=ddd"
    })
class DddConsoleRbacTests extends ConsoleRbacTests {}
