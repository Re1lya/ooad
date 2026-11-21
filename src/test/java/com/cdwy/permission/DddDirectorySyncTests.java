package com.cdwy.permission;

import org.springframework.boot.test.context.SpringBootTest;

/** FR-24 / FR-25：同一组员工信息同步验收在 DDD 模式下重跑，HTTP 契约与数据库不变。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ddd_directory_sync_tests;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=directory-sync-tests-signing-secret-32-bytes",
      "app.backend-strategy=ddd"
    })
class DddDirectorySyncTests extends DirectorySyncTests {}
