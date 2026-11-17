package com.cdwy.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.config.*;
import com.cdwy.permission.repository.rbac.*;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.auth.AdminBootstrapService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP 级业务验收：相同 Token 下的授权变化、实际资源映射与无会话登录。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:phase_two_tests;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3"
    })
@AutoConfigureMockMvc
class PhaseTwoRbac1Tests {
  static final String PASSWORD = "Review-2026!";
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;
  @Autowired UserAccountRepository users;
  @Autowired ApplicationContext context;
  @Autowired RequestMappingHandlerMapping routes;
  String admin, employee;
  long user, system, role, menu, button, resource;

  @BeforeEach
  void fixture() throws Exception {
    db.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table :
        List.of(
            "permission_api",
            "sys_role_permission",
            "sys_user_role",
            "api_resource",
            "sys_permission",
            "sys_account",
            "sys_user",
            "sys_role",
            "biz_system",
            "sys_department")) db.update("delete from " + table);
    db.execute("SET REFERENTIAL_INTEGRITY TRUE");
    if (db.queryForObject(
            "select count(*) from information_schema.tables where table_name ="
                + " 'SYS_ROLE_HIERARCHY'",
            Integer.class)
        > 0) db.update("delete from sys_role_hierarchy");
    bootstrap.initialize("admin", PASSWORD);
    admin = login("admin", PASSWORD);
    user =
        create(
            "/api/users", Map.of("username", "worker", "displayName", "员工", "password", PASSWORD));
    employee = login("worker", PASSWORD);
    system = create("/api/systems", Map.of("code", "course", "name", "课程管理系统"));
    role = create("/api/roles", Map.of("code", "teacher", "name", "教师"));
    menu =
        create(
            "/api/permissions",
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
            "/api/permissions",
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
            "/api/resources",
            Map.of(
                "systemId", system, "name", "课程列表", "method", "GET", "path", "/api/demo/courses"));
  }

  JsonNode result(MvcResult result) {
    return json.readTree(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  MockHttpServletRequestBuilder send(String method, String path, String token, Object body) {
    var b =
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
                "/api/auth/login",
                null,
                Map.of("username", name, "password", password),
                200))
        .get("accessToken")
        .asString();
  }

  /** FR-17：继承关系支持多级传递，并在相同令牌的下一次请求生效。 */
  @Test
  @DisplayName("FR-17 多级继承与撤回在同一Token下生效")
  void transitiveInheritanceAndRevocation() throws Exception {
    long manager = role("manager"), lead = role("lead");
    call("PUT", "/api/users/" + user + "/roles/" + lead, admin, null, 204);
    call("PUT", "/api/roles/" + role + "/permissions/" + button, admin, null, 204);
    call("PUT", "/api/permissions/" + button + "/resources/" + resource, admin, null, 204);
    call("GET", "/api/demo/courses", employee, null, 403);
    edge(manager, role, "PUT", 204);
    edge(lead, manager, "PUT", 204);
    call("GET", "/api/demo/courses", employee, null, 200);
    JsonNode origins = result(call("GET", "/api/me/permission-origins", employee, null, 200));
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
    call("GET", "/api/demo/courses", employee, null, 403);
  }

  /** FR-18：拒绝自继承和多级闭环，失败不污染原关系。 */
  @Test
  @DisplayName("FR-18 自继承、环路、重复边及缺失对象")
  void rejectCyclesAndKeepIdempotency() throws Exception {
    long second = role("second"), third = role("third");
    edge(role, role, "PUT", 400);
    edge(role, second, "PUT", 204);
    edge(role, second, "PUT", 204);
    edge(second, third, "PUT", 204);
    edge(third, role, "PUT", 400);
    edge(second, role, "PUT", 400);
    assertEquals(2, result(call("GET", "/api/role-hierarchy", admin, null, 200)).size());
    edge(role, 999999, "PUT", 404);
    edge(999999, role, "PUT", 404);
    edge(role, second, "DELETE", 204);
    edge(role, second, "DELETE", 204);
    call("DELETE", "/api/roles/" + third, admin, null, 409);
    edge(second, third, "DELETE", 204);
    call("DELETE", "/api/roles/" + third, admin, null, 204);
  }

  /** FR-19：多路径权限去重，停用阻断继承但保留其他来源。 */
  @Test
  @DisplayName("FR-19 菱形继承去重、停用隔离和直接授权")
  void diamondAndDisabledBranches() throws Exception {
    long left = role("left"), right = role("right"), top = role("top");
    edge(top, left, "PUT", 204);
    edge(top, right, "PUT", 204);
    edge(left, role, "PUT", 204);
    edge(right, role, "PUT", 204);
    call("PUT", "/api/roles/" + role + "/permissions/" + button, admin, null, 204);
    call("PUT", "/api/users/" + user + "/roles/" + top, admin, null, 204);
    assertEquals(1, result(call("GET", "/api/me/permissions", employee, null, 200)).size());
    call("PUT", "/api/roles/" + left + "/status", admin, Map.of("enabled", false), 200);
    assertEquals(1, result(call("GET", "/api/me/permissions", employee, null, 200)).size());
    call("PUT", "/api/roles/" + right + "/status", admin, Map.of("enabled", false), 200);
    assertEquals(0, result(call("GET", "/api/me/permissions", employee, null, 200)).size());
    call("PUT", "/api/users/" + user + "/roles/" + role, admin, null, 204);
    JsonNode sources = result(call("GET", "/api/me/permission-origins", employee, null, 200));
    assertEquals(1, sources.size());
    assertFalse(sources.get(0).get("sources").get(0).get("inherited").asBoolean());
    call("PUT", "/api/roles/" + role + "/status", admin, Map.of("enabled", false), 200);
    assertEquals(0, result(call("GET", "/api/me/permission-origins", employee, null, 200)).size());
  }

  /** FR-20：普通用户仅能读取自身来源，不能修改继承或查看他人。 */
  @Test
  @DisplayName("FR-20 继承管理权限边界与角色有效权限预览")
  void authorizationBoundaryAndPreview() throws Exception {
    long manager = role("manager");
    edge(manager, role, "PUT", 204);
    call("PUT", "/api/roles/" + role + "/permissions/" + button, admin, null, 204);
    assertEquals(
        1,
        result(call("GET", "/api/roles/" + manager + "/effective-permissions", admin, null, 200))
            .size());
    call("GET", "/api/role-hierarchy", employee, null, 403);
    call("PUT", "/api/roles/" + role + "/inherits/" + manager, employee, null, 403);
    call("DELETE", "/api/roles/" + manager + "/inherits/" + role, employee, null, 403);
    call("GET", "/api/users/" + user + "/permission-origins", employee, null, 403);
    call("GET", "/api/roles/" + manager + "/effective-permissions", employee, null, 403);
    call("GET", "/api/me/permission-origins", null, null, 401);
    call("GET", "/api/users/999999/permission-origins", admin, null, 404);
  }

  /** FR-19：撤回一条来源不能删除另一条仍有效的授权。 */
  @Test
  @DisplayName("FR-19 多来源去重且撤回直接授权后继承授权仍有效")
  void independentSourcesSurviveDirectRevocation() throws Exception {
    long manager = role("manager");
    edge(manager, role, "PUT", 204);
    call("PUT", "/api/users/" + user + "/roles/" + manager, admin, null, 204);
    call("PUT", "/api/users/" + user + "/roles/" + role, admin, null, 204);
    call("PUT", "/api/roles/" + role + "/permissions/" + button, admin, null, 204);
    JsonNode before = result(call("GET", "/api/me/permission-origins", employee, null, 200));
    assertEquals(1, before.size());
    assertEquals(2, before.get(0).get("sources").size());
    call("DELETE", "/api/users/" + user + "/roles/" + role, admin, null, 204);
    JsonNode after = result(call("GET", "/api/me/permission-origins", employee, null, 200));
    assertEquals(1, after.size());
    assertEquals(1, after.get(0).get("sources").size());
    assertTrue(after.get(0).get("sources").get(0).get("inherited").asBoolean());
  }

  /** FR-18：停用不能掩盖潜在环路；关系两端停用时拒绝新增。 */
  @Test
  @DisplayName("FR-18 停用节点仍参与防环检查")
  void disabledIntermediateStillParticipatesInCycleCheck() throws Exception {
    long middle = role("middle"), bottom = role("bottom");
    edge(role, middle, "PUT", 204);
    edge(middle, bottom, "PUT", 204);
    call("PUT", "/api/roles/" + middle + "/status", admin, Map.of("enabled", false), 200);
    edge(bottom, role, "PUT", 400);
    edge(bottom, middle, "PUT", 400);
    assertEquals(2, result(call("GET", "/api/role-hierarchy", admin, null, 200)).size());
  }

  /** FR-18：并发反向加边最多成功一次，H2 隔离环境不替代 MySQL 并发验证。 */
  @Test
  @DisplayName("FR-18 并发反向继承不会形成环路（H2）")
  void concurrentOppositeEdgesCannotFormCycle() throws Exception {
    long other = role("other");
    java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
    try (java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      java.util.concurrent.Future<Integer> forward =
          executor.submit(
              () -> {
                barrier.await();
                return mvc.perform(
                        send("PUT", "/api/roles/" + role + "/inherits/" + other, admin, null))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      java.util.concurrent.Future<Integer> reverse =
          executor.submit(
              () -> {
                barrier.await();
                return mvc.perform(
                        send("PUT", "/api/roles/" + other + "/inherits/" + role, admin, null))
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
      assertEquals(1, result(call("GET", "/api/role-hierarchy", admin, null, 200)).size());
    }
  }

  /** 创建独立测试角色。 */
  long role(String code) throws Exception {
    return create("/api/roles", Map.of("code", code, "name", code));
  }

  /** 调用继承端点；第一个角色获得第二个角色的权限。 */
  void edge(long senior, long inherited, String method, int status) throws Exception {
    call(method, "/api/roles/" + senior + "/inherits/" + inherited, admin, null, status);
  }
}
