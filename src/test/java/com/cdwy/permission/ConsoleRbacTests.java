package com.cdwy.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.service.auth.AdminBootstrapService;
import com.cdwy.permission.service.auth.ConsoleCatalog;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * FR-38：权限系统自身登记为应用 rbac，控制台菜单与管理接口都由 RBAC 授权。
 *
 * <ul>
 *   <li>内置目录覆盖每一个管理接口，启动时幂等补齐；
 *   <li>导航按 /api/me/menu?system=rbac 动态生成，授予或撤销后下一次请求立即变化；
 *   <li>没有登记访问规则的接口一律拒绝。
 * </ul>
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:console_rbac_tests;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes"
    })
@AutoConfigureMockMvc
class ConsoleRbacTests {
  static final String PASSWORD = "Review-2026!";
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  String admin, auditor;
  long auditorId;

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
    admin = login("admin");
    auditorId =
        result(
                call(
                    "POST",
                    "/api/users",
                    admin,
                    Map.of("username", "auditor", "displayName", "审计员", "password", PASSWORD),
                    201))
            .get("id")
            .asLong();
    auditor = login("auditor");
  }

  @Test
  @DisplayName("FR-38 每个标注为 rbac 的管理接口（个人接口除外）都在内置目录中登记，且目录中没有多余接口")
  void FR38_catalogCoversEveryConsoleEndpoint() {
    Set<String> endpoints = new TreeSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (info, method) -> {
              BusinessAccess rule = method.getBeanType().getAnnotation(BusinessAccess.class);
              if (rule == null || !ConsoleCatalog.SYSTEM.equals(rule.value())) return;
              for (var verb : info.getMethodsCondition().getMethods())
                for (String path : info.getPatternValues())
                  if (!path.startsWith("/api/me")) endpoints.add(verb.name() + " " + path);
            });
    Set<String> catalog = new TreeSet<>();
    ConsoleCatalog.APIS.forEach(api -> catalog.add(api.method() + " " + api.path()));
    assertEquals(endpoints, catalog);
    Set<String> codes = new HashSet<>();
    ConsoleCatalog.ENTRIES.forEach(e -> codes.add(e.code()));
    ConsoleCatalog.APIS.forEach(api -> assertTrue(codes.containsAll(api.grantedBy()), api.path()));
  }

  @Test
  @DisplayName("FR-38 启动登记幂等：重复初始化不产生重复数据；被删掉的内置授权在下次启动时补齐")
  void FR38_registrationIsIdempotentAndRestoresBuiltInGrants() throws Exception {
    bootstrap.initialize("admin", PASSWORD);
    bootstrap.initialize("admin", PASSWORD);
    assertEquals(ConsoleCatalog.ENTRIES.size(), count("select count(*) from sys_permission p join biz_system s on s.id = p.system_id where s.code = 'rbac'"));
    assertEquals(ConsoleCatalog.APIS.size(), count("select count(*) from api_resource r join biz_system s on s.id = r.system_id where s.code = 'rbac'"));
    long bindings = ConsoleCatalog.APIS.stream().mapToLong(api -> api.grantedBy().size()).sum();
    assertEquals(bindings, count("select count(*) from permission_api"));
    long role = db.queryForObject("select id from sys_role where code = 'rbac-admin'", Long.class);
    assertEquals(ConsoleCatalog.ENTRIES.size(), count("select count(*) from sys_role_permission where role_id = " + role));
    long adminId = db.queryForObject("select id from sys_user where display_name = '系统管理员'", Long.class);

    long overview = db.queryForObject("select id from sys_permission where code = 'rbac.overview'", Long.class);
    call("DELETE", "/api/roles/" + role + "/permissions/" + overview, admin, null, 204);
    call("GET", "/api/system-info", admin, null, 403);
    bootstrap.initialize("admin", PASSWORD);
    call("GET", "/api/system-info", admin, null, 200);

    db.update("delete from sys_user_role where user_id = ?", adminId);
    call("GET", "/api/users", admin, null, 403);
    bootstrap.initialize("admin", PASSWORD);
    call("GET", "/api/users", admin, null, 200);
  }

  @Test
  @DisplayName("FR-38 管理员的导航包含全部 11 个控制台菜单，按钮挂在所属菜单下")
  void FR38_administratorSeesEveryConsoleMenu() throws Exception {
    JsonNode menu = result(call("GET", "/api/me/menu?system=rbac", admin, null, 200));
    List<String> codes = new ArrayList<>();
    menu.forEach(node -> codes.add(node.get("permission").get("code").asString()));
    assertEquals(
        ConsoleCatalog.ENTRIES.stream()
            .filter(e -> e.parent() == null)
            .map(ConsoleCatalog.Entry::code)
            .toList(),
        codes);
    JsonNode users = menu.get(1);
    assertEquals("/users", users.get("permission").get("menuPath").asString());
    Set<String> buttons = new TreeSet<>();
    users.get("children").forEach(c -> buttons.add(c.get("permission").get("code").asString()));
    assertEquals(Set.of("rbac.users.write", "rbac.directory.sync"), buttons);
    call("GET", "/api/me/menu?system=nowhere", admin, null, 404);
  }

  @Test
  @DisplayName("FR-38 菜单根据后端授权动态显示：授予后出现、页面接口放行，撤销后下一次请求立即消失并返回 403")
  void FR38_menuAndApisFollowGrants() throws Exception {
    assertEquals(0, result(call("GET", "/api/me/menu?system=rbac", auditor, null, 200)).size());
    call("GET", "/api/audit-logs", auditor, null, 403);

    long role = result(call("POST", "/api/roles", admin, Map.of("code", "rbac-auditor", "name", "审计员"), 201)).get("id").asLong();
    long audit = db.queryForObject("select id from sys_permission where code = 'rbac.audit'", Long.class);
    long hierarchy = db.queryForObject("select id from sys_permission where code = 'rbac.hierarchy'", Long.class);
    call("PUT", "/api/roles/" + role + "/permissions/" + audit, admin, null, 204);
    call("PUT", "/api/users/" + auditorId + "/roles/" + role, admin, null, 204);

    JsonNode menu = result(call("GET", "/api/me/menu?system=rbac", auditor, null, 200));
    assertEquals(1, menu.size());
    assertEquals("rbac.audit", menu.get(0).get("permission").get("code").asString());
    call("GET", "/api/audit-logs", auditor, null, 200);
    call("GET", "/api/systems", auditor, null, 200);
    call("GET", "/api/users", auditor, null, 403);
    call("PUT", "/api/roles/" + role + "/inherits/" + role, auditor, null, 403);

    call("PUT", "/api/roles/" + role + "/permissions/" + hierarchy, admin, null, 204);
    assertEquals(2, result(call("GET", "/api/me/menu?system=rbac", auditor, null, 200)).size());
    call("GET", "/api/role-hierarchy", auditor, null, 200);
    call("PUT", "/api/roles/" + role + "/inherits/" + role, auditor, null, 403);

    call("DELETE", "/api/roles/" + role + "/permissions/" + audit, admin, null, 204);
    menu = result(call("GET", "/api/me/menu?system=rbac", auditor, null, 200));
    assertEquals(1, menu.size());
    assertEquals("rbac.hierarchy", menu.get(0).get("permission").get("code").asString());
    call("GET", "/api/audit-logs", auditor, null, 403);
  }

  @Test
  @DisplayName("FR-38 个人接口只要求登录；没有登记访问规则的接口一律拒绝；控制台管理员也不能绕过")
  void FR38_personalEndpointsAndFailClosed() throws Exception {
    call("GET", "/api/me", auditor, null, 200);
    call("GET", "/api/me/permissions", auditor, null, 200);
    call("GET", "/api/me/permission-origins", auditor, null, 200);
    JsonNode denied = result(call("GET", "/api/test/unregistered", admin, null, 403));
    assertEquals("FORBIDDEN", denied.get("code").asString());
  }

  long count(String sql) {
    return db.queryForObject(sql, Long.class);
  }

  JsonNode result(MvcResult result) {
    byte[] body = result.getResponse().getContentAsByteArray();
    return body.length == 0
        ? json.createObjectNode()
        : json.readTree(new String(body, StandardCharsets.UTF_8));
  }

  MvcResult call(String method, String path, String token, Object body, int status)
      throws Exception {
    var b =
        request(org.springframework.http.HttpMethod.valueOf(method), path)
            .contentType(MediaType.APPLICATION_JSON);
    if (token != null) b.header("Authorization", "Bearer " + token);
    if (body != null) b.content(json.writeValueAsString(body));
    return mvc.perform(b).andExpect(status().is(status)).andReturn();
  }

  String login(String name) throws Exception {
    return result(
            call(
                "POST",
                "/api/auth/login",
                null,
                Map.of("username", name, "password", PASSWORD),
                200))
        .get("accessToken")
        .asString();
  }
}
