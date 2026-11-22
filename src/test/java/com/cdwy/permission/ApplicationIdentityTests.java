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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * 课程管理系统接入验收（FR-27 ~ FR-37、NFR-11）：课程系统把统一身份基地址指向
 * /api/apps/course-project-management，按其契约（Apifox“统一身份鉴权”目录与课程系统客户端 record）调用 /auth/... 接口。
 *
 * <p>测试数据：应用 course-project-management 下菜单 MCO 绑定 API 权限码 CV，按钮 MCOR 绑定 CR，按钮 MTEC（添加教师）绑定统一身份目录的三个接口；
 * 角色 course-admin 继承 teacher，用户 lin 只分配 course-admin。部门：总公司（HQ）下有工程管理部与综合办公室，道路工程分公司（RD）下也有综合办公室。
 *
 * <p>响应字段按课程系统客户端 record 逐个断言：多一个或少一个字段都会失败。
 */
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(
    properties = {
      "permission.jwt.secret=first-review-integration-signing-secret-32-bytes",
      "permission.policy.external-max-days=7",
      "permission.policy.temporary-max-days=3",
      "permission.cors.allowed-origins=http://course.example, http://127.0.0.1:5173"
    })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ApplicationIdentityTests {
  static final String PASSWORD = "Review-2026!";
  static final String CPM = "course-project-management";
  static final String BASE = "/api/apps/" + CPM;
  private static final String APP_AUDIT_LOGS_URL = "/api/apps/{systemCode}/auth/audit-logs";
  private static final String APP_LOGIN_URL = "/api/apps/{systemCode}/auth/login";
  private static final String APP_ME_URL = "/api/apps/{systemCode}/auth/me";
  private static final String AUDIT_LOGS_OF_SYSTEM_URL = "/api/audit-logs?system={system}";
  private static final String AUDIT_LOGS_URL = "/api/audit-logs";
  private static final String CPM_API_PERMISSION_URL = BASE + "/auth/api-permissions/{operationId}";
  private static final String CPM_AUDIT_LOGS_BY_TARGET_URL =
      BASE + "/auth/audit-logs?target={target}";
  private static final String CPM_AUDIT_LOGS_PAGE_URL =
      BASE + "/auth/audit-logs?page={page}&pageSize={pageSize}";
  private static final String CPM_AUDIT_LOGS_URL = BASE + "/auth/audit-logs";
  private static final String CPM_DEPARTMENTS_PAGE_URL = BASE + "/auth/departments?page={page}";
  private static final String CPM_DEPARTMENTS_SEARCH_URL =
      BASE + "/auth/departments?keyword={keyword}";
  private static final String CPM_DEPARTMENTS_SIZE_URL =
      BASE + "/auth/departments?pageSize={pageSize}";
  private static final String CPM_DEPARTMENTS_URL = BASE + "/auth/departments";
  private static final String CPM_DEPARTMENT_USERS_SEARCH_URL =
      BASE + "/auth/departments/{departmentId}/users?keyword={keyword}";
  private static final String CPM_DEPARTMENT_USERS_URL =
      BASE + "/auth/departments/{departmentId}/users";
  private static final String CPM_LEGACY_IDENTITY_USER_URL = BASE + "/identity/users/SG000191";
  private static final String CPM_LOGIN_URL = BASE + "/auth/login";
  private static final String CPM_LOGOUT_URL = BASE + "/auth/logout";
  private static final String CPM_ME_URL = BASE + "/auth/me";
  private static final String CPM_PERMISSION_URL = BASE + "/auth/permissions/{permission}";
  private static final String CPM_SUBDEPARTMENTS_PAGE_URL =
      BASE + "/auth/departments?parentId={parentId}&page={page}&pageSize={pageSize}";
  private static final String CPM_SUBDEPARTMENTS_SEARCH_URL =
      BASE + "/auth/departments?parentId={parentId}&keyword={keyword}";
  private static final String CPM_SUBDEPARTMENTS_URL =
      BASE + "/auth/departments?parentId={parentId}";
  private static final String CPM_USER_URL = BASE + "/auth/users/{userId}";
  private static final String DEPARTMENTS_URL = "/api/departments";
  private static final String DEPARTMENT_URL = "/api/departments/{departmentId}";
  private static final String LOGIN_URL = "/api/auth/login";
  private static final String ME_URL = "/api/me";
  private static final String PERMISSIONS_URL = "/api/permissions";
  private static final String PERMISSION_RESOURCE_URL =
      "/api/permissions/{permissionId}/resources/{resourceId}";
  private static final String RESOURCES_URL = "/api/resources";
  private static final String RESOURCE_STATUS_URL = "/api/resources/{resourceId}/status";
  private static final String ROLES_URL = "/api/roles";
  private static final String ROLE_INHERIT_URL = "/api/roles/{roleId}/inherits/{inheritedId}";
  private static final String ROLE_PERMISSION_URL =
      "/api/roles/{roleId}/permissions/{permissionId}";
  private static final String ROLE_STATUS_URL = "/api/roles/{roleId}/status";
  private static final String SYSTEMS_URL = "/api/systems";
  private static final String SYSTEM_STATUS_URL = "/api/systems/{systemId}/status";
  private static final String USERS_BY_KEYWORD_URL = "/api/users?keyword={keyword}";
  private static final String USERS_PAGE_URL = "/api/users?page={page}&pageSize={pageSize}";
  private static final String USERS_SEARCH_URL =
      "/api/users?page={page}&pageSize={pageSize}&keyword={keyword}";
  private static final String USERS_URL = "/api/users";
  private static final String USER_ROLE_URL = "/api/users/{userId}/roles/{roleId}";

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;
  String admin, teacher, outsider;
  long lin, cpm, other, teacherRole, adminRole, menu, button, addTeacher, otherMenu, hq, rd;

  @BeforeEach
  void fixture() throws Exception {
    TestDatabase.clear(db);
    bootstrap.initialize("admin", PASSWORD);
    admin = login("admin", PASSWORD);
    hq = create(DEPARTMENTS_URL, Map.of("name", "总公司", "code", "HQ"));
    long engineering = create(DEPARTMENTS_URL, Map.of("name", "工程管理部", "parentId", hq, "code", "HQ-ENG"));
    create(DEPARTMENTS_URL, Map.of("name", "综合办公室", "parentId", hq, "code", "HQ-OFF"));
    rd = create(DEPARTMENTS_URL, Map.of("name", "道路工程分公司", "code", "RD"));
    long roadOffice = create(DEPARTMENTS_URL, Map.of("name", "综合办公室", "parentId", rd, "code", "RD-OFF"));
    lin =
        create(
            USERS_URL,
            Map.of(
                "username", "SG000001", "displayName", "林思远", "password", PASSWORD,
                "departmentId", engineering));
    create(
        USERS_URL,
        Map.of(
            "username", "SG000191", "displayName", "周雨桐", "password", PASSWORD, "departmentId",
            roadOffice));
    cpm = create(SYSTEMS_URL, Map.of("code", CPM, "name", "课程项目管理"));
    other = create(SYSTEMS_URL, Map.of("code", "oa", "name", "办公系统"));
    teacherRole = create(ROLES_URL, Map.of("code", "teacher", "name", "教师"));
    adminRole = create(ROLES_URL, Map.of("code", "course-admin", "name", "课程管理员"));
    menu = permission("MCO", "课程管理", "MENU", null);
    button = permission("MCOR", "审核课程", "BUTTON", menu);
    addTeacher = permission("MTEC", "添加教师", "BUTTON", menu);
    otherMenu =
        create(
            PERMISSIONS_URL,
            Map.of("systemId", other, "code", "OA-HOME", "name", "办公首页", "type", "MENU"));
    long viewApi = resource("管理员课程列表", "GET", "/admin/courses", "CV", "listAdminCourses");
    long reviewApi = resource("审核课程", "POST", "/courses/{courseId}/review", "CR", "reviewCourse");
    bind(menu, viewApi);
    bind(button, reviewApi);
    bind(addTeacher, resource("部门列表", "GET", "/auth/departments", null, "listIdentityDepartments"));
    bind(
        addTeacher,
        resource(
            "部门用户",
            "GET",
            "/auth/departments/{departmentId}/users",
            null,
            "listIdentityDepartmentUsers"));
    bind(addTeacher, resource("查询统一身份用户", "GET", "/auth/users/{userId}", "UL", "lookupIdentityUser"));
    call("PUT", url(ROLE_PERMISSION_URL, teacherRole, menu), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, teacherRole, otherMenu), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, adminRole, button), admin, null, 204);
    call("PUT", url(ROLE_PERMISSION_URL, adminRole, addTeacher), admin, null, 204);
    call("PUT", url(ROLE_INHERIT_URL, adminRole, teacherRole), admin, null, 204);
    call("PUT", url(USER_ROLE_URL, lin, adminRole), admin, null, 204);
    teacher = appLogin("SG000001", List.of(CPM));
    outsider = appLogin("SG000191", List.of(CPM));
  }

  // ---------- 登录、令牌目标系统、退出 ----------

  @Test
  @DisplayName("FR-31 登录不需要令牌，响应只有 accessToken、tokenType、expiresIn")
  void FR31_loginReturnsTokenContract() throws Exception {
    JsonNode token =
        result(
            call(
                "POST",
                CPM_LOGIN_URL,
                null,
                Map.of("username", "SG000001", "password", PASSWORD, "systems", List.of(CPM)),
                200));
    assertEquals(Set.of("accessToken", "tokenType", "expiresIn"), fields(token));
    assertEquals("Bearer", token.get("tokenType").asString());
    assertTrue(token.get("expiresIn").asLong() > 0);
    assertEquals(3, token.get("accessToken").asString().split("\\.").length);
  }

  @Test
  @DisplayName("FR-31 密码错误 401 UNAUTHORIZED；systems 缺当前应用、含未登记应用、为空或请求多出字段都 400")
  void FR31_loginRejectsBadCredentialsAndSystems() throws Exception {
    JsonNode wrong =
        result(
            call(
                "POST",
                CPM_LOGIN_URL,
                null,
                Map.of("username", "SG000001", "password", "wrong", "systems", List.of(CPM)),
                401));
    assertEquals("UNAUTHORIZED", wrong.get("code").asString());
    assertFalse(wrong.get("message").asString().isBlank());
    JsonNode missing =
        result(
            call(
                "POST",
                CPM_LOGIN_URL,
                null,
                Map.of("username", "SG000001", "password", PASSWORD, "systems", List.of("oa")),
                400));
    assertEquals("LOGIN_TARGET_NOT_IN_SYSTEMS", missing.get("code").asString());
    call(
        "POST",
        CPM_LOGIN_URL,
        null,
        Map.of("username", "SG000001", "password", PASSWORD, "systems", List.of(CPM, "nowhere")),
        400);
    call(
        "POST",
        CPM_LOGIN_URL,
        null,
        Map.of("username", "SG000001", "password", PASSWORD, "systems", List.of()),
        400);
    call(
        "POST",
        CPM_LOGIN_URL,
        null,
        Map.of("username", "SG000001", "password", PASSWORD, "systems", List.of(CPM), "extra", 1),
        400);
  }

  @Test
  @DisplayName("FR-31 令牌只能用于 aud 中的系统：课程令牌不能调控制台，控制台令牌不能调课程身份接口")
  void FR31_tokenAudienceIsEnforced() throws Exception {
    call("GET", ME_URL, teacher, null, 401);
    call("GET", CPM_ME_URL, login("SG000001", PASSWORD), null, 401);
    call("GET", url(APP_ME_URL, "oa"), teacher, null, 401);
    String both = appLogin("SG000001", List.of(CPM, "oa"));
    assertEquals(
        Set.of("OA-HOME"),
        texts(result(call("GET", url(APP_ME_URL, "oa"), both, null, 200)).get("permissions")));
    call("GET", CPM_ME_URL, both, null, 200);
  }

  @Test
  @DisplayName("FR-32 退出登录后同一令牌立即失效，重新登录可以继续使用")
  void FR32_logoutInvalidatesIssuedTokens() throws Exception {
    MvcResult out = call("POST", CPM_LOGOUT_URL, teacher, null, 204);
    assertEquals(0, out.getResponse().getContentLength());
    call("GET", CPM_ME_URL, teacher, null, 401);
    call("POST", CPM_LOGOUT_URL, null, null, 401);
    String again = appLogin("SG000001", List.of(CPM));
    call("GET", CPM_ME_URL, again, null, 200);
  }

  // ---------- 当前用户 ----------

  @Test
  @DisplayName("FR-27 /auth/me 返回 subject、继承得到的角色、菜单按钮码与绑定的 API 权限码、姓名和工号")
  void FR27_currentIdentityIncludesInheritedRolesAndCodes() throws Exception {
    JsonNode me = result(call("GET", CPM_ME_URL, teacher, null, 200));
    assertEquals(Set.of("subject", "roles", "permissions", "name", "no"), fields(me));
    assertEquals("SG000001", me.get("subject").asString());
    assertEquals("SG000001", me.get("no").asString());
    assertEquals("林思远", me.get("name").asString());
    assertEquals(Set.of("course-admin", "teacher"), texts(me.get("roles")));
    assertEquals(Set.of("MCO", "MCOR", "MTEC", "CV", "CR", "UL"), texts(me.get("permissions")));
  }

  @Test
  @DisplayName("FR-27 解除继承后同一令牌的下一次 /auth/me 立即失去继承权限")
  void FR27_inheritanceChangeIsVisibleOnNextCall() throws Exception {
    call("DELETE", url(ROLE_INHERIT_URL, adminRole, teacherRole), admin, null, 204);
    JsonNode me = result(call("GET", CPM_ME_URL, teacher, null, 200));
    assertEquals(Set.of("course-admin"), texts(me.get("roles")));
    assertEquals(Set.of("MCOR", "MTEC", "CR", "UL"), texts(me.get("permissions")));
    call("PUT", url(ROLE_INHERIT_URL, adminRole, teacherRole), admin, null, 204);
    me = result(call("GET", CPM_ME_URL, teacher, null, 200));
    assertEquals(Set.of("MCO", "MCOR", "MTEC", "CV", "CR", "UL"), texts(me.get("permissions")));
  }

  @Test
  @DisplayName("FR-27 停用被继承角色阻断路径；停用 API 资源只撤回对应后端权限码")
  void FR27_disabledRoleAndResourceAreExcluded() throws Exception {
    call("PUT", url(ROLE_STATUS_URL, teacherRole), admin, Map.of("enabled", false), 200);
    JsonNode me = result(call("GET", CPM_ME_URL, teacher, null, 200));
    assertEquals(Set.of("course-admin"), texts(me.get("roles")));
    assertEquals(Set.of("MCOR", "MTEC", "CR", "UL"), texts(me.get("permissions")));
    call("PUT", url(ROLE_STATUS_URL, teacherRole), admin, Map.of("enabled", true), 200);
    call("PUT", url(RESOURCE_STATUS_URL, resourceId("CR")), admin, Map.of("active", false), 200);
    me = result(call("GET", CPM_ME_URL, teacher, null, 200));
    assertEquals(Set.of("MCO", "MCOR", "MTEC", "CV", "UL"), texts(me.get("permissions")));
  }

  @Test
  @DisplayName("FR-27 应用停用时不返回角色和权限；未知应用的令牌无法签发，无令牌 401")
  void FR27_disabledApplicationAndMissingToken() throws Exception {
    call("PUT", url(SYSTEM_STATUS_URL, cpm), admin, Map.of("enabled", false), 200);
    JsonNode me = result(call("GET", CPM_ME_URL, teacher, null, 200));
    assertEquals(Set.of(), texts(me.get("permissions")));
    assertEquals(Set.of(), texts(me.get("roles")));
    JsonNode unauthorized = result(call("GET", CPM_ME_URL, null, null, 401));
    assertEquals("UNAUTHORIZED", unauthorized.get("code").asString());
    call(
        "POST",
        url(APP_LOGIN_URL, "unknown"),
        null,
        Map.of("username", "SG000001", "password", PASSWORD, "systems", List.of("unknown")),
        400);
  }

  // ---------- 前端权限与 API 权限校验 ----------

  @Test
  @DisplayName("FR-33 校验前端菜单/按钮权限码：只返回 allowed；继承得到的菜单为真，后端 API 权限码与无关权限为假")
  void FR33_permissionCheckCoversMenusAndButtonsOnly() throws Exception {
    JsonNode inherited = result(call("GET", url(CPM_PERMISSION_URL, "MCO"), teacher, null, 200));
    assertEquals(Set.of("allowed"), fields(inherited));
    assertTrue(inherited.get("allowed").asBoolean());
    assertTrue(allowed(url(CPM_PERMISSION_URL, "MCOR"), teacher));
    assertFalse(allowed(url(CPM_PERMISSION_URL, "CV"), teacher), "后端 API 权限码不是前端权限");
    assertFalse(allowed(url(CPM_PERMISSION_URL, "OA-HOME"), teacher), "其他应用的菜单");
    assertFalse(allowed(url(CPM_PERMISSION_URL, "NOPE"), teacher));
    assertFalse(allowed(url(CPM_PERMISSION_URL, "MCO"), outsider));
    call("DELETE", url(ROLE_INHERIT_URL, adminRole, teacherRole), admin, null, 204);
    assertFalse(allowed(url(CPM_PERMISSION_URL, "MCO"), teacher), "撤销后下一次校验立即为假");
    call("PUT", url(SYSTEM_STATUS_URL, cpm), admin, Map.of("enabled", false), 200);
    assertFalse(allowed(url(CPM_PERMISSION_URL, "MCOR"), teacher));
  }

  @Test
  @DisplayName("FR-34 按 operationId 判断能否调用接口：只返回 operationId 与 allowed；未登记 404 API_OPERATION_NOT_FOUND")
  void FR34_apiPermissionByOperationId() throws Exception {
    JsonNode check =
        result(call("GET", url(CPM_API_PERMISSION_URL, "listAdminCourses"), teacher, null, 200));
    assertEquals(Set.of("operationId", "allowed"), fields(check));
    assertEquals("listAdminCourses", check.get("operationId").asString());
    assertTrue(check.get("allowed").asBoolean());
    assertTrue(allowed(url(CPM_API_PERMISSION_URL, "reviewCourse"), teacher));
    assertFalse(allowed(url(CPM_API_PERMISSION_URL, "reviewCourse"), outsider));
    call("PUT", url(RESOURCE_STATUS_URL, resourceId("CR")), admin, Map.of("active", false), 200);
    assertFalse(allowed(url(CPM_API_PERMISSION_URL, "reviewCourse"), teacher), "停用的资源不可调用");
    JsonNode missing =
        result(call("GET", url(CPM_API_PERMISSION_URL, "noSuchOperation"), teacher, null, 404));
    assertEquals("API_OPERATION_NOT_FOUND", missing.get("code").asString());
    call("PUT", url(SYSTEM_STATUS_URL, cpm), admin, Map.of("enabled", false), 200);
    assertFalse(allowed(url(CPM_API_PERMISSION_URL, "listAdminCourses"), teacher));
  }

  @Test
  @DisplayName("FR-34 operationId 在同一应用内唯一，格式不合法时拒绝")
  void FR34_operationIdIsUniquePerApplication() throws Exception {
    Map<String, Object> duplicate = new HashMap<>();
    duplicate.put("systemId", cpm);
    duplicate.put("name", "重复");
    duplicate.put("method", "GET");
    duplicate.put("path", "/admin/courses/again");
    duplicate.put("operationId", "listAdminCourses");
    call("POST", RESOURCES_URL, admin, duplicate, 409);
    duplicate.put("operationId", "1bad id");
    call("POST", RESOURCES_URL, admin, duplicate, 400);
    duplicate.put("systemId", other);
    duplicate.put("operationId", "listAdminCourses");
    JsonNode created = result(call("POST", RESOURCES_URL, admin, duplicate, 201));
    assertEquals("listAdminCourses", created.get("operationId").asString());
  }

  // ---------- 统一身份目录 ----------

  @Test
  @DisplayName("FR-35 逐层查询部门：不传 parentId 为根部门，传入为直接子部门；字段与分页结构符合契约")
  void FR35_departmentsAreListedLevelByLevel() throws Exception {
    JsonNode roots = result(call("GET", CPM_DEPARTMENTS_URL, teacher, null, 200));
    assertEquals(Set.of("page", "pageSize", "total", "items"), fields(roots));
    assertEquals(1, roots.get("page").asInt());
    assertEquals(20, roots.get("pageSize").asInt());
    assertEquals(2, roots.get("total").asLong());
    JsonNode first = roots.get("items").get(0);
    assertEquals(Set.of("id", "code", "name", "parentId", "hasChildren"), fields(first));
    assertEquals("HQ", first.get("code").asString());
    assertTrue(first.get("parentId").isNull());
    assertTrue(first.get("hasChildren").asBoolean());

    JsonNode children =
        result(call("GET", url(CPM_SUBDEPARTMENTS_URL, hq), teacher, null, 200));
    assertEquals(List.of("HQ-ENG", "HQ-OFF"), codes(children));
    assertEquals(hq, children.get("items").get(0).get("parentId").asLong());
    assertFalse(children.get("items").get(0).get("hasChildren").asBoolean());

    JsonNode paged =
        result(
            call(
                "GET", url(CPM_SUBDEPARTMENTS_PAGE_URL, hq, 2, 1), teacher, null, 200));
    assertEquals(2, paged.get("total").asLong());
    assertEquals(List.of("HQ-OFF"), codes(paged));
    assertEquals(
        List.of("RD"),
        codes(result(call("GET", url(CPM_DEPARTMENTS_SEARCH_URL, "道路"), teacher, null, 200))));
    assertEquals(
        List.of("HQ-ENG"),
        codes(
            result(
                call("GET", url(CPM_SUBDEPARTMENTS_SEARCH_URL, hq, "eng"), teacher, null, 200))));
    assertEquals(
        List.of(),
        codes(result(call("GET", url(CPM_DEPARTMENTS_SEARCH_URL, "100%25"), teacher, null, 200))),
        "通配符按字面匹配");
  }

  @Test
  @DisplayName("FR-35 部门查询参数校验：页码、每页条数、关键字长度 400；上级部门不存在 404；没有授权 403")
  void FR35_departmentQueryValidationAndAccess() throws Exception {
    call("GET", url(CPM_DEPARTMENTS_PAGE_URL, 0), teacher, null, 400);
    call("GET", url(CPM_DEPARTMENTS_SIZE_URL, 0), teacher, null, 400);
    call("GET", url(CPM_DEPARTMENTS_SIZE_URL, 101), teacher, null, 400);
    call("GET", url(CPM_SUBDEPARTMENTS_URL, 0), teacher, null, 400);
    call("GET", url(CPM_DEPARTMENTS_SEARCH_URL, "很".repeat(101)), teacher, null, 400);
    JsonNode missing =
        result(call("GET", url(CPM_SUBDEPARTMENTS_URL, 999999), teacher, null, 404));
    assertEquals("IDENTITY_DEPARTMENT_NOT_FOUND", missing.get("code").asString());
    JsonNode forbidden = result(call("GET", CPM_DEPARTMENTS_URL, outsider, null, 403));
    assertEquals("FORBIDDEN", forbidden.get("code").asString());
  }

  @Test
  @DisplayName("FR-36 部门下的用户：按工号或姓名查询，字段不含联系方式；部门不存在 404；没有授权 403")
  void FR36_departmentUsers() throws Exception {
    long roadOffice =
        result(call("GET", url(CPM_SUBDEPARTMENTS_URL, rd), teacher, null, 200))
            .get("items")
            .get(0)
            .get("id")
            .asLong();
    JsonNode page =
        result(call("GET", url(CPM_DEPARTMENT_USERS_URL, roadOffice), teacher, null, 200));
    assertEquals(Set.of("page", "pageSize", "total", "items"), fields(page));
    assertEquals(1, page.get("total").asLong());
    JsonNode user = page.get("items").get(0);
    assertEquals(Set.of("userId", "no", "name", "departmentId", "departmentName"), fields(user));
    assertEquals("SG000191", user.get("userId").asString());
    assertEquals("SG000191", user.get("no").asString());
    assertEquals("周雨桐", user.get("name").asString());
    assertEquals(roadOffice, user.get("departmentId").asLong());
    assertEquals("综合办公室", user.get("departmentName").asString());
    assertEquals(
        1,
        result(
                call(
                    "GET",
                    url(CPM_DEPARTMENT_USERS_SEARCH_URL, roadOffice, "雨桐"),
                    teacher,
                    null,
                    200))
            .get("total")
            .asLong());
    assertEquals(
        0,
        result(
                call(
                    "GET",
                    url(CPM_DEPARTMENT_USERS_SEARCH_URL, roadOffice, "SG0000"),
                    teacher,
                    null,
                    200))
            .get("total")
            .asLong());
    JsonNode missing =
        result(call("GET", url(CPM_DEPARTMENT_USERS_URL, 999999), teacher, null, 404));
    assertEquals("IDENTITY_DEPARTMENT_NOT_FOUND", missing.get("code").asString());
    call("GET", url(CPM_DEPARTMENT_USERS_URL, roadOffice), outsider, null, 403);
  }

  @Test
  @DisplayName("FR-28 按统一身份用户标识查询姓名与工号；不存在 404 IDENTITY_USER_NOT_FOUND；旧路径已删除")
  void FR28_lookupUserByIdentityId() throws Exception {
    JsonNode user = result(call("GET", url(CPM_USER_URL, "SG000191"), teacher, null, 200));
    assertEquals(Set.of("userId", "no", "name"), fields(user));
    assertEquals("SG000191", user.get("userId").asString());
    assertEquals("SG000191", user.get("no").asString());
    assertEquals("周雨桐", user.get("name").asString());
    JsonNode missing = result(call("GET", url(CPM_USER_URL, "nobody"), teacher, null, 404));
    assertEquals("IDENTITY_USER_NOT_FOUND", missing.get("code").asString());
    call("GET", url(CPM_USER_URL, "SG000001"), outsider, null, 403);
    call("GET", CPM_LEGACY_IDENTITY_USER_URL, teacher, null, 404);
  }

  @Test
  @DisplayName("FR-35 部门名称同一上级下唯一、编码全局唯一；未填编码时自动编码")
  void FR35_departmentNamesAreUniquePerParentAndCodesAreUnique() throws Exception {
    call("POST", DEPARTMENTS_URL, admin, Map.of("name", "综合办公室", "parentId", hq), 409);
    call("POST", DEPARTMENTS_URL, admin, Map.of("name", "新部门", "code", "HQ"), 409);
    call("POST", DEPARTMENTS_URL, admin, Map.of("name", "新部门", "code", "-bad"), 400);
    JsonNode created =
        result(call("POST", DEPARTMENTS_URL, admin, Map.of("name", "新部门", "parentId", rd), 201));
    assertEquals(String.format("D%04d", created.get("id").asLong()), created.get("code").asString());
    long id = created.get("id").asLong();
    JsonNode renamed =
        result(
            call(
                "PUT", url(DEPARTMENT_URL, id), admin, Map.of("name", "养护部", "parentId", rd), 200));
    assertEquals(created.get("code").asString(), renamed.get("code").asString(), "修改时保留原编码");
    call(
        "PUT",
        url(DEPARTMENT_URL, id),
        admin,
        Map.of("name", "综合办公室", "parentId", rd),
        409);
  }

  @Test
  @DisplayName("FR-03 用户列表按工号或姓名在数据库层检索并分页；新建用户的所属部门随结果返回")
  void FR03_userSearchIsPagedInDatabaseAndKeepsDepartment() throws Exception {
    JsonNode found = result(call("GET", url(USERS_SEARCH_URL, 1, 10, "雨桐"), admin, null, 200));
    assertEquals(1, found.get("total").asLong());
    JsonNode user = found.get("items").get(0);
    assertEquals("SG000191", user.get("username").asString());
    long roadOffice =
        db.queryForObject("select id from sys_department where code = 'RD-OFF'", Long.class);
    assertEquals(roadOffice, user.get("departmentId").asLong());
    assertEquals(
        2,
        result(call("GET", url(USERS_SEARCH_URL, 1, 10, "sg000"), admin, null, 200))
            .get("total")
            .asLong());
    JsonNode second =
        result(call("GET", url(USERS_SEARCH_URL, 2, 1, "sg000"), admin, null, 200));
    assertEquals(1, second.get("items").size());
    assertEquals(
        3, result(call("GET", url(USERS_PAGE_URL, 1, 10), admin, null, 200)).get("total").asLong());
    call("GET", url(USERS_BY_KEYWORD_URL, "很".repeat(101)), admin, null, 400);
    JsonNode created =
        result(
            call(
                "POST",
                USERS_URL,
                admin,
                Map.of(
                    "username", "SG000192", "displayName", "吴承泽", "password", PASSWORD,
                    "departmentId", roadOffice),
                201));
    assertEquals(roadOffice, created.get("departmentId").asLong());
  }

  // ---------- 跨域 ----------

  @Test
  @DisplayName("FR-37 浏览器直连接口按配置放行课程前端来源：预检允许 Authorization 与 GET，未配置的来源被拒绝")
  void FR37_corsAllowsConfiguredOrigins() throws Exception {
    mvc.perform(
            options(url(CPM_PERMISSION_URL, "MCO"))
                .header("Origin", "http://course.example")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://course.example"))
        .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("authorization")))
        .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("GET")));
    mvc.perform(
            get(url(CPM_PERMISSION_URL, "MCO"))
                .header("Origin", "http://course.example")
                .header("Authorization", "Bearer " + teacher))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://course.example"));
    mvc.perform(
            options(CPM_DEPARTMENTS_URL)
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
    mvc.perform(
            options(USERS_URL)
                .header("Origin", "http://course.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  // ---------- 审计 ----------

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
    JsonNode first = result(call("POST", CPM_AUDIT_LOGS_URL, teacher, body, 201));
    JsonNode again = result(call("POST", CPM_AUDIT_LOGS_URL, teacher, body, 201));
    assertEquals(first.get("id").asLong(), again.get("id").asLong());
    assertEquals("林思远", first.get("actor").get("name").asString());
    assertEquals(Set.of("course-admin"), texts(first.get("actor").get("roles")));

    JsonNode page =
        result(call("GET", url(CPM_AUDIT_LOGS_PAGE_URL, 1, 100), teacher, null, 200));
    assertEquals(Set.of("page", "pageSize", "total", "items"), fields(page));
    assertEquals(1, page.get("total").asLong());
    assertEquals(30, page.get("items").get(0).get("after").get("imported").asInt());
    String both = appLogin("SG000001", List.of(CPM, "oa"));
    assertEquals(
        0, result(call("GET", url(APP_AUDIT_LOGS_URL, "oa"), both, null, 200)).get("total").asLong());
    assertEquals(
        1,
        result(call("GET", url(CPM_AUDIT_LOGS_BY_TARGET_URL, "名单"), teacher, null, 200))
            .get("total")
            .asLong());
  }

  @Test
  @DisplayName("FR-29 应用内没有任何有效权限的用户不能查询审计；请求体校验 400")
  void FR29_auditQueryRequiresPermissionInApplication() throws Exception {
    call("GET", CPM_AUDIT_LOGS_URL, outsider, null, 403);
    call("POST", CPM_AUDIT_LOGS_URL, teacher, Map.of("target", "缺少类型"), 400);
  }

  @Test
  @DisplayName("FR-30 角色继承建立与解除写入审计记录，操作人为当前管理员")
  void FR30_hierarchyChangesAreAudited() throws Exception {
    call("DELETE", url(ROLE_INHERIT_URL, adminRole, teacherRole), admin, null, 204);
    JsonNode page = result(call("GET", url(AUDIT_LOGS_OF_SYSTEM_URL, "rbac"), admin, null, 200));
    assertEquals(2, page.get("total").asLong());
    JsonNode latest = page.get("items").get(0);
    assertEquals("ROLE_INHERITANCE_REMOVED", latest.get("type").asString());
    assertEquals("rbac-role-" + adminRole, latest.get("objectId").asString());
    assertEquals("系统管理员", latest.get("actor").get("name").asString());
    assertEquals("ROLE_INHERITANCE_ADDED", page.get("items").get(1).get("type").asString());
    call("GET", AUDIT_LOGS_URL, login("SG000001", PASSWORD), null, 403);
  }

  @Test
  @DisplayName("NFR-11 前端菜单切换携带 X-Client-Menu，后端 logback 日志按菜单记录请求与各层调用")
  void NFR11_menuSwitchProducesLayeredLogs(CapturedOutput output) throws Exception {
    mvc.perform(
            get(SYSTEMS_URL)
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

  @Test
  @DisplayName("NFR-11 登录请求体中的密码不进入任何日志")
  void NFR11_loginPasswordIsNeverLogged(CapturedOutput output) throws Exception {
    appLogin("SG000001", List.of(CPM));
    assertFalse(output.getOut().contains(PASSWORD));
  }

  /** 结构化模式的服务层日志标签。 */
  String layer() {
    return "Service";
  }

  long permission(String code, String name, String type, Long parent) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("systemId", cpm);
    body.put("code", code);
    body.put("name", name);
    body.put("type", type);
    if (parent != null) body.put("parentId", parent);
    return create(PERMISSIONS_URL, body);
  }

  long resource(String name, String method, String path, String code, String operationId)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("systemId", cpm);
    body.put("name", name);
    body.put("method", method);
    body.put("path", path);
    body.put("operationId", operationId);
    if (code != null) body.put("code", code);
    return create(RESOURCES_URL, body);
  }

  void bind(long permission, long resource) throws Exception {
    call("PUT", url(PERMISSION_RESOURCE_URL, permission, resource), admin, null, 204);
  }

  long resourceId(String code) throws Exception {
    for (JsonNode resource : result(call("GET", RESOURCES_URL, admin, null, 200)))
      if (code.equals(resource.get("code").asString(null))) return resource.get("id").asLong();
    throw new AssertionError("没有权限码为 " + code + " 的资源");
  }

  boolean allowed(String path, String token) throws Exception {
    return result(call("GET", path, token, null, 200)).get("allowed").asBoolean();
  }

  Set<String> fields(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.propertyNames().forEach(names::add);
    return names;
  }

  List<String> codes(JsonNode page) {
    List<String> values = new ArrayList<>();
    page.get("items").forEach(item -> values.add(item.get("code").asString()));
    return values;
  }

  Set<String> texts(JsonNode array) {
    Set<String> values = new TreeSet<>();
    array.forEach(v -> values.add(v.asString()));
    return values;
  }

  JsonNode result(MvcResult result) {
    byte[] body = result.getResponse().getContentAsByteArray();
    return body.length == 0
        ? json.createObjectNode()
        : json.readTree(new String(body, StandardCharsets.UTF_8));
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

  /** 控制台登录（令牌目标系统为 rbac）。 */
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

  /** 接入应用登录（令牌目标系统为 systems）。 */
  String appLogin(String name, List<String> systems) throws Exception {
    return result(
            call(
                "POST",
                CPM_LOGIN_URL,
                null,
                Map.of("username", name, "password", PASSWORD, "systems", systems),
                200))
        .get("accessToken")
        .asString();
  }
}
