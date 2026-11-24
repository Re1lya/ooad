package com.cdwy.permission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.cdwy.permission.controller.common.ApiExceptionHandler;
import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.service.auth.AdminBootstrapService;
import com.cdwy.permission.support.RequestTransactionBoundary;
import com.cdwy.permission.support.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 业务错误码契约：每个业务错误码至少有一个测试，所有按 id 操作的接口都覆盖不存在的 id。
 *
 * <p>错误响应统一为 {@code {code, message}}。结构化与 DDD 两条设计路线共用本类的断言，DDD 路线见
 * {@link DddErrorCodeContractTests}。员工信息同步的错误码见 {@link DirectorySyncTests}，接入应用身份接口的错误码见
 * {@link ApplicationIdentityTests}。
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
class ErrorCodeContractTests {
  private static final String PASSWORD = "Contract-2026!";
  private static final long MISSING = 999_999_999L;

  private static final String LOGIN_URL = "/api/auth/login";
  private static final String ME_PASSWORD_URL = "/api/me/password";
  private static final String MENU_URL = "/api/me/menu";
  private static final String USERS_URL = "/api/users";
  private static final String USER_URL = "/api/users/{id}";
  private static final String USER_STATUS_URL = "/api/users/{id}/status";
  private static final String USER_PASSWORD_URL = "/api/users/{id}/password";
  private static final String USER_ROLES_URL = "/api/users/{userId}/roles";
  private static final String USER_ROLE_URL = "/api/users/{userId}/roles/{roleId}";
  private static final String USER_ORIGINS_URL = "/api/users/{userId}/permission-origins";
  private static final String DEPARTMENTS_URL = "/api/departments";
  private static final String DEPARTMENT_URL = "/api/departments/{id}";
  private static final String SYSTEMS_URL = "/api/systems";
  private static final String SYSTEM_URL = "/api/systems/{id}";
  private static final String SYSTEM_STATUS_URL = "/api/systems/{id}/status";
  private static final String ROLES_URL = "/api/roles";
  private static final String ROLE_URL = "/api/roles/{id}";
  private static final String ROLE_STATUS_URL = "/api/roles/{id}/status";
  private static final String ROLE_PERMISSIONS_URL = "/api/roles/{roleId}/permissions";
  private static final String ROLE_PERMISSION_URL =
      "/api/roles/{roleId}/permissions/{permissionId}";
  private static final String ROLE_EFFECTIVE_URL = "/api/roles/{roleId}/effective-permissions";
  private static final String ROLE_INHERIT_URL = "/api/roles/{seniorId}/inherits/{inheritedId}";
  private static final String PERMISSIONS_URL = "/api/permissions";
  private static final String PERMISSION_URL = "/api/permissions/{id}";
  private static final String PERMISSION_STATUS_URL = "/api/permissions/{id}/status";
  private static final String PERMISSION_RESOURCES_URL = "/api/permissions/{id}/resources";
  private static final String PERMISSION_RESOURCE_URL =
      "/api/permissions/{id}/resources/{resourceId}";
  private static final String RESOURCES_URL = "/api/resources";
  private static final String RESOURCE_URL = "/api/resources/{id}";
  private static final String RESOURCE_STATUS_URL = "/api/resources/{id}/status";
  private static final String APP_LOGIN_URL = "/api/apps/{systemCode}/auth/login";
  private static final String APP_AUDIT_URL = "/api/apps/{systemCode}/auth/audit-logs";

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;
  @Autowired RoleRepository roles;

  String admin;
  String workerToken;
  long worker;
  long system;
  long role;
  long menu;
  long resource;

  /** 每个测试从空库开始：管理员、一名员工、一个应用及其菜单和 API 资源、一个角色。 */
  @BeforeEach
  void fixture() throws Exception {
    TestDatabase.clear(db);
    bootstrap.initialize("admin", PASSWORD);
    admin = login("admin");
    worker = create(USERS_URL, user("worker"));
    workerToken = login("worker");
    system = create(SYSTEMS_URL, Map.of("code", "course", "name", "课程管理系统"));
    role = create(ROLES_URL, Map.of("code", "teacher", "name", "教师"));
    menu = create(PERMISSIONS_URL, menuBody("course", "/courses", null));
    resource =
        create(
            RESOURCES_URL,
            Map.of(
                "systemId", system, "name", "课程列表", "method", "GET", "path", "/api/demo/courses"));
  }

