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
import java.nio.charset.StandardCharsets;
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
import org.springframework.transaction.annotation.Propagation;
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
class PhaseTwoRbac1Tests {
  private static final String DEMO_COURSES_URL = "/api/demo/courses";
  private static final String LOGIN_URL = "/api/auth/login";
  private static final String MY_PERMISSIONS_URL = "/api/me/permissions";
  private static final String MY_PERMISSION_ORIGINS_URL = "/api/me/permission-origins";
  private static final String PERMISSIONS_URL = "/api/permissions";
  private static final String PERMISSION_RESOURCE_URL =
      "/api/permissions/{permissionId}/resources/{resourceId}";
  private static final String RESOURCES_URL = "/api/resources";
  private static final String ROLES_URL = "/api/roles";
  private static final String ROLE_EFFECTIVE_PERMISSIONS_URL =
      "/api/roles/{roleId}/effective-permissions";
  private static final String ROLE_HIERARCHY_URL = "/api/role-hierarchy";
  private static final String ROLE_INHERIT_URL = "/api/roles/{roleId}/inherits/{inheritedId}";
  private static final String ROLE_PERMISSION_URL =
      "/api/roles/{roleId}/permissions/{permissionId}";
  private static final String ROLE_STATUS_URL = "/api/roles/{roleId}/status";
  private static final String ROLE_URL = "/api/roles/{roleId}";
  private static final String SYSTEMS_URL = "/api/systems";
  private static final String USERS_URL = "/api/users";
  private static final String USER_PERMISSION_ORIGINS_URL =
      "/api/users/{userId}/permission-origins";
  private static final String USER_ROLE_URL = "/api/users/{userId}/roles/{roleId}";

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

