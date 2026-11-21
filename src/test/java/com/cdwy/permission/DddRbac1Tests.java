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

/** FR-23：DDD 配置复用全部 RBAC1 HTTP 业务验收，不更改 API 与数据库。 */
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
class DddRbac1Tests extends PhaseTwoRbac1Tests {
  private static final String SYSTEM_INFO_URL = "/api/system-info";

  /** 页面和运维能读到真实装载的实现模式。 */
  @Test
  @DisplayName("FR-23 DDD 模式下角色继承模块报告 ddd 实现")
  void FR23_reportsSelectedImplementation() throws Exception {
    mvc.perform(get(SYSTEM_INFO_URL).header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.backendStrategy").value("ddd"));
  }
}
