package com.cdwy.permission;

import com.cdwy.permission.support.RequestTransactionBoundary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 控制台需求的等价类、边界值与异常测试在 DDD 模式下重跑，HTTP 契约与数据库不变。 */
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(
    properties = {
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3",
      "app.backend-strategy=ddd"
    })
class DddConsoleRequirementTests extends ConsoleRequirementTests {
  /** 本类配置了 app.backend-strategy=ddd。 */
  @Override
  String strategy() {
    return "ddd";
  }
}
