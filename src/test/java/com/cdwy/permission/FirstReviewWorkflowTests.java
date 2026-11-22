package com.cdwy.permission;

import static com.cdwy.permission.support.TestUrls.url;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.config.*;
import com.cdwy.permission.repository.rbac.*;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.auth.AdminBootstrapService;
import com.cdwy.permission.support.RequestTransactionBoundary;
import com.cdwy.permission.support.TestDatabase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP 级业务验收：相同 Token 下的授权变化、实际资源映射与无会话登录。 */
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(
    properties = {
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3"
    })
@AutoConfigureMockMvc
class FirstReviewWorkflowTests {
  private static final String DEMO_COURSES_URL = "/api/demo/courses";
  private static final String DEPARTMENTS_URL = "/api/departments";
  private static final String DEPARTMENT_URL = "/api/departments/{departmentId}";
  private static final String LOGIN_URL = "/api/auth/login";
  private static final String ME_URL = "/api/me";
  private static final String MY_MENU_URL = "/api/me/menu";
  private static final String MY_PASSWORD_URL = "/api/me/password";
  private static final String MY_PERMISSIONS_URL = "/api/me/permissions";
  private static final String PERMISSIONS_URL = "/api/permissions";
  private static final String PERMISSION_RESOURCE_URL =
      "/api/permissions/{permissionId}/resources/{resourceId}";
  private static final String PERMISSION_URL = "/api/permissions/{permissionId}";
  private static final String RESOURCES_URL = "/api/resources";
  private static final String RESOURCE_STATUS_URL = "/api/resources/{resourceId}/status";
  private static final String RESOURCE_URL = "/api/resources/{resourceId}";
  private static final String ROLES_URL = "/api/roles";
  private static final String ROLE_PERMISSION_URL =
      "/api/roles/{roleId}/permissions/{permissionId}";
  private static final String ROLE_URL = "/api/roles/{roleId}";
  private static final String SYSTEMS_URL = "/api/systems";
  private static final String SYSTEM_INFO_URL = "/api/system-info";
  private static final String SYSTEM_URL = "/api/systems/{systemId}";
  private static final String USERS_URL = "/api/users";
  private static final String USER_ROLE_URL = "/api/users/{userId}/roles/{roleId}";
  private static final String USER_STATUS_URL = "/api/users/{userId}/status";
  private static final String USER_URL = "/api/users/{userId}";

  static final String PASSWORD = "Review-2026!";
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;
  @Autowired UserAccountRepository users;
  @Autowired ApplicationContext context;
  @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping routes;
  String admin, employee;
  long user, system, role, menu, button, resource;

  @BeforeEach
  void fixture() throws Exception {
    TestDatabase.clear(db);
    bootstrap.initialize("admin", PASSWORD);
    admin = login("admin", PASSWORD);
    user =
        create(
            USERS_URL, Map.of("username", "worker", "displayName", "员工", "password", PASSWORD));
    employee = login("worker", PASSWORD);
    system = create(SYSTEMS_URL, Map.of("code", "course", "name", "课程管理系统"));
    role = create(ROLES_URL, Map.of("code", "teacher", "name", "教师"));
    menu =
        create(
            PERMISSIONS_URL,
            Map.of(
                "systemId",
                system,
                "code",
                "course:menu",
                "name",
                "课程管理",
                "type",
                "MENU",
                "menuPath",
                "/courses"));
    button =
        create(
            PERMISSIONS_URL,
            Map.of(
                "systemId",
                system,
                "code",
                "course:list",
                "name",
                "查看课程",
                "type",
                "BUTTON",
                "parentId",
                menu));
    resource =
        create(
            RESOURCES_URL,
            Map.of(
                "systemId", system, "name", "课程列表", "method", "GET", "path", "/api/demo/courses"));
  }

