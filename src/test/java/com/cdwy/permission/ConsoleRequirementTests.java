package com.cdwy.permission;

import static com.cdwy.permission.support.TestUrls.url;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.service.auth.AdminBootstrapService;
import com.cdwy.permission.support.RequestTransactionBoundary;
import com.cdwy.permission.support.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 控制台需求的等价类、边界值与异常测试，补齐每条需求的三类用例。
 *
 * <p>边界值取自请求 DTO 与密码策略的限制：在上限处必须接受，超出一位必须拒绝；分页取 1 与 100、越界取 0 与 101。
 * 等价类覆盖一组合法输入的正常结果，异常覆盖非法输入、缺少授权与引用冲突。
 *
 * <p>测试数据：应用 course 下菜单 course:menu 与其按钮 course:list，按钮可绑定示例接口 GET /api/demo/courses；
 * 角色 teacher；普通用户 worker。
 */
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
class ConsoleRequirementTests {
  static final String PASSWORD = "Review-2026!";
  private static final String DEMO_COURSES_URL = "/api/demo/courses";
  private static final String DEPARTMENTS_URL = "/api/departments";
  private static final String DEPARTMENT_URL = "/api/departments/{departmentId}";
  private static final String LOGIN_URL = "/api/auth/login";
  private static final String ME_URL = "/api/me";
  private static final String MY_MENU_URL = "/api/me/menu?system={system}";
  private static final String MY_PASSWORD_URL = "/api/me/password";
  private static final String MY_PERMISSIONS_URL = "/api/me/permissions";
  private static final String MY_PERMISSION_ORIGINS_URL = "/api/me/permission-origins";
  private static final String PERMISSIONS_URL = "/api/permissions";
  private static final String PERMISSION_TREE_URL = "/api/permissions/tree?systemId={systemId}";
  private static final String PERMISSION_RESOURCES_URL = "/api/permissions/{permissionId}/resources";
  private static final String PERMISSION_RESOURCE_URL =
      "/api/permissions/{permissionId}/resources/{resourceId}";
  private static final String RESOURCES_URL = "/api/resources";
  private static final String RESOURCE_STATUS_URL = "/api/resources/{resourceId}/status";
  private static final String ROLES_URL = "/api/roles";
  private static final String ROLE_URL = "/api/roles/{roleId}";
  private static final String ROLE_STATUS_URL = "/api/roles/{roleId}/status";
  private static final String ROLE_PERMISSIONS_URL = "/api/roles/{roleId}/permissions";
  private static final String ROLE_PERMISSION_URL = "/api/roles/{roleId}/permissions/{permissionId}";
  private static final String ROLE_INHERIT_URL = "/api/roles/{roleId}/inherits/{inheritedId}";
  private static final String ROLE_EFFECTIVE_URL = "/api/roles/{roleId}/effective-permissions";
  private static final String SYSTEMS_URL = "/api/systems";
  private static final String SYSTEM_STATUS_URL = "/api/systems/{systemId}/status";
  private static final String USERS_URL = "/api/users";
  private static final String USERS_PAGE_URL = "/api/users?page={page}&pageSize={pageSize}";
  private static final String USER_ROLE_URL = "/api/users/{userId}/roles/{roleId}";
  private static final String USER_STATUS_URL = "/api/users/{userId}/status";
  private static final String USER_ORIGINS_URL = "/api/users/{userId}/permission-origins";

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;
  String admin, employee;
  long user, system, role, menu, button, resource;

  @BeforeEach
  void fixture() throws Exception {
    TestDatabase.clear(db);
    bootstrap.initialize("admin", PASSWORD);
    admin = login("admin", PASSWORD);
    user = create(USERS_URL, Map.of("username", "worker", "displayName", "员工", "password", PASSWORD));
    employee = login("worker", PASSWORD);
    system = create(SYSTEMS_URL, Map.of("code", "course", "name", "课程管理系统"));
    role = create(ROLES_URL, Map.of("code", "teacher", "name", "教师"));
    menu =
        create(
            PERMISSIONS_URL,
            Map.of(
                "systemId", system, "code", "course:menu", "name", "课程管理", "type", "MENU",
                "menuPath", "/courses"));
    button =
        create(
            PERMISSIONS_URL,
            Map.of(
                "systemId", system, "code", "course:list", "name", "查看课程", "type", "BUTTON",
                "parentId", menu));
    resource =
        create(
            RESOURCES_URL,
            Map.of("systemId", system, "name", "课程列表", "method", "GET", "path", DEMO_COURSES_URL));
  }

