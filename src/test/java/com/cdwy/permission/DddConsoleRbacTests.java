package com.cdwy.permission;

import org.springframework.boot.test.context.SpringBootTest;

/** FR-38：权限系统自身的 RBAC 授权在 DDD 模式下重跑，HTTP 契约与数据库不变。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ddd_console_rbac_tests;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "app.backend-strategy=ddd"
    })
class DddConsoleRbacTests extends ConsoleRbacTests {}