  /** NFR-01：未登录、凭证错误与越权分别返回 UNAUTHORIZED、FORBIDDEN。 */
  @Test
  @Tag("异常")
  @DisplayName("NFR-01 未登录或凭证错误返回 UNAUTHORIZED，普通用户调用管理接口返回 FORBIDDEN")
  void NFR01_authenticationAndAuthorizationCodes() throws Exception {
    expectError(HttpMethod.GET, USERS_URL, null, null, 401, "UNAUTHORIZED");
    expectError(
        HttpMethod.POST,
        LOGIN_URL,
        null,
        Map.of("username", "worker", "password", "Wrong-2026!"),
        401,
        "UNAUTHORIZED");
    expectError(HttpMethod.GET, USERS_URL, workerToken, null, 403, "FORBIDDEN");
  }

  /** NFR-07：请求格式问题在 API 层拦截，统一返回 BAD_REQUEST，不进入业务层。 */
  @Test
  @Tag("异常")
  @DisplayName("NFR-07 字段校验失败、JSON 无法解析、未知字段、参数越界或类型不符都返回 BAD_REQUEST")
  void NFR07_malformedRequestsReturnBadRequest() throws Exception {
    expectError(HttpMethod.POST, ROLES_URL, admin, Map.of("code", "Bad Code", "name", "x"), 400, "BAD_REQUEST");
    expectError(
        HttpMethod.PUT, ROLE_URL, admin, Map.of("name", "教师", "enabled", false), 400, "BAD_REQUEST", role);
    assertBadRequest(authorized(request(HttpMethod.POST, ROLES_URL)).content("{"));
    assertBadRequest(authorized(request(HttpMethod.GET, USERS_URL)).param("page", "0"));
    assertBadRequest(authorized(request(HttpMethod.GET, USERS_URL)).param("pageSize", "101"));
    assertBadRequest(authorized(request(HttpMethod.GET, USERS_URL)).param("pageSize", "abc"));
  }

