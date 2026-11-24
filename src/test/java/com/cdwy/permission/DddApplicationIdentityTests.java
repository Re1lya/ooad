package com.cdwy.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.support.RequestTransactionBoundary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** FR-27 ~ FR-37：同一组课程系统接入验收在 DDD 模式下重跑，HTTP 契约与数据库不变。 */
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(
    properties = {
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3",
      "permission.cors.allowed-origins=http://course.example, http://127.0.0.1:5173",
      "app.backend-strategy=ddd"
    })
class DddApplicationIdentityTests extends ApplicationIdentityTests {
  private static final String SYSTEM_INFO_URL = "/api/system-info";

  /** DDD 模式的应用服务日志标签。 */
  @Override
  String layer() {
    return "DDD-Application";
  }

  /** 接入应用身份模块报告实际装载的 DDD 实现。 */
  @Test
  @Tag("等价类")
  @DisplayName("FR-27 DDD 模式下统一身份模块报告 ddd 实现")
  void FR27_identityModuleReportsDdd() throws Exception {
    mvc.perform(get(SYSTEM_INFO_URL).header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules.identity").value("ddd"));
  }
}
