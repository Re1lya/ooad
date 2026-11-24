package com.cdwy.permission;

import static com.cdwy.permission.support.TestUrls.url;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.config.BusinessAccess;
import com.cdwy.permission.service.auth.AdminBootstrapService;
import com.cdwy.permission.service.auth.ConsoleCatalog;
import com.cdwy.permission.support.RequestTransactionBoundary;
import com.cdwy.permission.support.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
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
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(
    properties = {
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes"
    })
@AutoConfigureMockMvc
class ConsoleRbacTests {
  private static final String AUDIT_LOGS_URL = "/api/audit-logs";
  private static final String LOGIN_URL = "/api/auth/login";
  private static final String ME_URL = "/api/me";
  private static final String MY_MENU_OF_SYSTEM_URL = "/api/me/menu?system={system}";
  private static final String MY_PERMISSIONS_URL = "/api/me/permissions";
  private static final String MY_PERMISSION_ORIGINS_URL = "/api/me/permission-origins";
  private static final String ROLES_URL = "/api/roles";
  private static final String ROLE_HIERARCHY_URL = "/api/role-hierarchy";
  private static final String ROLE_INHERIT_URL = "/api/roles/{roleId}/inherits/{inheritedId}";
  private static final String ROLE_PERMISSION_URL =
      "/api/roles/{roleId}/permissions/{permissionId}";
  private static final String SYSTEMS_URL = "/api/systems";
  private static final String SYSTEM_INFO_URL = "/api/system-info";
  private static final String UNREGISTERED_URL = "/api/test/unregistered";
  private static final String USERS_URL = "/api/users";
  private static final String USER_ROLE_URL = "/api/users/{userId}/roles/{roleId}";

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
    TestDatabase.clear(db);
    bootstrap.initialize("admin", PASSWORD);
    admin = login("admin");
    auditorId =
        result(
                call(
                    "POST",
                    USERS_URL,
                    admin,
                    Map.of("username", "auditor", "displayName", "审计员", "password", PASSWORD),
                    201))
            .get("id")
            .asLong();
    auditor = login("auditor");
  }

  @Test
  @Tag("等价类")
  @DisplayName("FR-38 每个标注为 rbac 的管理接口（个人接口除外）都在内置目录中登记，且目录中没有多余接口")
  void FR38_catalogCoversEveryConsoleEndpoint() {
    Set<String> endpoints = new TreeSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (info, method) -> {
              BusinessAccess rule = method.getBeanType().getAnnotation(BusinessAccess.class);
              if (rule == null || !ConsoleCatalog.SYSTEM.equals(rule.value())) return;
              for (RequestMethod verb : info.getMethodsCondition().getMethods())
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
  @Tag("等价类")
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
    call("DELETE", url(ROLE_PERMISSION_URL, role, overview), admin, null, 204);
    call("GET", SYSTEM_INFO_URL, admin, null, 403);
    bootstrap.initialize("admin", PASSWORD);
    call("GET", SYSTEM_INFO_URL, admin, null, 200);

    db.update("delete from sys_user_role where user_id = ?", adminId);
    call("GET", USERS_URL, admin, null, 403);
    bootstrap.initialize("admin", PASSWORD);
    call("GET", USERS_URL, admin, null, 200);
  }

  /**
   * FR-38：接口已拒绝停用内置对象，但早先的数据或直接改库仍可能留下停用状态；启动登记要把它们恢复启用，管理员不会被永久锁在外面。
   */
  @Test
  @Tag("异常")
  @DisplayName("FR-38 内置应用、管理员角色、内置菜单与管理接口被停用时管理员进不了控制台，下次启动登记恢复启用")
  void FR38_startupRestoresDisabledBuiltIns() throws Exception {
    db.update("update biz_system set enabled = 0 where code = 'rbac'");
    db.update("update sys_role set enabled = 0 where code = 'rbac-admin'");
    db.update("update sys_permission set active = 0 where code = 'rbac.users'");
    db.update(
        "update api_resource set active = 0 where method = 'GET' and path = '/api/users'"
            + " and system_id = (select id from biz_system where code = 'rbac')");
    call("GET", USERS_URL, admin, null, 403);
    bootstrap.initialize("admin", PASSWORD);
    call("GET", USERS_URL, admin, null, 200);
    assertEquals(1, count("select count(*) from biz_system where code = 'rbac' and enabled = 1"));
    assertEquals(1, count("select count(*) from sys_role where code = 'rbac-admin' and enabled = 1"));
    assertEquals(0, count("select count(*) from sys_permission where code like 'rbac.%' and active = 0"));
    assertEquals(
        0,
        count(
            "select count(*) from api_resource r join biz_system s on s.id = r.system_id"
                + " where s.code = 'rbac' and r.active = 0"));
  }

  @Test
  @Tag("等价类")
  @DisplayName("FR-38 管理员的导航包含全部 11 个控制台菜单，按钮挂在所属菜单下")
  void FR38_administratorSeesEveryConsoleMenu() throws Exception {
    JsonNode menu = result(call("GET", url(MY_MENU_OF_SYSTEM_URL, "rbac"), admin, null, 200));
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
    call("GET", url(MY_MENU_OF_SYSTEM_URL, "nowhere"), admin, null, 404);
  }

  @Test
  @Tag("等价类")
  @Tag("异常")
  @DisplayName("FR-38 菜单根据后端授权动态显示：授予后出现、页面接口放行，撤销后下一次请求立即消失并返回 403")
  void FR38_menuAndApisFollowGrants() throws Exception {
    assertEquals(0, result(call("GET", url(MY_MENU_OF_SYSTEM_URL, "rbac"), auditor, null, 200)).size());
    call("GET", AUDIT_LOGS_URL, auditor, null, 403);

    long role = result(call("POST", ROLES_URL, admin, Map.of("code", "rbac-auditor", "name", "审计员"), 201)).get("id").asLong();
    long audit = db.queryForObject("select id from sys_permission where code = 'rbac.audit'", Long.class);
    long hierarchy = db.queryForObject("select id from sys_permission where code = 'rbac.hierarchy'", Long.class);
    call("PUT", url(ROLE_PERMISSION_URL, role, audit), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, auditorId, role), admin, null, 204);

    JsonNode menu = result(call("GET", url(MY_MENU_OF_SYSTEM_URL, "rbac"), auditor, null, 200));
    assertEquals(1, menu.size());
    assertEquals("rbac.audit", menu.get(0).get("permission").get("code").asString());
    call("GET", AUDIT_LOGS_URL, auditor, null, 200);
    call("GET", SYSTEMS_URL, auditor, null, 200);
    call("GET", USERS_URL, auditor, null, 403);
    call("PUT", url(ROLE_INHERIT_URL, role, role), auditor, null, 403);

    call("PUT", url(ROLE_PERMISSION_URL, role, hierarchy), admin, null, 204);
    assertEquals(2, result(call("GET", url(MY_MENU_OF_SYSTEM_URL, "rbac"), auditor, null, 200)).size());
    call("GET", ROLE_HIERARCHY_URL, auditor, null, 200);
    call("PUT", url(ROLE_INHERIT_URL, role, role), auditor, null, 403);

    call("DELETE", url(ROLE_PERMISSION_URL, role, audit), admin, null, 204);
    menu = result(call("GET", url(MY_MENU_OF_SYSTEM_URL, "rbac"), auditor, null, 200));
    assertEquals(1, menu.size());
    assertEquals("rbac.hierarchy", menu.get(0).get("permission").get("code").asString());
    call("GET", AUDIT_LOGS_URL, auditor, null, 403);
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-38 只有“用户管理”菜单时可以查看用户但不能新建；再授予“维护用户”按钮后才能新建")
  void FR38_readMenuWithoutWriteButton() throws Exception {
    long role = result(call("POST", ROLES_URL, admin, Map.of("code", "user-viewer", "name", "用户查看员"), 201)).get("id").asLong();
    long users = db.queryForObject("select id from sys_permission where code = 'rbac.users'", Long.class);
    long write = db.queryForObject("select id from sys_permission where code = 'rbac.users.write'", Long.class);
    call("PUT", url(ROLE_PERMISSION_URL, role, users), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, auditorId, role), admin, null, 204);
    Map<String, Object> person = Map.of("username", "new-person", "displayName", "新人员", "password", "Review-2026!");
    call("GET", USERS_URL, auditor, null, 200);
    call("POST", USERS_URL, auditor, person, 403);
    call("PUT", url(ROLE_PERMISSION_URL, role, write), admin, null, 204);
    call("POST", USERS_URL, auditor, person, 201);
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-38 个人接口只要求登录；没有登记访问规则的接口一律拒绝；控制台管理员也不能绕过")
  void FR38_personalEndpointsAndFailClosed() throws Exception {
    call("GET", ME_URL, auditor, null, 200);
    call("GET", MY_PERMISSIONS_URL, auditor, null, 200);
    call("GET", MY_PERMISSION_ORIGINS_URL, auditor, null, 200);
    JsonNode denied = result(call("GET", UNREGISTERED_URL, admin, null, 403));
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
    MockHttpServletRequestBuilder b =
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
                LOGIN_URL,
                null,
                Map.of("username", name, "password", PASSWORD),
                200))
        .get("accessToken")
        .asString();
  }
}
