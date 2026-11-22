package com.cdwy.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.port.error.*;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-26：无需 Spring 的领域规则和架构边界回归，不伪装真实 MySQL 验证。 */
class DualDesignBoundaryTests {
  /** 应用层不允许通过导入或全限定名绕过仓储端口。 */
  @Test
  @DisplayName("FR-26 NFR-04 DDD 应用层与领域层不依赖 JPA、实体与持久化适配器")
  void FR26_applicationAndDomainHaveNoPersistenceDependencies() throws Exception {
    for (String directory : java.util.List.of("application", "domain")) {
      try (java.util.stream.Stream<Path> paths =
          Files.walk(Path.of("src/main/java/com/cdwy/permission/service/ddd/" + directory))) {
        for (Path source : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
          String content = Files.readString(source);
          for (String forbidden :
              java.util.List.of(
                  "com.cdwy.permission.repository.",
                  "com.cdwy.permission.entity.",
                  "jakarta.persistence.",
                  "org.springframework.data.",
                  "infrastructure.persistence"))
            assertFalse(content.contains(forbidden), source + " depends on " + forbidden);
        }
      }
    }
  }

  /** HTTP 表现只依赖用例端口，不能选到固定的结构化实现。 */
  @Test
  @DisplayName("FR-26 NFR-04 Controller 只依赖用例端口，不依赖结构化实现或实体")
  void FR26_httpBoundaryDependsOnPorts() throws Exception {
    try (java.util.stream.Stream<Path> paths =
        Files.walk(Path.of("src/main/java/com/cdwy/permission/controller"))) {
      for (Path source : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String content = Files.readString(source);
        assertFalse(
            content.matches(
                "(?s).*import"
                    + " com\\.cdwy\\.permission\\.service\\.(user|role|organization|system|resource|permission|auth|rbac)\\..*"),
            source.toString());
        assertFalse(content.contains("com.cdwy.permission.entity."), source.toString());
      }
    }
  }

  /** 期限规则不依赖数据库：截止日当天可用，次日零点（策略时区）起失效，超过上限被拒绝。 */
  @Test
  @DisplayName("FR-26 有效期包含截止日当天，次日零点起失效，超过上限返回 ACCESS_WINDOW_INVALID")
  void FR26_expiryIncludesLastDayAndExcludesFollowingMidnight() {
    AccessWindow window =
        new AccessWindow(7, 3, ZoneId.of("Asia/Shanghai"), LocalDate.of(2026, 11, 18));
    Account account = new Account();
    account.setId(9L);
    account.profile("临时人员", null, Account.TEMPORARY, LocalDate.of(2026, 11, 21), window);
    assertTrue(account.usable(Instant.parse("2026-11-21T15:59:59Z")));
    assertFalse(account.usable(Instant.parse("2026-11-21T16:00:00Z")));
    InvalidDataException tooLong =
        assertThrows(
            InvalidDataException.class,
            () -> window.expiry(Account.TEMPORARY, LocalDate.of(2026, 11, 22)));
    assertEquals("ACCESS_WINDOW_INVALID", tooLong.getCode());
  }

  /** 重复停用不重复递增凭证版本，重新启用仍不恢复旧 Token。 */
  @Test
  @DisplayName("FR-26 停用、启用与重置密码轮换凭证版本，重复停用不重复递增")
  void FR26_accountStateAndPasswordRotateCredentialVersion() {
    Account account = new Account();
    account.setId(9L);
    account.changeStatus(1L, false);
    assertEquals(1, account.getCredentialVersion());
    account.changeStatus(1L, false);
    assertEquals(1, account.getCredentialVersion());
    account.changeStatus(1L, true);
    assertEquals(2, account.getCredentialVersion());
    account.resetPassword("encoded-fixture");
    assertEquals(3, account.getCredentialVersion());
    assertThrows(ForbiddenException.class, () -> account.changeStatus(9L, false));
  }

  /** 管理员身份不能通过改为外部人员或删除账号移除。 */
  @Test
  @DisplayName("FR-26 管理员身份不能通过改为外部人员或删除账号移除")
  void FR26_adminIdentityCannotBeRemovedByChangingPersonType() {
    Account account = new Account();
    account.setAdministrator(true);
    account.setId(1L);
    assertThrows(
        ForbiddenException.class,
        () ->
            account.profile(
                "管理员",
                null,
                Account.EXTERNAL,
                LocalDate.of(2026, 11, 19),
                new AccessWindow(7, 3, ZoneOffset.UTC, LocalDate.of(2026, 11, 18))));
    assertThrows(ForbiddenException.class, () -> account.assertDeletable(2L));
  }
}
