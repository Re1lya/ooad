package com.cdwy.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.service.auth.AdminBootstrapService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 课程管理系统接入验收（FR-27 ~ FR-30、NFR-11）：课程系统把 IDENTITY_BASE_URL 指向
 * /api/apps/cpm，按既有契约调用 /auth/me、/identity/users/{userId}、/auth/audit-logs。
 *
 * <p>测试数据：cpm 应用下“课程管理”菜单 MCO 绑定 API 权限码 CV，“审核课程”按钮 MCOR 绑定 CR；
 * 角色 course-admin 继承 teacher，用户 lin 只分配 course-admin。
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:application_identity_tests;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3"
    })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ApplicationIdentityTests {
  static final String PASSWORD = "Review-2026!";
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;
  String admin, teacher, outsider;
  long lin, cpm, other, teacherRole, adminRole, menu, button, otherMenu;

  @BeforeEach
  void fixture() throws Exception {
    db.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table :
        List.of(
            "sys_audit_log",
            "sys_role_hierarchy",
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
    bootstrap.initialize("admin", PASSWORD);
    admin = login("admin", PASSWORD);
    lin = create("/api/users", Map.of("username", "lin", "displayName", "林老师", "password", PASSWORD));
    create("/api/users", Map.of("username", "zhou", "displayName", "周同学", "password", PASSWORD));
    teacher = login("lin", PASSWORD);
    outsider = login("zhou", PASSWORD);
    cpm = create("/api/systems", Map.of("code", "cpm", "name", "课程项目管理系统"));
    other = create("/api/systems", Map.of("code", "oa", "name", "办公系统"));
    teacherRole = create("/api/roles", Map.of("code", "teacher", "name", "教师"));
    adminRole = create("/api/roles", Map.of("code", "course-admin", "name", "课程管理员"));
    menu =
        create(
            "/api/permissions",
            Map.of("systemId", cpm, "code", "MCO", "name", "课程管理", "type", "MENU"));
    button =
        create(
            "/api/permissions",
            Map.of(
                "systemId", cpm, "code", "MCOR", "name", "审核课程", "type", "BUTTON", "parentId",
                menu));
    otherMenu =
        create(
            "/api/permissions",
            Map.of("systemId", other, "code", "OA-HOME", "name", "办公首页", "type", "MENU"));
    long viewApi =
        create(
            "/api/resources",
            Map.of(
                "systemId", cpm, "name", "查看课程", "method", "GET", "path", "/courses", "code",
                "CV"));
    long reviewApi =
        create(
            "/api/resources",
            Map.of(
                "systemId",
                cpm,
                "name",
                "审核课程",
                "method",
                "PUT",
                "path",
                "/courses/{id}/review",
                "code",
                "CR"));
    call("PUT", "/api/permissions/" + menu + "/resources/" + viewApi, admin, null, 204);
    call("PUT", "/api/permissions/" + button + "/resources/" + reviewApi, admin, null, 204);
    call("PUT", "/api/roles/" + teacherRole + "/permissions/" + menu, admin, null, 204);
    call("PUT", "/api/roles/" + teacherRole + "/permissions/" + otherMenu, admin, null, 204);
    call("PUT", "/api/roles/" + adminRole + "/permissions/" + button, admin, null, 204);
    call("PUT", "/api/roles/" + adminRole + "/inherits/" + teacherRole, admin, null, 204);
    call("PUT", "/api/users/" + lin + "/roles/" + adminRole, admin, null, 204);
  }

  @Test
  @DisplayName("FR-27 /auth/me 返回继承得到的角色、菜单按钮码与绑定的 API 权限码")
  void FR27_currentIdentityIncludesInheritedRolesAndCodes() throws Exception {
    JsonNode me = result(call("GET", "/api/apps/cpm/auth/me", teacher, null, 200));
    assertEquals("lin", me.get("subject").asString());
    assertEquals(Set.of("course-admin", "teacher"), texts(me.get("roles")));
    assertEquals(Set.of("MCO", "MCOR", "CV", "CR"), texts(me.get("permissions")));
  }

  @Test
  @DisplayName("FR-27 解除继承后同一 Token 的下一次 /auth/me 立即失去继承权限")
  void FR27_inheritanceChangeIsVisibleOnNextCall() throws Exception {
    call("DELETE", "/api/roles/" + adminRole + "/inherits/" + teacherRole, admin, null, 204);
    JsonNode me = result(call("GET", "/api/apps/cpm/auth/me", teacher, null, 200));
    assertEquals(Set.of("course-admin"), texts(me.get("roles")));
    assertEquals(Set.of("MCOR", "CR"), texts(me.get("permissions")));
    call("PUT", "/api/roles/" + adminRole + "/inherits/" + teacherRole, admin, null, 204);
    me = result(call("GET", "/api/apps/cpm/auth/me", teacher, null, 200));
    assertEquals(Set.of("MCO", "MCOR", "CV", "CR"), texts(me.get("permissions")));
  }

  @Test
  @DisplayName("FR-27 停用被继承角色阻断路径；停用 API 资源只撤回对应后端权限码")
  void FR27_disabledRoleAndResourceAreExcluded() throws Exception {
    call("PUT", "/api/roles/" + teacherRole + "/status", admin, Map.of("enabled", false), 200);
    JsonNode me = result(call("GET", "/api/apps/cpm/auth/me", teacher, null, 200));
    assertEquals(Set.of("course-admin"), texts(me.get("roles")));
    assertEquals(Set.of("MCOR", "CR"), texts(me.get("permissions")));
    call("PUT", "/api/roles/" + teacherRole + "/status", admin, Map.of("enabled", true), 200);
    long reviewApi = 0;
    for (JsonNode resource : result(call("GET", "/api/resources", admin, null, 200)))
      if ("CR".equals(resource.get("code").asString())) reviewApi = resource.get("id").asLong();
    call("PUT", "/api/resources/" + reviewApi + "/status", admin, Map.of("active", false), 200);
    me = result(call("GET", "/api/apps/cpm/auth/me", teacher, null, 200));
    assertEquals(Set.of("MCO", "MCOR", "CV"), texts(me.get("permissions")));
  }

  @Test
  @DisplayName("FR-27 只返回本应用权限码；应用停用时不返回角色和权限；未知应用 404、无 Token 401")
  void FR27_permissionsAreScopedToTheApplication() throws Exception {
    JsonNode oa = result(call("GET", "/api/apps/oa/auth/me", teacher, null, 200));
    assertEquals(Set.of("OA-HOME"), texts(oa.get("permissions")));
    call("PUT", "/api/systems/" + cpm + "/status", admin, Map.of("enabled", false), 200);
    JsonNode me = result(call("GET", "/api/apps/cpm/auth/me", teacher, null, 200));
    assertEquals(Set.of(), texts(me.get("permissions")));
    assertEquals(Set.of(), texts(me.get("roles")));
    call("GET", "/api/apps/unknown/auth/me", teacher, null, 404);
    call("GET", "/api/apps/cpm/auth/me", null, null, 401);
  }

  @Test
  @DisplayName("FR-28 按统一身份用户标识查询姓名与工号")
  void FR28_lookupUserByIdentityId() throws Exception {
    JsonNode user = result(call("GET", "/api/apps/cpm/identity/users/lin", teacher, null, 200));
    assertEquals("lin", user.get("userId").asString());
    assertEquals("lin", user.get("no").asString());
    assertEquals("林老师", user.get("name").asString());
    call("GET", "/api/apps/cpm/identity/users/nobody", teacher, null, 404);
  }

  @Test
  @DisplayName("FR-29 审计写入按 requestId 幂等，查询按应用隔离并保留操作人快照")
  void FR29_auditWriteIsIdempotentAndScoped() throws Exception {
    Map<String, Object> body =
        Map.of(
            "requestId", "req-1",
            "type", "ROSTER_CHANGED",
            "objectType", "class",
            "objectId", "cpm-class-12",
            "target", "导入学生名单",
            "before", Map.of("imported", 0),
            "after", Map.of("imported", 30));
    JsonNode first = result(call("POST", "/api/apps/cpm/auth/audit-logs", teacher, body, 201));
    JsonNode again = result(call("POST", "/api/apps/cpm/auth/audit-logs", teacher, body, 201));
    assertEquals(first.get("id").asLong(), again.get("id").asLong());
    assertEquals("cpm-class-12", first.get("classId").asString());
    assertEquals("林老师", first.get("actor").get("name").asString());
    assertEquals(Set.of("course-admin"), texts(first.get("actor").get("roles")));

    JsonNode page =
        result(call("GET", "/api/apps/cpm/auth/audit-logs?page=1&pageSize=100", teacher, null, 200));
    assertEquals(1, page.get("total").asLong());
    assertEquals(30, page.get("items").get(0).get("after").get("imported").asInt());
    assertEquals(
        0, result(call("GET", "/api/apps/oa/auth/audit-logs", admin, null, 200)).get("total").asLong());
    assertEquals(
        1,
        result(call("GET", "/api/apps/cpm/auth/audit-logs?target=名单", teacher, null, 200))
            .get("total")
            .asLong());
  }

  @Test
  @DisplayName("FR-29 应用内没有任何有效权限的用户不能查询审计；管理员可以")
  void FR29_auditQueryRequiresPermissionInApplication() throws Exception {
    call("GET", "/api/apps/cpm/auth/audit-logs", outsider, null, 403);
    call("GET", "/api/apps/cpm/auth/audit-logs", admin, null, 200);
    call("POST", "/api/apps/unknown/auth/audit-logs", teacher, Map.of("type", "X"), 404);
    call("POST", "/api/apps/cpm/auth/audit-logs", teacher, Map.of("target", "缺少类型"), 400);
  }

  @Test
  @DisplayName("FR-30 角色继承建立与解除写入审计记录，操作人为当前管理员")
  void FR30_hierarchyChangesAreAudited() throws Exception {
    call("DELETE", "/api/roles/" + adminRole + "/inherits/" + teacherRole, admin, null, 204);
    JsonNode page = result(call("GET", "/api/audit-logs?system=rbac", admin, null, 200));
    assertEquals(2, page.get("total").asLong());
    JsonNode latest = page.get("items").get(0);
    assertEquals("ROLE_INHERITANCE_REMOVED", latest.get("type").asString());
    assertEquals("rbac-role-" + adminRole, latest.get("objectId").asString());
    assertEquals("系统管理员", latest.get("actor").get("name").asString());
    assertEquals("ROLE_INHERITANCE_ADDED", page.get("items").get(1).get("type").asString());
    call("GET", "/api/audit-logs", teacher, null, 403);
  }

  @Test
  @DisplayName("NFR-11 前端菜单切换携带 X-Client-Menu，后端 logback 日志按菜单记录请求与各层调用")
  void NFR11_menuSwitchProducesLayeredLogs(CapturedOutput output) throws Exception {
    mvc.perform(
            get("/api/systems")
                .header("Authorization", "Bearer " + admin)
                .header("X-Client-Menu", "systems"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"));
    String log = output.getOut();
    assertTrue(log.contains("[menu=systems]"), "日志应包含菜单上下文");
    assertTrue(log.contains("请求开始 GET /api/systems 菜单=systems"));
    assertTrue(log.contains("[Controller] list() 完成"));
    assertTrue(log.contains("list() 完成") && log.contains("[" + layer() + "]"));
    assertTrue(log.contains("请求结束 GET /api/systems 状态=200"));
  }

  /** 结构化模式的服务层日志标签。 */
  String layer() {
    return "Service";
  }

  Set<String> texts(JsonNode array) {
    Set<String> values = new TreeSet<>();
    array.forEach(v -> values.add(v.asString()));
    return values;
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
}
