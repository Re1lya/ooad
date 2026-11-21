package com.cdwy.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.support.RequestTransactionBoundary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** FR-26：全业务面向对象模式必须通过与结构化模式相同的 HTTP 验收。 */
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(properties = {
  "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
  "permission.policy.external-max-days=7", "permission.policy.temporary-max-days=3",
  "app.backend-strategy=ddd"
})
class DddFullWorkflowTests extends FirstReviewWorkflowTests {
  private static final String SYSTEM_INFO_URL = "/api/system-info";

  /** 防止仅切换角色图，却把整个系统误报为面向对象模式。 */
  @Test @DisplayName("FR-26 DDD 模式下每个业务模块都报告 ddd 实现")
 void FR26_reportsEverySelectedBusinessImplementation() throws Exception {
    for (String module : java.util.List.of("users", "departments", "systems", "roles",
        "permissions", "resources", "authorization", "hierarchy", "authentication", "directorySync")) {
      mvc.perform(get(SYSTEM_INFO_URL).header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.modules." + module).value("ddd"));
    }
  }
}
