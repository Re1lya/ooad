package com.cdwy.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** FR-23：DDD 配置复用全部 RBAC1 HTTP 业务验收，不更改 API 与数据库。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ddd_rbac1_tests;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3",
      "app.backend-strategy=ddd"
    })
class DddRbac1Tests extends PhaseTwoRbac1Tests {
  /** 页面和运维能读到真实装载的实现模式。 */
  @Test
  void FR23_reportsSelectedImplementation() throws Exception {
    mvc.perform(get("/api/system-info").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.backendStrategy").value("ddd"));
  }
}
