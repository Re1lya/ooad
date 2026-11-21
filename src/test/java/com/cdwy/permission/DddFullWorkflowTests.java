package com.cdwy.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** FR-26：全业务面向对象模式必须通过与结构化模式相同的 HTTP 验收。 */
@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:ddd_full_workflow;DB_CLOSE_DELAY=-1",
  "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
  "permission.policy.external-max-days=7", "permission.policy.temporary-max-days=3",
  "app.backend-strategy=ddd"
})
class DddFullWorkflowTests extends FirstReviewWorkflowTests {
  /** 防止仅切换角色图，却把整个系统误报为面向对象模式。 */
  @Test void FR26_reportsEverySelectedBusinessImplementation() throws Exception {
    for (String module : java.util.List.of("users", "departments", "systems", "roles",
        "permissions", "resources", "authorization", "hierarchy", "authentication", "directorySync")) {
      mvc.perform(get("/api/system-info").header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.modules." + module).value("ddd"));
    }
  }
}