  /** FR-17：继承关系支持多级传递，并在相同令牌的下一次请求生效。 */
  @Test
  @DisplayName("FR-17 多级继承与撤回在同一Token下生效")
  void FR17_transitiveInheritanceAndRevocation() throws Exception {
    long manager = role("manager"), lead = role("lead");
    call("PUT", url(USER_ROLE_URL, user, lead), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    call("PUT", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    call("GET", DEMO_COURSES_URL, employee, null, 403);
    edge(manager, role, "PUT", 204);
    edge(lead, manager, "PUT", 204);
    call("GET", DEMO_COURSES_URL, employee, null, 200);
    JsonNode origins = result(call("GET", MY_PERMISSION_ORIGINS_URL, employee, null, 200));
    assertEquals(1, origins.size());
    assertEquals(button, origins.get(0).get("permission").get("id").asLong());
    JsonNode source = origins.get(0).get("sources").get(0);
    assertTrue(source.get("inherited").asBoolean());
    assertEquals(
        List.of(lead, manager, role),
        json.readValue(
            source.get("path").toString(),
            json.getTypeFactory().constructCollectionType(List.class, Long.class)));
    edge(manager, role, "DELETE", 204);
    call("GET", DEMO_COURSES_URL, employee, null, 403);
  }

  /** FR-18：拒绝自继承和多级闭环，失败不污染原关系。 */
  @Test
  @DisplayName("FR-18 自继承、环路、重复边及缺失对象")
  void FR18_rejectCyclesAndKeepIdempotency() throws Exception {
    long second = role("second"), third = role("third");
    edge(role, role, "PUT", 400);
    edge(role, second, "PUT", 204);
    edge(role, second, "PUT", 204);
    edge(second, third, "PUT", 204);
    edge(third, role, "PUT", 400);
    edge(second, role, "PUT", 400);
    assertEquals(2, result(call("GET", ROLE_HIERARCHY_URL, admin, null, 200)).size());
    edge(role, 999999, "PUT", 404);
    edge(999999, role, "PUT", 404);
    edge(role, second, "DELETE", 204);
    edge(role, second, "DELETE", 204);
    call("DELETE", url(ROLE_URL, third), admin, null, 409);
    edge(second, third, "DELETE", 204);
    call("DELETE", url(ROLE_URL, third), admin, null, 204);
  }

  /** FR-19：多路径权限去重，停用阻断继承但保留其他来源。 */
  @Test
  @DisplayName("FR-19 菱形继承去重、停用隔离和直接授权")
  void FR19_diamondAndDisabledBranches() throws Exception {
    long left = role("left"), right = role("right"), top = role("top");
    edge(top, left, "PUT", 204);
    edge(top, right, "PUT", 204);
    edge(left, role, "PUT", 204);
    edge(right, role, "PUT", 204);
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, top), admin, null, 204);
    assertEquals(1, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
    call("PUT", url(ROLE_STATUS_URL, left), admin, Map.of("enabled", false), 200);
    assertEquals(1, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
    call("PUT", url(ROLE_STATUS_URL, right), admin, Map.of("enabled", false), 200);
    assertEquals(0, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
    JsonNode sources = result(call("GET", MY_PERMISSION_ORIGINS_URL, employee, null, 200));
    assertEquals(1, sources.size());
    assertFalse(sources.get(0).get("sources").get(0).get("inherited").asBoolean());
    call("PUT", url(ROLE_STATUS_URL, role), admin, Map.of("enabled", false), 200);
    assertEquals(0, result(call("GET", MY_PERMISSION_ORIGINS_URL, employee, null, 200)).size());
  }

  /** FR-20：普通用户仅能读取自身来源，不能修改继承或查看他人。 */
  @Test
  @DisplayName("FR-20 继承管理权限边界与角色有效权限预览")
  void FR20_authorizationBoundaryAndPreview() throws Exception {
    long manager = role("manager");
    edge(manager, role, "PUT", 204);
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    assertEquals(
        1,
        result(call("GET", url(ROLE_EFFECTIVE_PERMISSIONS_URL, manager), admin, null, 200))
            .size());
    call("GET", ROLE_HIERARCHY_URL, employee, null, 403);
    call("PUT", url(ROLE_INHERIT_URL, role, manager), employee, null, 403);
    call("DELETE", url(ROLE_INHERIT_URL, manager, role), employee, null, 403);
    call("GET", url(USER_PERMISSION_ORIGINS_URL, user), employee, null, 403);
    call("GET", url(ROLE_EFFECTIVE_PERMISSIONS_URL, manager), employee, null, 403);
    call("GET", MY_PERMISSION_ORIGINS_URL, null, null, 401);
    call("GET", url(USER_PERMISSION_ORIGINS_URL, 999999), admin, null, 404);
  }

  /** FR-19：撤回一条来源不能删除另一条仍有效的授权。 */
  @Test
  @DisplayName("FR-19 多来源去重且撤回直接授权后继承授权仍有效")
  void FR19_independentSourcesSurviveDirectRevocation() throws Exception {
    long manager = role("manager");
    edge(manager, role, "PUT", 204);
    call("PUT", url(USER_ROLE_URL, user, manager), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    JsonNode before = result(call("GET", MY_PERMISSION_ORIGINS_URL, employee, null, 200));
    assertEquals(1, before.size());
    assertEquals(2, before.get(0).get("sources").size());
    call("DELETE", url(USER_ROLE_URL, user, role), admin, null, 204);
    JsonNode after = result(call("GET", MY_PERMISSION_ORIGINS_URL, employee, null, 200));
    assertEquals(1, after.size());
    assertEquals(1, after.get(0).get("sources").size());
    assertTrue(after.get(0).get("sources").get(0).get("inherited").asBoolean());
  }

  /** FR-18：停用不能掩盖潜在环路；关系两端停用时拒绝新增。 */
  @Test
  @DisplayName("FR-18 停用节点仍参与防环检查")
  void FR18_disabledIntermediateStillParticipatesInCycleCheck() throws Exception {
    long middle = role("middle"), bottom = role("bottom");
    edge(role, middle, "PUT", 204);
    edge(middle, bottom, "PUT", 204);
    call("PUT", url(ROLE_STATUS_URL, middle), admin, Map.of("enabled", false), 200);
    edge(bottom, role, "PUT", 400);
    edge(bottom, middle, "PUT", 400);
    assertEquals(2, result(call("GET", ROLE_HIERARCHY_URL, admin, null, 200)).size());
  }

  /** FR-18：并发反向加边最多成功一次，由 MySQL 行锁串行化继承图写入。 */
  @Test
  @DisplayName("FR-18 并发反向继承不会形成环路")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void FR18_concurrentOppositeEdgesCannotFormCycle() throws Exception {
    try {
      opposingEdgesRace();
    } finally {
      TestDatabase.clear(db);
    }
  }

  /**
   * 两个线程同时写入方向相反的继承边。并发请求运行在各自的连接上，只能看到已提交的数据，所以这个测试不在回滚事务里运行，
   * 结束后自行清空提交的数据。
   */
  private void opposingEdgesRace() throws Exception {
    long other = role("other");
    java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
    try (java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      java.util.concurrent.Future<Integer> forward =
          executor.submit(
              () -> {
                barrier.await();
                return mvc.perform(
                        send("PUT", url(ROLE_INHERIT_URL, role, other), admin, null))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      java.util.concurrent.Future<Integer> reverse =
          executor.submit(
              () -> {
                barrier.await();
                return mvc.perform(
                        send("PUT", url(ROLE_INHERIT_URL, other, role), admin, null))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      List<Integer> statuses =
          new ArrayList<>(
              List.of(
                  forward.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  reverse.get(10, java.util.concurrent.TimeUnit.SECONDS)));
      statuses.sort(Integer::compareTo);
      assertEquals(List.of(204, 400), statuses);
      assertEquals(1, result(call("GET", ROLE_HIERARCHY_URL, admin, null, 200)).size());
    }
  }

  /** 创建独立测试角色。 */
  long role(String code) throws Exception {
    return create(ROLES_URL, Map.of("code", code, "name", code));
  }

  /** 调用继承端点；第一个角色获得第二个角色的权限。 */
  void edge(long senior, long inherited, String method, int status) throws Exception {
    call(method, url(ROLE_INHERIT_URL, senior, inherited), admin, null, status);
  }
}
