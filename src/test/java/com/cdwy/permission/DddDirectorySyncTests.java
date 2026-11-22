package com.cdwy.permission;

import com.cdwy.permission.support.RequestTransactionBoundary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** FR-24 / FR-25：同一组员工信息同步验收在 DDD 模式下重跑，HTTP 契约与数据库不变。 */
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(
    properties = {
      "permission.jwt.secret=directory-sync-tests-signing-secret-32-bytes",
      "app.backend-strategy=ddd"
    })
class DddDirectorySyncTests extends DirectorySyncTests {}
