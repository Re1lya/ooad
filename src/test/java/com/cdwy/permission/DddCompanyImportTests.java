package com.cdwy.permission;

import org.springframework.boot.test.context.SpringBootTest;

/** FR-24/26：同一组真实 HTTP 导入验收在面向对象模式重跑，含冲突全回滚及撤权。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ddd_company_import;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=company-import-tests-signing-secret-32-bytes",
      "app.backend-strategy=ddd"
    })
class DddCompanyImportTests extends CompanyImportTests {}
