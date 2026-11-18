package com.cdwy.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** FR-27 ~ FR-30：同一组课程系统接入验收在 DDD 模式下重跑，HTTP 契约与数据库不变。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ddd_application_identity_tests;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3",
      "app.backend-strategy=ddd"
    })
class DddApplicationIdentityTests extends ApplicationIdentityTests {
  /** DDD 模式的应用服务日志标签。 */
  @Override
  String layer() {
    return "DDD-Application";
  }

  /** 接入应用身份模块报告实际装载的 DDD 实现。 */
  @Test
  void FR27_identityModuleReportsDdd() throws Exception {
    mvc.perform(get("/api/system-info").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules.identity").value("ddd"));
  }
}