  /** FR-02：改密码时原密码错误视为凭证错误，新密码须满足密码策略。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-02 本人改密：原密码错误返回 UNAUTHORIZED，新密码太弱返回 PASSWORD_POLICY_VIOLATION")
  void FR02_passwordChangeCodes() throws Exception {
    expectError(
        HttpMethod.PUT,
        ME_PASSWORD_URL,
        workerToken,
        Map.of("currentPassword", "Wrong-2026!", "newPassword", "Changed-2026!"),
        401,
        "UNAUTHORIZED");
    expectError(
        HttpMethod.PUT,
        ME_PASSWORD_URL,
        workerToken,
        Map.of("currentPassword", PASSWORD, "newPassword", "abcdefgh"),
        400,
        "PASSWORD_POLICY_VIOLATION");
  }

  /** FR-03：新建用户时登录名唯一、密码满足策略、所选部门存在。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-03 新建用户：登录名重复 USERNAME_EXISTS，弱密码 PASSWORD_POLICY_VIOLATION，部门不存在 DEPARTMENT_NOT_FOUND")
  void FR03_userCreationCodes() throws Exception {
    expectError(HttpMethod.POST, USERS_URL, admin, user("worker"), 409, "USERNAME_EXISTS");
    Map<String, Object> weak = user("weak");
    weak.put("password", "abcdefgh");
    expectError(HttpMethod.POST, USERS_URL, admin, weak, 400, "PASSWORD_POLICY_VIOLATION");
    Map<String, Object> lost = user("lost");
    lost.put("departmentId", MISSING);
    expectError(HttpMethod.POST, USERS_URL, admin, lost, 404, "DEPARTMENT_NOT_FOUND");
  }

  /** FR-04：正式员工不设期限；外部、临时人员必须设期限，且不超过配置的上限。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-04 人员期限不符合人员类型或超过上限时返回 ACCESS_WINDOW_INVALID")
  void FR04_accessWindowCodes() throws Exception {
    Map<String, Object> employee = user("employee");
    employee.put("expiresOn", LocalDate.now().plusDays(1).toString());
    expectError(HttpMethod.POST, USERS_URL, admin, employee, 400, "ACCESS_WINDOW_INVALID");
    Map<String, Object> external = user("external");
    external.put("personType", "EXTERNAL");
    expectError(HttpMethod.POST, USERS_URL, admin, external, 400, "ACCESS_WINDOW_INVALID");
    Map<String, Object> temporary = user("temporary");
    temporary.put("personType", "TEMPORARY");
    temporary.put("expiresOn", LocalDate.now().plusDays(30).toString());
    expectError(HttpMethod.POST, USERS_URL, admin, temporary, 400, "ACCESS_WINDOW_INVALID");
  }

  /** FR-05：部门同级不重名、编码唯一、树不成环，有子部门或员工时不能删除。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-05 部门：同级重名、编码重复、形成环路、仍被使用时分别返回对应错误码")
  void FR05_departmentCodes() throws Exception {
    long root = create(DEPARTMENTS_URL, Map.of("name", "总公司", "code", "HQ"));
    long child = create(DEPARTMENTS_URL, Map.of("name", "财务部", "parentId", root));
    expectError(HttpMethod.POST, DEPARTMENTS_URL, admin, Map.of("name", "总公司"), 409, "DEPARTMENT_NAME_EXISTS");
    expectError(
        HttpMethod.POST, DEPARTMENTS_URL, admin, Map.of("name", "分公司", "code", "HQ"), 409, "DEPARTMENT_CODE_EXISTS");
    expectError(
        HttpMethod.PUT,
        DEPARTMENT_URL,
        admin,
        Map.of("name", "总公司", "code", "HQ", "parentId", child),
        400,
        "DEPARTMENT_CYCLE",
        root);
    expectError(HttpMethod.DELETE, DEPARTMENT_URL, admin, null, 409, "DEPARTMENT_IN_USE", root);
  }

  /** FR-06：应用编码唯一；有权限或资源时不能删除；引用不存在的应用返回 SYSTEM_NOT_FOUND。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-06 应用：编码重复 SYSTEM_CODE_EXISTS，仍被引用 SYSTEM_IN_USE，引用不存在的应用 SYSTEM_NOT_FOUND")
  void FR06_systemCodes() throws Exception {
    expectError(
        HttpMethod.POST, SYSTEMS_URL, admin, Map.of("code", "course", "name", "重复"), 409, "SYSTEM_CODE_EXISTS");
    expectError(HttpMethod.DELETE, SYSTEM_URL, admin, null, 409, "SYSTEM_IN_USE", system);
    Map<String, Object> orphanMenu = menuBody("orphan", "/orphan", null);
    orphanMenu.put("systemId", MISSING);
    expectError(HttpMethod.POST, PERMISSIONS_URL, admin, orphanMenu, 404, "SYSTEM_NOT_FOUND");
    expectError(
        HttpMethod.POST,
        RESOURCES_URL,
        admin,
        Map.of("systemId", MISSING, "name", "孤立接口", "method", "GET", "path", "/api/orphan"),
        404,
        "SYSTEM_NOT_FOUND");
    expectErrorResponse(
        mvc.perform(authorized(request(HttpMethod.GET, MENU_URL)).param("system", "nowhere"))
            .andReturn()
            .getResponse(),
        404,
        "SYSTEM_NOT_FOUND");
  }

  /** FR-07：角色编码唯一；仍有授权或继承关系时不能删除。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-07 角色：编码重复 ROLE_CODE_EXISTS，仍有授权 ROLE_IN_USE，仍有继承关系 ROLE_HAS_INHERITANCE")
  void FR07_roleCodes() throws Exception {
    expectError(HttpMethod.POST, ROLES_URL, admin, Map.of("code", "teacher", "name", "重复"), 409, "ROLE_CODE_EXISTS");
    call(HttpMethod.PUT, USER_ROLE_URL, admin, null, 204, worker, role);
    expectError(HttpMethod.DELETE, ROLE_URL, admin, null, 409, "ROLE_IN_USE", role);
    long senior = create(ROLES_URL, Map.of("code", "senior", "name", "高级"));
    long junior = create(ROLES_URL, Map.of("code", "junior", "name", "初级"));
    call(HttpMethod.PUT, ROLE_INHERIT_URL, admin, null, 204, senior, junior);
    expectError(HttpMethod.DELETE, ROLE_URL, admin, null, 409, "ROLE_HAS_INHERITANCE", junior);
  }

  /** FR-08：前端权限树的结构规则。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-08 前端权限：编码重复、按钮无上级、按钮带地址、上级不合法、形成环路、有子节点改按钮、仍被使用")
  void FR08_permissionTreeCodes() throws Exception {
    expectError(
        HttpMethod.POST, PERMISSIONS_URL, admin, menuBody("course", "/duplicate", null), 409, "PERMISSION_CODE_EXISTS");
    expectError(
        HttpMethod.POST, PERMISSIONS_URL, admin, buttonBody("orphan", null), 400, "BUTTON_PARENT_REQUIRED");
    Map<String, Object> pathy = buttonBody("pathy", menu);
    pathy.put("menuPath", "/pathy");
    expectError(HttpMethod.POST, PERMISSIONS_URL, admin, pathy, 400, "BUTTON_PATH_NOT_ALLOWED");
    long button = create(PERMISSIONS_URL, buttonBody("course:list", menu));
    expectError(
        HttpMethod.POST,
        PERMISSIONS_URL,
        admin,
        buttonBody("under-button", button),
        400,
        "PERMISSION_PARENT_INVALID");
    long child = create(PERMISSIONS_URL, menuBody("course:sub", "/courses/sub", menu));
    expectError(
        HttpMethod.PUT,
        PERMISSION_URL,
        admin,
        Map.of("name", "课程", "type", "MENU", "menuPath", "/courses", "parentId", child),
        400,
        "PERMISSION_CYCLE",
        menu);
    long other = create(PERMISSIONS_URL, menuBody("other", "/other", null));
    expectError(
        HttpMethod.PUT,
        PERMISSION_URL,
        admin,
        Map.of("name", "课程", "type", "BUTTON", "parentId", other),
        409,
        "PERMISSION_HAS_CHILDREN",
        menu);
    expectError(HttpMethod.DELETE, PERMISSION_URL, admin, null, 409, "PERMISSION_IN_USE", menu);
  }

  /** FR-09：同一应用内方法加路径唯一、operationId 唯一；仍绑定前端权限时不能删除。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-09 API 资源：路由重复 API_RESOURCE_EXISTS，operationId 重复 API_OPERATION_EXISTS，仍被绑定 API_RESOURCE_IN_USE")
  void FR09_resourceCodes() throws Exception {
    expectError(
        HttpMethod.POST,
        RESOURCES_URL,
        admin,
        Map.of("systemId", system, "name", "重复", "method", "GET", "path", "/api/demo/courses"),
        409,
        "API_RESOURCE_EXISTS");
    create(
        RESOURCES_URL,
        Map.of(
            "systemId",
            system,
            "name",
            "新增课程",
            "method",
            "POST",
            "path",
            "/api/demo/courses",
            "operationId",
            "createCourse"));
    expectError(
        HttpMethod.POST,
        RESOURCES_URL,
        admin,
        Map.of(
            "systemId",
            system,
            "name",
            "修改课程",
            "method",
            "PUT",
            "path",
            "/api/demo/courses/{id}",
            "operationId",
            "createCourse"),
        409,
        "API_OPERATION_EXISTS");
    call(HttpMethod.PUT, PERMISSION_RESOURCE_URL, admin, null, 204, menu, resource);
    expectError(HttpMethod.DELETE, RESOURCE_URL, admin, null, 409, "API_RESOURCE_IN_USE", resource);
  }

  /** FR-10、FR-11：停用的角色或前端权限不能再新增授权。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-10 FR-11 给用户授予停用角色、给角色授予停用权限都返回 GRANT_TARGET_INACTIVE")
  void FR10_inactiveGrantTargets() throws Exception {
    call(HttpMethod.PUT, ROLE_STATUS_URL, admin, Map.of("enabled", false), 200, role);
    expectError(HttpMethod.PUT, USER_ROLE_URL, admin, null, 400, "GRANT_TARGET_INACTIVE", worker, role);
    call(HttpMethod.PUT, PERMISSION_STATUS_URL, admin, Map.of("active", false), 200, menu);
    long active = create(ROLES_URL, Map.of("code", "active-role", "name", "在用角色"));
    expectError(
        HttpMethod.PUT, ROLE_PERMISSION_URL, admin, null, 400, "GRANT_TARGET_INACTIVE", active, menu);
  }

  /** FR-12：前端权限只能绑定同一应用的 API 资源。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-12 前端权限绑定其他应用的 API 资源返回 BINDING_SYSTEM_MISMATCH")
  void FR12_bindingAcrossSystems() throws Exception {
    long office = create(SYSTEMS_URL, Map.of("code", "oa", "name", "办公系统"));
    long foreign =
        create(
            RESOURCES_URL,
            Map.of("systemId", office, "name", "公告", "method", "GET", "path", "/api/oa/notices"));
    expectError(
        HttpMethod.PUT, PERMISSION_RESOURCE_URL, admin, null, 400, "BINDING_SYSTEM_MISMATCH", menu, foreign);
  }

  /** FR-18：继承图拒绝自继承与环路，关系两端的角色必须启用。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-18 角色继承：自继承 ROLE_SELF_INHERITANCE，成环 ROLE_INHERITANCE_CYCLE，端点停用 ROLE_DISABLED")
  void FR18_hierarchyCodes() throws Exception {
    long senior = create(ROLES_URL, Map.of("code", "senior", "name", "高级"));
    long junior = create(ROLES_URL, Map.of("code", "junior", "name", "初级"));
    expectError(HttpMethod.PUT, ROLE_INHERIT_URL, admin, null, 400, "ROLE_SELF_INHERITANCE", senior, senior);
    call(HttpMethod.PUT, ROLE_INHERIT_URL, admin, null, 204, senior, junior);
    expectError(HttpMethod.PUT, ROLE_INHERIT_URL, admin, null, 400, "ROLE_INHERITANCE_CYCLE", junior, senior);
    call(HttpMethod.PUT, ROLE_STATUS_URL, admin, Map.of("enabled", false), 200, role);
    expectError(HttpMethod.PUT, ROLE_INHERIT_URL, admin, null, 400, "ROLE_DISABLED", senior, role);
  }

  /** FR-38：权限系统自身的内置应用、管理员角色、内置菜单按钮与管理接口不能停用或删除，否则所有人（包括管理员）都会进不了控制台。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-38 停用或删除内置应用、内置管理员角色、内置菜单按钮与管理接口都返回 CONSOLE_BUILT_IN，重新启用仍然允许")
  void FR38_builtInConsoleObjectsCannotBeDisabledOrDeleted() throws Exception {
    long console = id("select id from biz_system where code = 'rbac'");
    long adminRole = id("select id from sys_role where code = 'rbac-admin'");
    long usersMenu = id("select id from sys_permission where code = 'rbac.users'");
    long usersApi =
        id("select id from api_resource where system_id = " + console + " and method = 'GET' and path = '/api/users'");
    expectError(HttpMethod.PUT, SYSTEM_STATUS_URL, admin, Map.of("enabled", false), 409, "CONSOLE_BUILT_IN", console);
    expectError(HttpMethod.DELETE, SYSTEM_URL, admin, null, 409, "CONSOLE_BUILT_IN", console);
    expectError(HttpMethod.PUT, ROLE_STATUS_URL, admin, Map.of("enabled", false), 409, "CONSOLE_BUILT_IN", adminRole);
    expectError(HttpMethod.DELETE, ROLE_URL, admin, null, 409, "CONSOLE_BUILT_IN", adminRole);
    expectError(
        HttpMethod.PUT, PERMISSION_STATUS_URL, admin, Map.of("active", false), 409, "CONSOLE_BUILT_IN", usersMenu);
    expectError(HttpMethod.DELETE, PERMISSION_URL, admin, null, 409, "CONSOLE_BUILT_IN", usersMenu);
    expectError(
        HttpMethod.PUT, RESOURCE_STATUS_URL, admin, Map.of("active", false), 409, "CONSOLE_BUILT_IN", usersApi);
    expectError(HttpMethod.DELETE, RESOURCE_URL, admin, null, 409, "CONSOLE_BUILT_IN", usersApi);
    call(HttpMethod.PUT, ROLE_STATUS_URL, admin, Map.of("enabled", true), 200, adminRole);
    call(HttpMethod.GET, USERS_URL, admin, null, 200);
    // 其他应用的同名接口不是内置接口，照常可以停用
    long sameRoute =
        create(RESOURCES_URL, Map.of("systemId", system, "name", "课程侧的用户列表", "method", "GET", "path", "/api/users"));
    call(HttpMethod.PUT, RESOURCE_STATUS_URL, admin, Map.of("active", false), 200, sameRoute);
  }

  /** FR-31：接入应用登录时，令牌目标 systems 必须包含当前应用，且每个目标都已登记。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-31 接入应用登录：systems 不含当前应用 LOGIN_TARGET_NOT_IN_SYSTEMS，含未登记应用 LOGIN_SYSTEM_UNKNOWN")
  void FR31_applicationLoginTargets() throws Exception {
    expectError(
        HttpMethod.POST,
        APP_LOGIN_URL,
        null,
        Map.of("username", "worker", "password", PASSWORD, "systems", List.of("oa")),
        400,
        "LOGIN_TARGET_NOT_IN_SYSTEMS",
        "course");
    expectError(
        HttpMethod.POST,
        APP_LOGIN_URL,
        null,
        Map.of("username", "worker", "password", PASSWORD, "systems", List.of("course", "nowhere")),
        400,
        "LOGIN_SYSTEM_UNKNOWN",
        "course");
  }

  /** FR-29：审计记录的 before、after 序列化后不能超过列宽。 */
  @Test
  @Tag("异常")
  @DisplayName("FR-29 写入审计记录时 before 内容过长返回 AUDIT_SNAPSHOT_TOO_LARGE")
  void FR29_auditSnapshotTooLarge() throws Exception {
    String token =
        call(
                HttpMethod.POST,
                APP_LOGIN_URL,
                null,
                Map.of("username", "worker", "password", PASSWORD, "systems", List.of("course")),
                200,
                "course")
            .get("accessToken")
            .asString();
    expectError(
        HttpMethod.POST,
        APP_AUDIT_URL,
        token,
        Map.of("type", "UPDATE", "before", Map.of("text", "x".repeat(5000))),
        400,
        "AUDIT_SNAPSHOT_TOO_LARGE",
        "course");
  }