  // ---------- FR-01 账号密码登录 ----------

  @Test
  @Tag("边界值")
  @DisplayName("FR-01 登录名最长 128 个字符、密码最长 256 个字符：上限内按账号校验返回 401，超出一位返回 400")
  void FR01_loginFieldLengthBoundaries() throws Exception {
    loginStatus("u".repeat(128), PASSWORD, 401);
    loginStatus("u".repeat(129), PASSWORD, 400);
    loginStatus("worker", "p".repeat(256), 401);
    loginStatus("worker", "p".repeat(257), 400);
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-01 登录名或密码为空、缺少请求体返回 400；账号不存在、已停用返回 401")
  void FR01_invalidLoginRequests() throws Exception {
    loginStatus("", PASSWORD, 400);
    loginStatus("worker", "", 400);
    call("POST", LOGIN_URL, null, null, 400);
    loginStatus("nobody", PASSWORD, 401);
    call("PUT", url(USER_STATUS_URL, user), admin, Map.of("enabled", false), 200);
    loginStatus("worker", PASSWORD, 401);
  }

  // ---------- FR-02 本人身份与改密 ----------

  @Test
  @Tag("边界值")
  @DisplayName("FR-02 新密码边界：8 个字符且三类字符可以，7 个字符或只有两类字符拒绝；72 字节可以，73 字节拒绝")
  void FR02_passwordPolicyBoundaries() throws Exception {
    String current = PASSWORD;
    String token = employee;
    assertEquals("PASSWORD_POLICY_VIOLATION", changePassword(token, current, "Abcde1!", 400));
    assertEquals("PASSWORD_POLICY_VIOLATION", changePassword(token, current, "abcdefgh1", 400));
    changePassword(token, current, "Abcdef1!", 204);
    current = "Abcdef1!";
    token = login("worker", current);
    String bytes73 = "Aa1!" + "a".repeat(69);
    assertEquals("PASSWORD_POLICY_VIOLATION", changePassword(token, current, bytes73, 400));
    String bytes72 = "Aa1!" + "a".repeat(68);
    changePassword(token, current, bytes72, 204);
    call("GET", ME_URL, login("worker", bytes72), null, 200);
  }

  // ---------- FR-03 用户维护 ----------

  @Test
  @Tag("边界值")
  @DisplayName("FR-03 登录名 1～64 个字符可以、65 个拒绝；姓名 128 个字符可以、129 个拒绝；分页 pageSize 1 与 100 可以，0、101 与 page 0 拒绝")
  void FR03_userFieldAndPagingBoundaries() throws Exception {
    createUser("a", "单字", 201);
    createUser("b".repeat(64), "六十四", 201);
    createUser("c".repeat(65), "六十五", 400);
    createUser("max-name", "名".repeat(128), 201);
    createUser("over-name", "名".repeat(129), 400);
    JsonNode one = result(call("GET", url(USERS_PAGE_URL, 1, 1), admin, null, 200));
    assertEquals(1, one.get("items").size());
    assertTrue(one.get("total").asLong() > 1);
    assertEquals(100, result(call("GET", url(USERS_PAGE_URL, 1, 100), admin, null, 200)).get("pageSize").asInt());
    call("GET", url(USERS_PAGE_URL, 1, 0), admin, null, 400);
    call("GET", url(USERS_PAGE_URL, 1, 101), admin, null, 400);
    call("GET", url(USERS_PAGE_URL, 0, 20), admin, null, 400);
  }

  // ---------- FR-05 部门维护 ----------

  @Test
  @Tag("等价类")
  @DisplayName("FR-05 新建上级与下级部门、改名、调整上级、删除没有下级和人员的部门都成功")
  void FR05_departmentLifecycle() throws Exception {
    long head = create(DEPARTMENTS_URL, Map.of("name", "总公司"));
    long branch = create(DEPARTMENTS_URL, Map.of("name", "分公司"));
    long office = create(DEPARTMENTS_URL, Map.of("name", "办公室", "parentId", head));
    call("PUT", url(DEPARTMENT_URL, office), admin, Map.of("name", "综合办公室", "parentId", branch), 200);
    JsonNode moved = find(result(call("GET", DEPARTMENTS_URL, admin, null, 200)), office);
    assertEquals("综合办公室", moved.get("name").asString());
    assertEquals(branch, moved.get("parentId").asLong());
    call("DELETE", url(DEPARTMENT_URL, office), admin, null, 204);
    assertNull(find(result(call("GET", DEPARTMENTS_URL, admin, null, 200)), office));
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-05 部门名称 128 个字符可以、129 个拒绝；编码 64 位可以、65 位拒绝；只剩一个下级时仍不能删除，删掉下级后可以删除")
  void FR05_departmentBoundaries() throws Exception {
    create(DEPARTMENTS_URL, Map.of("name", "部".repeat(128)));
    call("POST", DEPARTMENTS_URL, admin, Map.of("name", "部".repeat(129)), 400);
    create(DEPARTMENTS_URL, Map.of("name", "编码上限", "code", "D".repeat(64)));
    call("POST", DEPARTMENTS_URL, admin, Map.of("name", "编码超长", "code", "D".repeat(65)), 400);
    long parent = create(DEPARTMENTS_URL, Map.of("name", "上级"));
    long child = create(DEPARTMENTS_URL, Map.of("name", "唯一下级", "parentId", parent));
    call("DELETE", url(DEPARTMENT_URL, parent), admin, null, 409);
    call("DELETE", url(DEPARTMENT_URL, child), admin, null, 204);
    call("DELETE", url(DEPARTMENT_URL, parent), admin, null, 204);
  }

  // ---------- FR-06 应用维护 ----------

  @Test
  @Tag("等价类")
  @DisplayName("FR-06 停用应用后其下菜单不再计入本人权限，重新启用后恢复")
  void FR06_systemStatusControlsItsPermissions() throws Exception {
    grantDemo();
    assertEquals(1, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
    call("PUT", url(SYSTEM_STATUS_URL, system), admin, Map.of("enabled", false), 200);
    assertEquals(0, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
    call("GET", DEMO_COURSES_URL, employee, null, 403);
    call("PUT", url(SYSTEM_STATUS_URL, system), admin, Map.of("enabled", true), 200);
    assertEquals(1, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
    call("GET", DEMO_COURSES_URL, employee, null, 200);
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-06 应用编码 1 位与 64 位可以、65 位拒绝；名称 128 个字符可以、129 个拒绝")
  void FR06_systemFieldBoundaries() throws Exception {
    create(SYSTEMS_URL, Map.of("code", "a", "name", "单字母编码"));
    create(SYSTEMS_URL, Map.of("code", "s".repeat(64), "name", "六十四位编码"));
    call("POST", SYSTEMS_URL, admin, Map.of("code", "t".repeat(65), "name", "超长编码"), 400);
    create(SYSTEMS_URL, Map.of("code", "max-name", "name", "应".repeat(128)));
    call("POST", SYSTEMS_URL, admin, Map.of("code", "over-name", "name", "应".repeat(129)), 400);
  }

  // ---------- FR-07 角色维护 ----------

  @Test
  @Tag("等价类")
  @DisplayName("FR-07 一人多角色取并集；停用其中一个角色只去掉它提供的权限")
  void FR07_multipleRolesUnion() throws Exception {
    long other = create(ROLES_URL, Map.of("code", "observer", "name", "督导"));
    call("PUT", url(ROLE_PERMISSION_URL, role, menu), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, other, button), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, other), admin, null, 204);
    assertEquals(Set.of("course:menu", "course:list"), codes(call("GET", MY_PERMISSIONS_URL, employee, null, 200)));
    call("PUT", url(ROLE_STATUS_URL, other), admin, Map.of("enabled", false), 200);
    assertEquals(Set.of("course:menu"), codes(call("GET", MY_PERMISSIONS_URL, employee, null, 200)));
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-07 角色编码 64 位可以、65 位拒绝；名称 128 个字符、描述 255 个字符可以，各超出一位拒绝")
  void FR07_roleFieldBoundaries() throws Exception {
    create(ROLES_URL, Map.of("code", "r".repeat(64), "name", "编码上限"));
    call("POST", ROLES_URL, admin, Map.of("code", "q".repeat(65), "name", "编码超长"), 400);
    create(ROLES_URL, Map.of("code", "max-name", "name", "角".repeat(128), "description", "述".repeat(255)));
    call("POST", ROLES_URL, admin, Map.of("code", "over-name", "name", "角".repeat(129)), 400);
    call("POST", ROLES_URL, admin, Map.of("code", "over-desc", "name", "描述超长", "description", "述".repeat(256)), 400);
  }

  // ---------- FR-08 前端权限树 ----------

  @Test
  @Tag("等价类")
  @DisplayName("FR-08 同一应用的菜单下挂按钮与子菜单，权限树按层级返回")
  void FR08_menuTreeIsReturnedByLevel() throws Exception {
    long sub =
        create(
            PERMISSIONS_URL,
            Map.of(
                "systemId", system, "code", "course:sub", "name", "子菜单", "type", "MENU",
                "parentId", menu, "menuPath", "/courses/sub"));
    JsonNode tree = result(call("GET", url(PERMISSION_TREE_URL, system), admin, null, 200));
    assertEquals(1, tree.size());
    JsonNode root = tree.get(0);
    assertEquals(menu, root.get("permission").get("id").asLong());
    Set<Long> children = new HashSet<>();
    root.get("children").forEach(c -> children.add(c.get("permission").get("id").asLong()));
    assertEquals(Set.of(button, sub), children);
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-08 权限码与名称 128 个字符可以、129 个拒绝；菜单地址 255 个字符可以、256 个拒绝")
  void FR08_permissionFieldBoundaries() throws Exception {
    createMenu("m".repeat(128), "名", "/a", 201);
    createMenu("n".repeat(129), "名", "/a", 400);
    createMenu("max-name", "名".repeat(128), "/a", 201);
    createMenu("over-name", "名".repeat(129), "/a", 400);
    createMenu("max-path", "地址上限", "/" + "p".repeat(254), 201);
    createMenu("over-path", "地址超长", "/" + "p".repeat(255), 400);
  }

  // ---------- FR-09 API 资源维护 ----------

  @Test
  @Tag("等价类")
  @DisplayName("FR-09 同一后端权限码可以登记在多个接口上；接口可以单独停用和启用")
  void FR09_backendCodeCoversSeveralResources() throws Exception {
    long create = createResource("新建学期", "POST", "/api/terms", "TC", null, 201);
    long update = createResource("修改学期", "PATCH", "/api/terms/{termId}", "TC", null, 201);
    call("PUT", url(RESOURCE_STATUS_URL, update), admin, Map.of("active", false), 200);
    JsonNode all = result(call("GET", RESOURCES_URL, admin, null, 200));
    assertEquals("TC", find(all, create).get("code").asString());
    assertEquals("TC", find(all, update).get("code").asString());
    assertFalse(find(all, update).get("active").asBoolean());
    call("PUT", url(RESOURCE_STATUS_URL, update), admin, Map.of("active", true), 200);
    assertTrue(find(result(call("GET", RESOURCES_URL, admin, null, 200)), update).get("active").asBoolean());
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-09 路径 255 个字符可以、256 个拒绝；名称 128 个字符可以、129 个拒绝；权限码 64 位、operationId 128 位可以，各超出一位拒绝")
  void FR09_resourceFieldBoundaries() throws Exception {
    createResource("路径上限", "GET", "/" + "p".repeat(254), null, null, 201);
    createResource("路径超长", "GET", "/" + "q".repeat(255), null, null, 400);
    createResource("名".repeat(128), "GET", "/name-max", null, null, 201);
    createResource("名".repeat(129), "GET", "/name-over", null, null, 400);
    createResource("权限码上限", "GET", "/code-max", "C".repeat(64), null, 201);
    createResource("权限码超长", "GET", "/code-over", "C".repeat(65), null, 400);
    createResource("操作上限", "GET", "/op-max", null, "o".repeat(128), 201);
    createResource("操作超长", "GET", "/op-over", null, "o".repeat(129), 400);
  }

  // ---------- FR-10 ~ FR-12 三组关系 ----------

  @Test
  @Tag("边界值")
  @DisplayName("FR-10 用户只有一个角色时撤回它，权限立即清空；没有任何角色的用户权限为空")
  void FR10_lastRoleRevocation() throws Exception {
    assertEquals(0, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
    grantDemo();
    call("GET", DEMO_COURSES_URL, employee, null, 200);
    call("DELETE", url(USER_ROLE_URL, user, role), admin, null, 204);
    assertEquals(0, result(call("GET", MY_PERMISSIONS_URL, employee, null, 200)).size());
    call("GET", DEMO_COURSES_URL, employee, null, 403);
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-11 同一权限重复授予只保留一条；角色唯一的权限撤回后该角色不再提供任何权限")
  void FR11_singlePermissionOfRole() throws Exception {
    grantDemo();
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    assertEquals(1, result(call("GET", url(ROLE_PERMISSIONS_URL, role), admin, null, 200)).size());
    call("DELETE", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    assertEquals(0, result(call("GET", url(ROLE_PERMISSIONS_URL, role), admin, null, 200)).size());
    call("GET", DEMO_COURSES_URL, employee, null, 403);
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-12 按钮未绑定任何接口时不能调用；绑定一个接口即可调用，重复绑定只保留一条")
  void FR12_bindingCountBoundaries() throws Exception {
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
    assertEquals(0, result(call("GET", url(PERMISSION_RESOURCES_URL, button), admin, null, 200)).size());
    call("GET", DEMO_COURSES_URL, employee, null, 403);
    call("PUT", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    call("PUT", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    assertEquals(1, result(call("GET", url(PERMISSION_RESOURCES_URL, button), admin, null, 200)).size());
    call("GET", DEMO_COURSES_URL, employee, null, 200);
  }

  // ---------- FR-13 个人导航 ----------

  @Test
  @Tag("边界值")
  @DisplayName("FR-13 没有任何授权时导航为空；只授权第三层按钮时，两层祖先菜单都作为不授权的容器出现")
  void FR13_navigationDepthBoundaries() throws Exception {
    assertEquals(0, result(call("GET", url(MY_MENU_URL, "course"), employee, null, 200)).size());
    long sub =
        create(
            PERMISSIONS_URL,
            Map.of(
                "systemId", system, "code", "course:sub", "name", "子菜单", "type", "MENU",
                "parentId", menu, "menuPath", "/courses/sub"));
    long leaf =
        create(
            PERMISSIONS_URL,
            Map.of("systemId", system, "code", "course:leaf", "name", "叶子按钮", "type", "BUTTON", "parentId", sub));
    call("PUT", url(ROLE_PERMISSION_URL, role, leaf), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
    JsonNode tree = result(call("GET", url(MY_MENU_URL, "course"), employee, null, 200));
    JsonNode top = tree.get(0);
    assertEquals(menu, top.get("permission").get("id").asLong());
    assertFalse(top.get("granted").asBoolean());
    JsonNode middle = top.get("children").get(0);
    assertEquals(sub, middle.get("permission").get("id").asLong());
    assertFalse(middle.get("granted").asBoolean());
    assertTrue(middle.get("children").get(0).get("granted").asBoolean());
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-13 没有令牌或账号停用后不能读取个人导航")
  void FR13_navigationRequiresValidIdentity() throws Exception {
    call("GET", url(MY_MENU_URL, "course"), null, null, 401);
    call("PUT", url(USER_STATUS_URL, user), admin, Map.of("enabled", false), 200);
    call("GET", url(MY_MENU_URL, "course"), employee, null, 401);
  }

  // ---------- FR-14 真实请求鉴权 ----------

  @Test
  @Tag("边界值")
  @DisplayName("FR-14 授权链完整时放行；停用链上唯一的 API 资源立即拒绝，重新启用后恢复")
  void FR14_singleResourceInChain() throws Exception {
    grantDemo();
    call("GET", DEMO_COURSES_URL, employee, null, 200);
    call("PUT", url(RESOURCE_STATUS_URL, resource), admin, Map.of("active", false), 200);
    call("GET", DEMO_COURSES_URL, employee, null, 403);
    call("PUT", url(RESOURCE_STATUS_URL, resource), admin, Map.of("active", true), 200);
    call("GET", DEMO_COURSES_URL, employee, null, 200);
  }

  // ---------- FR-15 状态独立变更 ----------

  @Test
  @Tag("边界值")
  @DisplayName("FR-15 把状态设成当前值是幂等的：重复启用、重复停用都返回 200，名称等普通字段不变")
  void FR15_settingCurrentStatusIsIdempotent() throws Exception {
    JsonNode enabled = result(call("PUT", url(ROLE_STATUS_URL, role), admin, Map.of("enabled", true), 200));
    assertTrue(enabled.get("enabled").asBoolean());
    call("PUT", url(ROLE_STATUS_URL, role), admin, Map.of("enabled", false), 200);
    JsonNode again = result(call("PUT", url(ROLE_STATUS_URL, role), admin, Map.of("enabled", false), 200));
    assertFalse(again.get("enabled").asBoolean());
    assertEquals("教师", again.get("name").asString());
  }

  // ---------- FR-17 ~ FR-20 角色继承 ----------

  @Test
  @Tag("边界值")
  @DisplayName("FR-17 三层继承链末端的权限传到链首；断开中间一条边后下一次请求立即拒绝")
  void FR17_longestChainAndMiddleCut() throws Exception {
    long head = role("head"), middle = role("middle");
    call("PUT", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    edge(head, middle, 204);
    edge(middle, role, 204);
    call("PUT", url(USER_ROLE_URL, user, head), admin, null, 204);
    call("GET", DEMO_COURSES_URL, employee, null, 200);
    call("DELETE", url(ROLE_INHERIT_URL, middle, role), admin, null, 204);
    call("GET", DEMO_COURSES_URL, employee, null, 403);
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-17 继承不存在的角色返回 404；普通用户不能建立继承，返回 403")
  void FR17_invalidInheritanceRequests() throws Exception {
    call("PUT", url(ROLE_INHERIT_URL, role, 999_999L), admin, null, 404);
    call("PUT", url(ROLE_INHERIT_URL, 999_999L, role), admin, null, 404);
    long other = role("other");
    call("PUT", url(ROLE_INHERIT_URL, other, role), employee, null, 403);
  }

  @Test
  @Tag("等价类")
  @Tag("边界值")
  @DisplayName("FR-18 合法的边可以建立且重复建立幂等；三个角色成链后，闭合成环的那条边被拒绝，指向链外的边可以建立")
  void FR18_validEdgesAndClosingEdge() throws Exception {
    long a = role("a"), b = role("b"), c = role("c"), d = role("d");
    edge(a, b, 204);
    edge(a, b, 204);
    edge(b, c, 204);
    assertEquals("ROLE_INHERITANCE_CYCLE", result(call("PUT", url(ROLE_INHERIT_URL, c, a), admin, null, 400)).get("code").asString());
    edge(c, d, 204);
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-19 直接授权的来源路径只有角色自身；经两层继承得到的来源给出完整路径")
  void FR19_sourcePathLengths() throws Exception {
    long head = role("head"), middle = role("middle");
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    edge(head, middle, 204);
    edge(middle, role, 204);
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
    JsonNode direct = result(call("GET", MY_PERMISSION_ORIGINS_URL, employee, null, 200)).get(0).get("sources").get(0);
    assertFalse(direct.get("inherited").asBoolean());
    assertEquals(List.of(role), ids(direct.get("path")));
    call("DELETE", url(USER_ROLE_URL, user, role), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, head), admin, null, 204);
    JsonNode inherited = result(call("GET", MY_PERMISSION_ORIGINS_URL, employee, null, 200)).get(0).get("sources").get(0);
    assertTrue(inherited.get("inherited").asBoolean());
    assertEquals(List.of(head, middle, role), ids(inherited.get("path")));
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-19 预览不存在的角色返回 404；没有令牌不能查看本人权限来源")
  void FR19_invalidOriginRequests() throws Exception {
    call("GET", url(ROLE_EFFECTIVE_URL, 999_999L), admin, null, 404);
    call("GET", MY_PERMISSION_ORIGINS_URL, null, null, 401);
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-20 普通用户只能经本人接口查来源：即使在管理接口上填自己的编号也返回 403")
  void FR20_ownIdOnAdministratorRouteIsStillForbidden() throws Exception {
    call("GET", MY_PERMISSION_ORIGINS_URL, employee, null, 200);
    call("GET", url(USER_ORIGINS_URL, user), employee, null, 403);
    call("GET", url(USER_ORIGINS_URL, user), admin, null, 200);
  }

  // ---------- FR-23 RBAC1 核心双实现 ----------

  /** 本类按默认配置运行，没有设置 app.backend-strategy；DDD 子类改成 ddd。 */
  String strategy() {
    return "structured";
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-23 不配置设计路线时按默认的 structured 装配，配置 ddd 时为 ddd；系统信息报告实际装载的路线")
  void FR23_defaultStrategyIsStructured() throws Exception {
    JsonNode info = result(call("GET", "/api/system-info", admin, null, 200));
    assertEquals(strategy(), info.get("backendStrategy").asString());
    assertEquals(strategy(), info.get("modules").get("hierarchy").asString());
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-23 没有令牌或没有“工作台”菜单授权的普通用户不能查看实际装载的路线")
  void FR23_strategyIsNotVisibleWithoutGrant() throws Exception {
    call("GET", "/api/system-info", null, null, 401);
    call("GET", "/api/system-info", employee, null, 403);
  }

  // ---------- 辅助方法 ----------

  /** 完整授权链：按钮绑定示例接口、角色获得按钮、用户获得角色。 */
  void grantDemo() throws Exception {
    call("PUT", url(PERMISSION_RESOURCE_URL, button, resource), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, role, button), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, user, role), admin, null, 204);
  }

  long role(String code) throws Exception {
    return create(ROLES_URL, Map.of("code", code, "name", code));
  }

  void edge(long senior, long inherited, int status) throws Exception {
    call("PUT", url(ROLE_INHERIT_URL, senior, inherited), admin, null, status);
  }

  void loginStatus(String username, String password, int status) throws Exception {
    call("POST", LOGIN_URL, null, Map.of("username", username, "password", password), status);
  }

  /** 本人改密；失败时返回业务错误码，成功返回 null。 */
  String changePassword(String token, String current, String next, int status) throws Exception {
    MvcResult r =
        call("PUT", MY_PASSWORD_URL, token, Map.of("currentPassword", current, "newPassword", next), status);
    return status >= 400 ? result(r).get("code").asString() : null;
  }

  void createUser(String username, String displayName, int status) throws Exception {
    call("POST", USERS_URL, admin, Map.of("username", username, "displayName", displayName, "password", PASSWORD), status);
  }

  void createMenu(String code, String name, String path, int status) throws Exception {
    call(
        "POST",
        PERMISSIONS_URL,
        admin,
        Map.of("systemId", system, "code", code, "name", name, "type", "MENU", "menuPath", path),
        status);
  }

  long createResource(String name, String method, String path, String code, String operationId, int status)
      throws Exception {
    Map<String, Object> body = new HashMap<>(Map.of("systemId", system, "name", name, "method", method, "path", path));
    if (code != null) body.put("code", code);
    if (operationId != null) body.put("operationId", operationId);
    MvcResult r = call("POST", RESOURCES_URL, admin, body, status);
    return status == 201 ? result(r).get("id").asLong() : 0;
  }

  JsonNode find(JsonNode list, long id) {
    for (JsonNode node : list) if (node.get("id").asLong() == id) return node;
    return null;
  }

  Set<String> codes(MvcResult r) {
    Set<String> codes = new HashSet<>();
    result(r).forEach(p -> codes.add(p.get("code").asString()));
    return codes;
  }

  List<Long> ids(JsonNode array) {
    List<Long> ids = new ArrayList<>();
    array.forEach(n -> ids.add(n.asLong()));
    return ids;
  }

  JsonNode result(MvcResult result) {
    return json.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  MockHttpServletRequestBuilder send(String method, String path, String token, Object body) {
    MockHttpServletRequestBuilder b =
        request(org.springframework.http.HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON);
    if (token != null) b.header("Authorization", "Bearer " + token);
    if (body != null) b.content(json.writeValueAsString(body));
    return b;
  }

  MvcResult call(String method, String path, String token, Object body, int status) throws Exception {
    return mvc.perform(send(method, path, token, body)).andExpect(status().is(status)).andReturn();
  }

  long create(String path, Object input) throws Exception {
    return result(call("POST", path, admin, input, 201)).get("id").asLong();
  }

  String login(String name, String password) throws Exception {
    return result(call("POST", LOGIN_URL, null, Map.of("username", name, "password", password), 200))
        .get("accessToken")
        .asString();
  }
}