  JsonNode result(MvcResult result) {
    return json.readTree(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  MockHttpServletRequestBuilder send(String method, String path, String token, Object body) {
    MockHttpServletRequestBuilder b =
        request(org.springframework.http.HttpMethod.valueOf(method), path)
            .contentType(MediaType.APPLICATION_JSON);
    if (token != null) b.header("Authorization", "Bearer " + token);
    if (body != null) b.content(json.writeValueAsString(body));
    return b;
  }

  MvcResult call(String method, String path, String token, Object body, int status)
      throws Exception {
    return mvc.perform(send(method, path, token, body)).andExpect(status().is(status)).andReturn();
  }

  long create(String path, Object input) throws Exception {
    return result(call("POST", path, admin, input, 201)).get("id").asLong();
  }

  String login(String name, String password) throws Exception {
    return result(
            call(
                "POST",
                LOGIN_URL,
                null,
                Map.of("username", name, "password", password),
                200))
        .get("accessToken")
        .asString();
  }

  void grant() throws Exception {
    call("PUT", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
  }

  void demo(int expected) throws Exception {
    call("GET", DEMO_COURSES_URL, employee, null, expected);
  }

  @Test
  @DisplayName("FR-01 NFR-10 登录返回 Bearer JWT，不创建服务端会话，库中也没有会话表")
  void FR01_loginReturnsBearerAndNeverCreatesServerSession() throws Exception {
    MvcResult response =
        call(
            "POST",
            LOGIN_URL,
            null,
            Map.of("username", "worker", "password", PASSWORD),
            200);
    assertEquals("Bearer", result(response).get("tokenType").asString());
    assertTrue(result(response).get("expiresIn").asLong() > 0);
    assertNull(response.getRequest().getSession(false));
    assertNull(response.getResponse().getHeader("Set-Cookie"));
    // Hibernate may create temporary bulk-mutation tables for @SecondaryTable; these are not
    // persisted business tables.
    Set<String> tables =
        new HashSet<>(
            db.queryForList(
                "select table_name from information_schema.tables where table_schema = database()"
                    + " and table_type = 'BASE TABLE'",
                String.class));
    // 11 张业务表 + 1 张审计日志技术表（不进入核心 ER）；JWT 无状态，不存在会话表。
    assertEquals(12, tables.size());
    assertTrue(tables.stream().anyMatch(t -> t.equalsIgnoreCase("sys_audit_log")));
    assertFalse(tables.stream().anyMatch(t -> t.toLowerCase().contains("session")));
    call("GET", ME_URL, employee, null, 200);
  }

  @Test
  @DisplayName("NFR-01 错误凭证、缺少或畸形令牌、过期或伪造令牌一律返回 401")
  void NFR01_badCredentialsMissingMalformedExpiredAndForgedTokensReturn401() throws Exception {
    call("POST", LOGIN_URL, null, Map.of("username", "worker", "password", "wrong"), 401);
    call("POST", LOGIN_URL, null, Map.of("username", "missing", "password", PASSWORD), 401);
    call("GET", ME_URL, null, null, 401);
    call("GET", ME_URL, "not-a-jwt", null, 401);
    javax.crypto.SecretKey key =
        Keys.hmacShaKeyFor(
            "first-review-integration-signing-secret-32-bytes".getBytes(StandardCharsets.UTF_8));
    String expired =
        Jwts.builder()
            .subject("" + user)
            .claim("cv", 0)
            .issuedAt(Date.from(Instant.now().minusSeconds(60)))
            .expiration(Date.from(Instant.now().minusSeconds(1)))
            .signWith(key)
            .compact();
    call("GET", ME_URL, expired, null, 401);
    String forged =
        Jwts.builder()
            .subject("" + user)
            .claim("cv", 0)
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(Jwts.SIG.HS256.key().build())
            .compact();
    call("GET", ME_URL, forged, null, 401);
  }

  @Test
  @DisplayName("NFR-02 响应不含密码摘要与凭证版本，请求也不能夹带管理员身份")
  void NFR02_loginResponseCannotExposePasswordOrCredentialVersionOrAdminAssignment() throws Exception {
    String body =
        call(
                "POST",
                LOGIN_URL,
                null,
                Map.of("username", "worker", "password", PASSWORD),
                200)
            .getResponse()
            .getContentAsString();
    assertFalse(body.contains("passwordHash"));
    assertFalse(body.contains("credentialVersion"));
    call(
        "POST",
        USERS_URL,
        admin,
        Map.of(
            "username",
            "escalate",
            "displayName",
            "不能提权",
            "password",
            PASSWORD,
            "administrator",
            true),
        400);
  }

  @Test
  @DisplayName("NFR-01 普通用户不能调用管理接口，管理员也不会自动获得业务接口权限")
  void NFR01_ordinaryUsersCannotManageAndAdministratorHasNoAutomaticBusinessGrant() throws Exception {
    call("GET", USERS_URL, employee, null, 403);
    call("POST", ROLES_URL, employee, Map.of("code", "hack", "name", "不能创建"), 403);
    call("GET", DEMO_COURSES_URL, admin, null, 403);
  }

  @Test
  @DisplayName("FR-14 角色、权限、接口绑定缺一不可，新授权无需重新登录即生效")
  void FR14_rolePermissionAndApiBindingAreAllNecessaryAndNewGrantsWorkWithoutRelogin() throws Exception {
    demo(403);
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
    demo(403);
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    demo(403);
    call("PUT", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    demo(200);
  }

  @Test
  @DisplayName("FR-10 重复分配幂等，撤回一个角色后其他角色提供的权限仍然有效")
  void FR10_duplicateAssignmentsAreIdempotentAndGrantUnionSurvivesOneRoleRevocation() throws Exception {
    grant();
    grant();
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from sys_user_role where user_id = ?", Integer.class, user));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from permission_api where resource_id = ?", Integer.class, resource));
    long second = create(ROLES_URL, Map.of("code", "assistant", "name", "助教"));
    call("PUT", url(USER_ROLE_URL, user, second), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, second, button), admin, null, 204);
    call("DELETE", url(USER_ROLE_URL, user, role), admin, null, 204);
    demo(200);
    call("DELETE", url(USER_ROLE_URL, user, second), admin, null, 204);
    demo(403);
  }

  @Test
  @DisplayName("FR-11 FR-12 撤回角色权限或解除接口绑定后，已签发的令牌立即被拒绝")
  void FR11_permissionRevocationAndApiUnbindingImmediatelyRejectExistingToken() throws Exception {
    grant();
    demo(200);
    call("DELETE", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    demo(403);
    grant();
    demo(200);
    call("DELETE", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    demo(403);
  }

  @Test
  @DisplayName("FR-15 状态接口停用后影响全部持有者，且不改动普通字段")
  void FR15_statusCommandsAffectAllHoldersWithoutChangingOrdinaryFields() throws Exception {
    grant();
    demo(200);
    for (String route :
        List.of(
            url(PERMISSION_URL, button),
            url(PERMISSION_URL, menu),
            url(RESOURCE_URL, resource))) {
      call("PUT", route + "/status", admin, Map.of("active", false), 200);
      demo(403);
      call("PUT", route + "/status", admin, Map.of("active", true), 200);
      demo(200);
    }
    for (String route : List.of(url(ROLE_URL, role), url(SYSTEM_URL, system))) {
      call("PUT", route + "/status", admin, Map.of("enabled", false), 200);
      demo(403);
      call("PUT", route + "/status", admin, Map.of("enabled", true), 200);
      demo(200);
    }
  }

  @Test
  @DisplayName("FR-15 普通编辑不能夹带状态字段，改名不影响已有授权")
  void FR15_ordinaryEditsCannotSmuggleStatusAndNamesDoNotRevokeGrants() throws Exception {
    grant();
    call(
        "PUT",
        url(PERMISSION_URL, button),
        admin,
        Map.of("name", "查看课程", "type", "BUTTON", "parentId", menu, "active", false),
        400);
    call("PUT", url(ROLE_URL, role), admin, Map.of("name", "教师", "enabled", false), 400);
    call("PUT", url(SYSTEM_URL, system), admin, Map.of("name", "课程", "enabled", false), 400);
    call(
        "PUT",
        url(USER_URL, user),
        admin,
        Map.of("displayName", "员工", "personType", "EMPLOYEE", "enabled", false),
        400);
    call(
        "PUT",
        url(PERMISSION_URL, button),
        admin,
        Map.of("name", "查看课程列表", "type", "BUTTON", "parentId", menu),
        200);
    demo(200);
  }

  @Test
  @DisplayName("FR-03 停用账号使已签发令牌失效，重新启用也不恢复旧令牌")
  void FR03_disabledAccountInvalidatesTokenAndReenableDoesNotReviveIt() throws Exception {
    call("PUT", url(USER_STATUS_URL, user), admin, Map.of("enabled", false), 200);
    call("GET", ME_URL, employee, null, 401);
    call("POST", LOGIN_URL, null, Map.of("username", "worker", "password", PASSWORD), 401);
    call("PUT", url(USER_STATUS_URL, user), admin, Map.of("enabled", true), 200);
    call("GET", ME_URL, employee, null, 401);
    call("GET", ME_URL, login("worker", PASSWORD), null, 200);
  }

  @Test
  @DisplayName("FR-02 改密后旧令牌失效，不依赖服务端会话存储")
  void FR02_passwordChangeInvalidatesOldTokenWithoutSessionStorage() throws Exception {
    call(
        "PUT",
        MY_PASSWORD_URL,
        employee,
        Map.of("currentPassword", "wrong", "newPassword", "New-Password!2026"),
        401);
    call(
        "PUT",
        MY_PASSWORD_URL,
        employee,
        Map.of("currentPassword", PASSWORD, "newPassword", "New-Password!2026"),
        204);
    call("GET", ME_URL, employee, null, 401);
    call("POST", LOGIN_URL, null, Map.of("username", "worker", "password", PASSWORD), 401);
    call("GET", ME_URL, login("worker", "New-Password!2026"), null, 200);
  }

  @Test
  @DisplayName("FR-13 个人菜单保留祖先容器，祖先不因此获得接口权限")
  void FR13_menuTreeKeepsAncestorsAsContainersWithoutGrantingTheirApi() throws Exception {
    grant();
    JsonNode tree = result(call("GET", MY_MENU_URL, employee, null, 200));
    assertEquals(menu, tree.get(0).get("permission").get("id").asLong());
    assertFalse(tree.get(0).get("granted").asBoolean());
    assertTrue(tree.get(0).get("children").get(0).get("granted").asBoolean());
    assertEquals(1, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
  }

  @Test
  @DisplayName("FR-08 菜单树拒绝环路、跨应用上级和以按钮为上级")
  void FR08_menuTreeRejectsCyclesCrossApplicationParentsAndButtonParents() throws Exception {
    call(
        "PUT",
        url(PERMISSION_URL, menu),
        admin,
        Map.of("name", "课程", "type", "MENU", "parentId", menu),
        400);
    call(
        "PUT",
        url(PERMISSION_URL, menu),
        admin,
        Map.of("name", "课程", "type", "BUTTON", "parentId", button),
        400);
    long other = create(SYSTEMS_URL, Map.of("code", "other", "name", "其他"));
    call(
        "POST",
        PERMISSIONS_URL,
        admin,
        Map.of(
            "systemId",
            other,
            "code",
            "other:button",
            "name",
            "按钮",
            "type",
            "BUTTON",
            "parentId",
            menu),
        400);
    call(
        "POST",
        PERMISSIONS_URL,
        admin,
        Map.of("systemId", system, "code", "bad:button", "name", "按钮", "type", "BUTTON"),
        400);
    call(
        "POST",
        PERMISSIONS_URL,
        admin,
        Map.of("systemId", system, "code", "bad:api", "name", "API", "type", "API"),
        400);
  }

  @Test
  @DisplayName("FR-12 FR-14 接口不能跨应用绑定，鉴权按最具体的路径匹配且不能绕过")
  void FR14_apiResourcesCannotBindAcrossApplicationsOrBypassMoreSpecificRoute() throws Exception {
    grant();
    long other = create(SYSTEMS_URL, Map.of("code", "other", "name", "其他"));
    long otherApi =
        create(
            RESOURCES_URL,
            Map.of("systemId", other, "name", "其他接口", "method", "GET", "path", "/api/other"));
    call("PUT", url(PERMISSION_RESOURCE_URL, button, otherApi), admin, null, 400);
    long variable =
        create(
            RESOURCES_URL,
            Map.of(
                "systemId",
                system,
                "name",
                "泛化路径",
                "method",
                "GET",
                "path",
                "/api/demo/{anything}"));
    call("PUT", url(PERMISSION_RESOURCE_URL, button, variable), admin, null, 204);
    call("DELETE", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    demo(403);
    call("PUT", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    demo(200);
    // Disabling an exact resource must not fall back to an authorized broader pattern.
    call("PUT", url(RESOURCE_STATUS_URL, resource), admin, Map.of("active", false), 200);
    demo(403);
    call("PUT", url(RESOURCE_STATUS_URL, resource), admin, Map.of("active", true), 200);
    demo(200);
  }

  @Test
  @DisplayName("FR-09 API 资源拒绝重复路由和过宽的通配路径")
  void FR09_resourceValidationRejectsDuplicatesAndBroadWildcards() throws Exception {
    call(
        "POST",
        RESOURCES_URL,
        admin,
        Map.of("systemId", system, "name", "重复", "method", "GET", "path", "/api/demo/courses"),
        409);
    call(
        "POST",
        RESOURCES_URL,
        admin,
        Map.of("systemId", system, "name", "过宽", "method", "GET", "path", "/api/**"),
        400);
    call(
        "POST",
        RESOURCES_URL,
        admin,
        Map.of("systemId", system, "name", "无绝对路径", "method", "GET", "path", "api/demo"),
        400);
  }

  @Test
  @DisplayName("FR-05 部门树防环，有子部门或员工时不能删除")
  void FR05_departmentTreeAndDeleteReferencesRemainConsistent() throws Exception {
    long root = create(DEPARTMENTS_URL, Map.of("name", "信息部"));
    long child = create(DEPARTMENTS_URL, Map.of("name", "开发组", "parentId", root));
    call("PUT", url(DEPARTMENT_URL, root), admin, Map.of("name", "信息部", "parentId", child), 400);
    call("DELETE", url(DEPARTMENT_URL, root), admin, null, 409);
    call(
        "PUT",
        url(USER_URL, user),
        admin,
        Map.of("displayName", "员工", "departmentId", child, "personType", "EMPLOYEE"),
        200);
    call("DELETE", url(DEPARTMENT_URL, child), admin, null, 409);
  }

  @Test
  @DisplayName("FR-04 NFR-06 外部与临时人员共用用户实体，期限受配置上限约束，过期后不能登录")
  void FR04_externalAndTemporaryPeopleUseSharedUserEntityAndConfiguredValidityLimits() throws Exception {
    java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    call(
        "POST",
        USERS_URL,
        admin,
        Map.of(
            "username",
            "external",
            "displayName",
            "外部",
            "password",
            PASSWORD,
            "personType",
            "EXTERNAL"),
        400);
    call(
        "POST",
        USERS_URL,
        admin,
        Map.of(
            "username",
            "external",
            "displayName",
            "外部",
            "password",
            PASSWORD,
            "personType",
            "EXTERNAL",
            "expiresOn",
            today.plusDays(8).toString()),
        400);
    long external =
        create(
            USERS_URL,
            Map.of(
                "username",
                "external",
                "displayName",
                "外部",
                "password",
                PASSWORD,
                "personType",
                "EXTERNAL",
                "expiresOn",
                today.plusDays(7).toString()));
    assertEquals(
        "EXTERNAL",
        result(call("GET", url(USER_URL, external), admin, null, 200))
            .get("personType")
            .asString());
    call(
        "POST",
        USERS_URL,
        admin,
        Map.of(
            "username",
            "temp",
            "displayName",
            "临时",
            "password",
            PASSWORD,
            "personType",
            "TEMPORARY",
            "expiresOn",
            today.plusDays(4).toString()),
        400);
    // 库中时间按 UTC 存储（hibernate.jdbc.time_zone=UTC），直接写库时同样按 UTC 写入。
    db.update(
        "update sys_user set expires_at=? where id=?",
        java.time.LocalDateTime.ofInstant(Instant.now().minusSeconds(1), java.time.ZoneOffset.UTC),
        external);
    call(
        "POST", LOGIN_URL, null, Map.of("username", "external", "password", PASSWORD), 401);
  }

  @Test
  @DisplayName("FR-03 删除前检查引用，管理员账号受保护")
  void FR03_deletionsRespectReferencesAndProtectAdministrator() throws Exception {
    grant();
    call("DELETE", url(ROLE_URL, role), admin, null, 409);
    call("DELETE", url(PERMISSION_URL, button), admin, null, 409);
    call("DELETE", url(RESOURCE_URL, resource), admin, null, 409);
    call("DELETE", url(SYSTEM_URL, system), admin, null, 409);
    long adminId = result(call("GET", ME_URL, admin, null, 200)).get("id").asLong();
    call("PUT", url(USER_STATUS_URL, adminId), admin, Map.of("enabled", false), 403);
    call("DELETE", url(USER_URL, adminId), admin, null, 403);
    call("DELETE", url(USER_URL, user), admin, null, 204);
    call("GET", ME_URL, employee, null, 401);
  }

  @Test
  @DisplayName("NFR-01 初始化不能把普通账号提升为管理员，已删除的接口不再注册")
  void NFR01_initializationCannotEscalateOrdinaryUserAndRemovedEndpointsAreNotRegistered() {
    assertThrows(IllegalStateException.class, () -> bootstrap.initialize("worker", PASSWORD));
    List<String> registered =
        routes.getHandlerMethods().keySet().stream()
            .flatMap(r -> r.getPatternValues().stream())
            .toList();
    for (String old :
        List.of(
            "imports/departments",
            "imports/users",
            "imports/permissions",
            "reviews",
            "sessions",
            "approvals",
            "integration",
            "oidc",
            "refresh",
            "offline")) assertTrue(registered.stream().noneMatch(p -> p.contains(old)), old);
    assertFalse(context.containsBean("loginSessionRepository"));
  }

  @Test
  @DisplayName("NFR-05 导出实际路由与 MySQL 库结构，供需求追踪文档生成")
  void NFR05_exportOpenApiInputsRoutesAndActualSchemaForDocumentation() throws Exception {
    Map<String, List<Map<String, Object>>> schemas = new LinkedHashMap<>();
    for (String table :
        db.queryForList(
            "select table_name from information_schema.tables where table_schema = database()"
                + " and table_type = 'BASE TABLE' order by table_name",
            String.class))
      schemas.put(
          table,
          db.queryForList(
              "select column_name as COLUMN_NAME, data_type as DATA_TYPE, is_nullable as"
                  + " IS_NULLABLE from information_schema.columns where table_schema = database()"
                  + " and table_name = ? order by ordinal_position",
              table));
    List<Map<String, Object>> exported =
        routes.getHandlerMethods().entrySet().stream()
            .filter(
                e ->
                    e.getValue()
                        .getBeanType()
                        .getPackageName()
                        .startsWith("com.cdwy.permission.controller"))
            .flatMap(
                e ->
                    e.getKey().getPatternValues().stream()
                        .map(
                            p ->
                                Map.of(
                                    "path",
                                    p,
                                    "methods",
                                    e.getKey().getMethodsCondition().getMethods().stream()
                                        .map(Enum::name)
                                        .toList(),
                                    "controller",
                                    e.getValue().getBeanType().getSimpleName(),
                                    "method",
                                    e.getValue().getMethod().getName())))
            .toList();
    java.nio.file.Path out = java.nio.file.Path.of("target/first-review");
    java.nio.file.Files.createDirectories(out);
    java.nio.file.Files.writeString(
        out.resolve("schema.json"),
        json.writerWithDefaultPrettyPrinter().writeValueAsString(schemas));
    java.nio.file.Files.writeString(
        out.resolve("routes.json"),
        json.writerWithDefaultPrettyPrinter().writeValueAsString(exported));
  }

  /** FR-26：两种配置均对外报告真实装载模式，防止部分服务静默回退。 */
  @Test
  @DisplayName("FR-26 NFR-06 两种配置下全部业务模块都报告实际装载的设计路线")
  void FR26_allBusinessModulesReportSelectedStrategy() throws Exception {
    String selected = environment.getProperty("app.backend-strategy", "structured");
    for (String module :
        java.util.List.of(
            "users",
            "departments",
            "systems",
            "roles",
            "permissions",
            "resources",
            "authorization",
            "hierarchy",
            "authentication",
            "directorySync",
            "identity")) {
      mvc.perform(get(SYSTEM_INFO_URL).header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.modules." + module).value(selected));
    }
  }

  @Autowired org.springframework.core.env.Environment environment;
}