  /** NFR-03：并发写入漏过业务检查时，由数据库唯一约束兜底，并翻译为 409 DATA_CONFLICT。 */
  @Test
  @Tag("异常")
  @DisplayName("NFR-03 业务检查之外的唯一约束冲突由数据库拦截，统一翻译为 DATA_CONFLICT")
  void NFR03_uniqueViolationFallsBackToDataConflict() {
    Role duplicate = new Role();
    duplicate.setCode("teacher");
    duplicate.setName("并发写入的同编码角色");
    DataIntegrityViolationException error =
        assertThrows(DataIntegrityViolationException.class, () -> roles.saveAndFlush(duplicate));
    ResponseEntity<Map<String, String>> response = new ApiExceptionHandler().constraint(error);
    assertEquals(409, response.getStatusCode().value());
    assertEquals("DATA_CONFLICT", response.getBody().get("code"));
  }

  /** NFR-07：所有按 id 操作的接口，id 不存在时返回 404 和对应对象的业务码；两个 id 的接口分别覆盖。 */
  @Test
  @Tag("异常")
  @DisplayName("NFR-07 所有按 id 操作的接口对不存在的 id 返回 404 与对应的 *_NOT_FOUND 业务码")
  void NFR07_everyByIdEndpointRejectsMissingIds() {
    Map<String, Object> userUpdate = Map.of("displayName", "不存在", "personType", "EMPLOYEE");
    Map<String, Object> rename = Map.of("name", "不存在");
    Map<String, Object> enabled = Map.of("enabled", true);
    Map<String, Object> active = Map.of("active", true);
    List<Executable> checks = new ArrayList<>();
    missing(checks, HttpMethod.GET, USER_URL, null, "USER_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, USER_URL, userUpdate, "USER_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.DELETE, USER_URL, null, "USER_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, USER_STATUS_URL, enabled, "USER_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, USER_PASSWORD_URL, Map.of("password", PASSWORD), "USER_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.GET, USER_ROLES_URL, null, "USER_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.GET, USER_ORIGINS_URL, null, "USER_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, USER_ROLE_URL, null, "USER_NOT_FOUND", MISSING, role);
    missing(checks, HttpMethod.PUT, USER_ROLE_URL, null, "ROLE_NOT_FOUND", worker, MISSING);
    missing(checks, HttpMethod.DELETE, USER_ROLE_URL, null, "USER_NOT_FOUND", MISSING, role);
    missing(checks, HttpMethod.DELETE, USER_ROLE_URL, null, "ROLE_NOT_FOUND", worker, MISSING);
    missing(checks, HttpMethod.PUT, DEPARTMENT_URL, rename, "DEPARTMENT_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.DELETE, DEPARTMENT_URL, null, "DEPARTMENT_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, SYSTEM_URL, rename, "SYSTEM_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.DELETE, SYSTEM_URL, null, "SYSTEM_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, SYSTEM_STATUS_URL, enabled, "SYSTEM_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, ROLE_URL, rename, "ROLE_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.DELETE, ROLE_URL, null, "ROLE_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, ROLE_STATUS_URL, enabled, "ROLE_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.GET, ROLE_PERMISSIONS_URL, null, "ROLE_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.GET, ROLE_EFFECTIVE_URL, null, "ROLE_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, ROLE_PERMISSION_URL, null, "ROLE_NOT_FOUND", MISSING, menu);
    missing(checks, HttpMethod.PUT, ROLE_PERMISSION_URL, null, "PERMISSION_NOT_FOUND", role, MISSING);
    missing(checks, HttpMethod.DELETE, ROLE_PERMISSION_URL, null, "ROLE_NOT_FOUND", MISSING, menu);
    missing(checks, HttpMethod.DELETE, ROLE_PERMISSION_URL, null, "PERMISSION_NOT_FOUND", role, MISSING);
    missing(checks, HttpMethod.PUT, ROLE_INHERIT_URL, null, "ROLE_NOT_FOUND", MISSING, role);
    missing(checks, HttpMethod.PUT, ROLE_INHERIT_URL, null, "ROLE_NOT_FOUND", role, MISSING);
    missing(checks, HttpMethod.DELETE, ROLE_INHERIT_URL, null, "ROLE_NOT_FOUND", MISSING, role);
    missing(checks, HttpMethod.DELETE, ROLE_INHERIT_URL, null, "ROLE_NOT_FOUND", role, MISSING);
    missing(
        checks,
        HttpMethod.PUT,
        PERMISSION_URL,
        Map.of("name", "不存在", "type", "MENU", "menuPath", "/missing"),
        "PERMISSION_NOT_FOUND",
        MISSING);
    missing(checks, HttpMethod.DELETE, PERMISSION_URL, null, "PERMISSION_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, PERMISSION_STATUS_URL, active, "PERMISSION_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.GET, PERMISSION_RESOURCES_URL, null, "PERMISSION_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, PERMISSION_RESOURCE_URL, null, "PERMISSION_NOT_FOUND", MISSING, resource);
    missing(checks, HttpMethod.PUT, PERMISSION_RESOURCE_URL, null, "API_RESOURCE_NOT_FOUND", menu, MISSING);
    missing(checks, HttpMethod.DELETE, PERMISSION_RESOURCE_URL, null, "PERMISSION_NOT_FOUND", MISSING, resource);
    missing(checks, HttpMethod.DELETE, PERMISSION_RESOURCE_URL, null, "API_RESOURCE_NOT_FOUND", menu, MISSING);
    missing(checks, HttpMethod.PUT, RESOURCE_URL, rename, "API_RESOURCE_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.DELETE, RESOURCE_URL, null, "API_RESOURCE_NOT_FOUND", MISSING);
    missing(checks, HttpMethod.PUT, RESOURCE_STATUS_URL, active, "API_RESOURCE_NOT_FOUND", MISSING);
    assertAll(checks);
  }

  // ---------- 辅助 ----------

  /** 登记一条“id 不存在”的检查，失败信息带上接口和参数，便于一次看到全部不符合项。 */
  private void missing(
      List<Executable> checks, HttpMethod method, String url, Object body, String code, Object... ids) {
    checks.add(() -> expectError(method, url, admin, body, 404, code, ids));
  }

  private long id(String sql) {
    return db.queryForObject(sql, Long.class);
  }

  private Map<String, Object> user(String username) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("displayName", username);
    body.put("password", PASSWORD);
    body.put("personType", "EMPLOYEE");
    return body;
  }

  private Map<String, Object> menuBody(String code, String path, Long parentId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("systemId", system);
    body.put("code", code);
    body.put("name", code);
    body.put("type", "MENU");
    body.put("menuPath", path);
    if (parentId != null) body.put("parentId", parentId);
    return body;
  }

  private Map<String, Object> buttonBody(String code, Long parentId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("systemId", system);
    body.put("code", code);
    body.put("name", code);
    body.put("type", "BUTTON");
    if (parentId != null) body.put("parentId", parentId);
    return body;
  }

  private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder) {
    return builder.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + admin);
  }

  private MockHttpServletRequestBuilder send(
      HttpMethod method, String url, String token, Object body, Object... vars) {
    MockHttpServletRequestBuilder builder =
        request(method, url, vars).contentType(MediaType.APPLICATION_JSON);
    if (token != null) builder.header("Authorization", "Bearer " + token);
    if (body != null) builder.content(json.writeValueAsString(body));
    return builder;
  }

  private JsonNode call(
      HttpMethod method, String url, String token, Object body, int status, Object... vars)
      throws Exception {
    MockHttpServletResponse response = mvc.perform(send(method, url, token, body, vars)).andReturn().getResponse();
    String text = response.getContentAsString(StandardCharsets.UTF_8);
    assertEquals(status, response.getStatus(), () -> method + " " + url + " " + List.of(vars) + "：" + text);
    return text.isEmpty() ? null : json.readTree(text);
  }

  private void expectError(
      HttpMethod method, String url, String token, Object body, int status, String code, Object... vars)
      throws Exception {
    JsonNode error = call(method, url, token, body, status, vars);
    assertEquals(code, error.get("code").asString(), () -> method + " " + url + " " + List.of(vars));
  }

  private void assertBadRequest(MockHttpServletRequestBuilder builder) throws Exception {
    expectErrorResponse(mvc.perform(builder).andReturn().getResponse(), 400, "BAD_REQUEST");
  }

  private void expectErrorResponse(MockHttpServletResponse response, int status, String code)
      throws Exception {
    String text = response.getContentAsString(StandardCharsets.UTF_8);
    assertEquals(status, response.getStatus(), text);
    assertEquals(code, json.readTree(text).get("code").asString(), text);
  }

  private long create(String url, Map<String, Object> body) throws Exception {
    return call(HttpMethod.POST, url, admin, body, 201).get("id").asLong();
  }

  private String login(String username) throws Exception {
    return call(HttpMethod.POST, LOGIN_URL, null, Map.of("username", username, "password", PASSWORD), 200)
        .get("accessToken")
        .asString();
  }
}
