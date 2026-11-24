package com.cdwy.permission;

import static com.cdwy.permission.support.TestUrls.url;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.service.auth.AdminBootstrapService;
import com.cdwy.permission.service.port.data.DirectoryData;
import com.cdwy.permission.support.RequestTransactionBoundary;
import com.cdwy.permission.support.TestDatabase;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * FR-24 / FR-25：员工信息同步。上传与课程管理系统《市政公司员工信息表》同格式的员工表，先预览、再凭摘要确认写入，可重复执行。
 *
 * <p>测试数据全部为合成数据，不含真实员工信息。
 */
@ActiveProfiles("test")
@Transactional
@Import(RequestTransactionBoundary.class)
@SpringBootTest(
    properties = {
      "permission.jwt.secret=directory-sync-tests-signing-secret-32-bytes"
    })
@AutoConfigureMockMvc
class DirectorySyncTests {
  private static final String LOGIN_URL = "/api/auth/login";
  private static final String ROLES_URL = "/api/roles";
  private static final String ROLE_PERMISSION_URL =
      "/api/roles/{roleId}/permissions/{permissionId}";
  private static final String SYNC_PREVIEW_URL = "/api/directory/sync/preview";
  private static final String SYNC_STEP_URL = "/api/directory/sync/{step}";
  private static final String USERS_SEARCH_URL =
      "/api/users?page={page}&pageSize={pageSize}&keyword={keyword}";
  private static final String USERS_URL = "/api/users";
  private static final String USER_PASSWORD_URL = "/api/users/{userId}/password";
  private static final String USER_ROLE_URL = "/api/users/{userId}/roles/{roleId}";
  private static final String USER_STATUS_URL = "/api/users/{userId}/status";

  static final String PASSWORD = "Fixture-2026!";
  static final String[] HEADER = {"姓名", "用户名", "分公司", "部门", "岗位", "电话号码"};
  static final String[][] ROWS = {
    {"林思远", "SG000001", "总公司", "工程管理部", "总工程师", "13800000001"},
    {"陈嘉禾", "SG000002", "总公司", "综合办公室", "主任", ""},
    {"周雨桐", "SG000191", "道路工程分公司", "综合办公室", "职员", "13800000191"},
    {"吴承泽", "SG000192", "道路工程分公司", "施工一部", "施工员", "13800000192"},
    {"郑书瑶", "SG000300", "桥梁工程分公司", "综合办公室", "职员", "13800000300"}
  };

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;
  String admin;

  @BeforeEach
  void fixture() throws Exception {
    TestDatabase.clear(db);
    bootstrap.initialize("sync-admin", PASSWORD);
    admin = login("sync-admin", PASSWORD, 200);
    role("teacher", "教师");
    role("student", "学生");
  }

  @Test
  @Tag("等价类")
  @DisplayName("FR-24 预览只计算变化：两级部门、新人员、按分公司补授角色，不写数据库")
  void FR24_previewCountsChangesAndWritesNothing() throws Exception {
    JsonNode summary = sync("preview", workbook(ROWS), rule("teacher", "student"), null, 200);
    assertEquals(
        Set.of(
            "rows",
            "companies",
            "departmentsCreated",
            "departmentsKept",
            "usersCreated",
            "usersUpdated",
            "usersUnchanged",
            "roleGrants",
            "digest",
            "applied"),
        fields(summary));
    assertEquals(5, summary.get("rows").asInt());
    assertEquals(3, summary.get("companies").asInt());
    assertEquals(8, summary.get("departmentsCreated").asInt(), "3 个分公司 + 5 个部门");
    assertEquals(0, summary.get("departmentsKept").asInt());
    assertEquals(5, summary.get("usersCreated").asInt());
    assertEquals(5, summary.get("roleGrants").asInt());
    assertFalse(summary.get("applied").asBoolean());
    assertTrue(summary.get("digest").asString().matches("[0-9a-f]{64}"));
    assertEquals(0, count("select count(*) from sys_department"));
    assertEquals(1, count("select count(*) from sys_account"));
  }

  @Test
  @Tag("等价类")
  @DisplayName("FR-25 确认后建成“分公司 / 部门”两级树，同名部门分属不同分公司；新人员停用且无法登录；按分公司授予教师或学生")
  void FR25_commitBuildsTwoLevelTreeAndUnprovisionedUsers() throws Exception {
    JsonNode result = commit(workbook(ROWS), rule("teacher", "student"));
    assertTrue(result.get("applied").asBoolean());
    assertEquals(8, result.get("departmentsCreated").asInt());
    assertEquals(5, result.get("usersCreated").asInt());
    assertEquals(5, result.get("roleGrants").asInt());

    assertEquals(3, count("select count(*) from sys_department where parent_id is null"));
    assertEquals(
        3,
        count("select count(*) from sys_department where name = '综合办公室' and parent_id is not null"));
    assertEquals(0, count("select count(*) from sys_department where code is null"));
    assertEquals(
        "道路工程分公司",
        db.queryForObject(
            "select p.name from sys_user u join sys_account a on a.user_id = u.id"
                + " join sys_department d on d.id = u.department_id"
                + " join sys_department p on p.id = d.parent_id where a.username = 'SG000191'",
            String.class));
    assertEquals(
        0, count("select count(*) from sys_user where enabled = true and administrator = false"));
    login("SG000191", PASSWORD, 401);
    assertTrue(hasRole("SG000001", "teacher"));
    assertTrue(hasRole("SG000002", "teacher"));
    assertTrue(hasRole("SG000191", "student"));
    assertFalse(hasRole("SG000191", "teacher"));
    JsonNode user =
        result(call(get(url(USERS_SEARCH_URL, 1, 5, "SG000192")), 200)).get("items").get(0);
    assertEquals("吴承泽", user.get("displayName").asString());
    assertEquals("施工员", user.get("jobTitle").asString());
    assertEquals("道路工程分公司", user.get("companyName").asString());
    assertEquals("施工一部", user.get("sourceDepartment").asString());
    assertNotNull(user.get("departmentId"));
  }

  @Test
  @Tag("等价类")
  @DisplayName("FR-25 重复执行同一份表没有任何变化")
  void FR25_rerunIsIdempotent() throws Exception {
    commit(workbook(ROWS), rule("teacher", "student"));
    JsonNode again = sync("preview", workbook(ROWS), rule("teacher", "student"), null, 200);
    assertEquals(0, again.get("departmentsCreated").asInt());
    assertEquals(8, again.get("departmentsKept").asInt());
    assertEquals(0, again.get("usersCreated").asInt());
    assertEquals(0, again.get("usersUpdated").asInt());
    assertEquals(5, again.get("usersUnchanged").asInt());
    assertEquals(0, again.get("roleGrants").asInt());
    commit(workbook(ROWS), rule("teacher", "student"));
    assertEquals(8, count("select count(*) from sys_department"));
    assertEquals(6, count("select count(*) from sys_account"));
    assertEquals(
        5,
        count(
            "select count(*) from sys_user_role ur join sys_role r on r.id = ur.role_id"
                + " where r.code in ('teacher', 'student')"));
  }

  @Test
  @Tag("等价类")
  @DisplayName("FR-25 变化的人员只更新姓名、部门、岗位与电话，保留密码、启用状态与已有角色；不在表中的人员不受影响；角色只增不减")
  void FR25_changesUpdateSourceFieldsAndKeepCredentialsAndGrants() throws Exception {
    commit(workbook(ROWS), rule("teacher", "student"));
    long id = userId("SG000191");
    call(put(url(USER_PASSWORD_URL, id)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("password", PASSWORD))), 204);
    call(put(url(USER_STATUS_URL, id)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("enabled", true))), 200);
    login("SG000191", PASSWORD, 200);

    String[][] changed = {
      {"林思远", "SG000001", "道路工程分公司", "施工一部", "总工程师", "13800000001"},
      {"陈嘉禾", "SG000002", "总公司", "综合办公室", "主任", ""},
      {"周雨彤", "SG000191", "道路工程分公司", "施工一部", "职员", "13800000191"},
      {"吴承泽", "SG000192", "道路工程分公司", "施工一部", "施工员", "13800000192"}
    };
    JsonNode preview = sync("preview", workbook(changed), rule("teacher", "student"), null, 200);
    assertEquals(0, preview.get("departmentsCreated").asInt());
    assertEquals(2, preview.get("usersUpdated").asInt());
    assertEquals(2, preview.get("usersUnchanged").asInt());
    assertEquals(1, preview.get("roleGrants").asInt(), "林思远调到分公司，补授学生");
    commit(workbook(changed), rule("teacher", "student"));

    assertEquals(
        "周雨彤",
        db.queryForObject(
            "select u.display_name from sys_user u join sys_account a on a.user_id = u.id where"
                + " a.username = 'SG000191'",
            String.class));
    assertEquals(
        "施工一部",
        db.queryForObject(
            "select d.name from sys_user u join sys_account a on a.user_id = u.id join"
                + " sys_department d on d.id = u.department_id where a.username = 'SG000191'",
            String.class));
    login("SG000191", PASSWORD, 200);
    assertTrue(hasRole("SG000001", "teacher"), "已有角色不撤销");
    assertTrue(hasRole("SG000001", "student"));
    assertEquals(1, count("select count(*) from sys_account where username = 'SG000300'"));
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-25 确认时文件或规则与预览不一致须重新预览，且不写入")
  void FR25_digestMustMatchFileAndRule() throws Exception {
    byte[] file = workbook(ROWS);
    String digest =
        sync("preview", file, rule("teacher", "student"), null, 200).get("digest").asString();
    JsonNode stale = sync("commit", workbook(Arrays.copyOf(ROWS, 4)), rule("teacher", "student"), digest, 400);
    assertEquals("SYNC_PREVIEW_MISMATCH", stale.get("code").asString());
    assertTrue(stale.get("message").asString().contains("重新预览"));
    sync("commit", file, rule("teacher", null), digest, 400);
    sync("commit", file, Map.of("headOffice", "道路工程分公司", "headOfficeRole", "teacher", "branchRole", "student"), digest, 400);
    assertEquals(0, count("select count(*) from sys_department"));
    sync("commit", file, rule("teacher", "student"), digest, 200);
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-25 表中出现管理员账号时整批拒绝，已经建好的部门也回滚")
  void FR25_administratorConflictRollsBackEverything() throws Exception {
    String[][] rows = Arrays.copyOf(ROWS, ROWS.length + 1);
    rows[ROWS.length] = new String[] {"冒名", "sync-admin", "总公司", "综合办公室", "", ""};
    byte[] file = workbook(rows);
    JsonNode conflict = sync("preview", file, rule("teacher", "student"), null, 409);
    assertEquals("SYNC_ADMINISTRATOR_CONFLICT", conflict.get("code").asString());
    assertTrue(conflict.get("message").asString().contains("sync-admin"));
    Map<String, String> rule = rule("teacher", "student");
    String digest =
        DirectoryData.digest(
            new DirectoryData.Sheet(List.of(), sha256(file)),
            new DirectoryData.Rule(rule.get("headOffice"), rule.get("headOfficeRole"), rule.get("branchRole")));
    sync("commit", file, rule, digest, 409);
    assertEquals(0, count("select count(*) from sys_department"));
    assertEquals(1, count("select count(*) from sys_account"));
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-24 各列长度上限：姓名、分公司、部门、岗位 128 个字符，用户名 64 个字符，电话 32 个字符可以，各超出一位拒绝并指出行号")
  void FR24_columnLengthBoundaries() throws Exception {
    JsonNode preview = sync("preview", workbook(new String[][] {MAX_ROW}), rule(null, null), null, 200);
    assertEquals(1, preview.get("usersCreated").asInt());
    assertTrue(message(new String[][] {HEADER, longer(MAX_ROW, 0)}).contains("第 2 行：姓名超过 128 个字符"));
    assertTrue(message(new String[][] {HEADER, longer(MAX_ROW, 1)}).contains("第 2 行：用户名超过 64 个字符"));
    assertTrue(message(new String[][] {HEADER, longer(MAX_ROW, 2)}).contains("第 2 行：分公司超过 128 个字符"));
    assertTrue(message(new String[][] {HEADER, longer(MAX_ROW, 3)}).contains("第 2 行：部门超过 128 个字符"));
    assertTrue(message(new String[][] {HEADER, longer(MAX_ROW, 4)}).contains("第 2 行：岗位超过 128 个字符"));
    assertTrue(message(new String[][] {HEADER, longer(MAX_ROW, 5)}).contains("第 2 行：电话号码超过 32 个字符"));
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-25 只有一行、且各列都取到上限的员工表可以确认写入，再预览同一张表没有变化")
  void FR25_singleMaximumRowCommitsAndStaysIdempotent() throws Exception {
    JsonNode done = commit(workbook(new String[][] {MAX_ROW}), rule("teacher", "student"));
    assertEquals(1, done.get("usersCreated").asInt());
    JsonNode again = sync("preview", workbook(new String[][] {MAX_ROW}), rule("teacher", "student"), null, 200);
    assertEquals(0, again.get("usersCreated").asInt());
    assertEquals(0, again.get("usersUpdated").asInt());
    assertEquals(1, again.get("usersUnchanged").asInt());
  }

  /** 各列都取到长度上限的一行：姓名、分公司、部门、岗位 128 个字符，用户名 64 个字符，电话 32 个字符。 */
  static final String[] MAX_ROW = {
    "姓".repeat(128), "S".repeat(64), "分".repeat(128), "部".repeat(128), "岗".repeat(128), "1".repeat(32)
  };

  /** 把一行的第 column 列加长一个字符。 */
  static String[] longer(String[] row, int column) {
    String[] copy = row.clone();
    copy[column] = copy[column] + (column == 1 ? "S" : column == 5 ? "1" : "长");
    return copy;
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-24 员工表格式错误逐项拒绝，并指出 Excel 行号")
  void FR24_invalidSheetsAreRejectedWithRowNumbers() throws Exception {
    assertTrue(message(new String[][] {{"姓名", "工号", "分公司", "部门", "岗位", "电话号码"}, ROWS[0]}).contains("表头"));
    assertTrue(message(new String[][] {HEADER}).contains("没有数据行"));
    assertTrue(message(new String[][] {HEADER, ROWS[0], ROWS[0]}).contains("第 3 行"));
    assertTrue(message(new String[][] {HEADER, {"甲", "SG 01", "总公司", "部门", "", ""}}).contains("第 2 行"));
    assertTrue(message(new String[][] {HEADER, {"甲", "SG0001", "", "部门", "", ""}}).contains("第 2 行：分公司不能为空"));
    assertTrue(message(new String[][] {HEADER, {"甲", "SG0001", "总公司", "部门", "", "1".repeat(33)}}).contains("电话号码超过 32 个字符"));
    JsonNode notExcel =
        sync("preview", "不是 Excel".getBytes(StandardCharsets.UTF_8), rule(null, null), null, 400);
    assertEquals("EXCEL_INVALID", notExcel.get("code").asString());
    assertTrue(notExcel.get("message").asString().contains("Excel"));
    assertEquals("BAD_REQUEST", sync("preview", new byte[0], rule(null, null), null, 400).get("code").asString());
    JsonNode noSheet = sync("preview", zipWith("readme.txt", "不是工作表"), rule(null, null), null, 400);
    assertEquals("EXCEL_INVALID", noSheet.get("code").asString());
    assertTrue(noSheet.get("message").asString().contains("缺少第一张工作表"), "具体原因不能被笼统提示替换");
    JsonNode unknownRole = sync("preview", workbook(ROWS), rule("nobody", null), null, 400);
    assertEquals("SYNC_ROLE_NOT_FOUND", unknownRole.get("code").asString());
    assertTrue(unknownRole.get("message").asString().contains("角色不存在"));
    assertEquals(
        "BAD_REQUEST",
        sync("preview", workbook(ROWS), Map.of("headOffice", " "), null, 400).get("code").asString());
  }

  @Test
  @Tag("等价类")
  @DisplayName("FR-24 按工作簿关系定位首张工作表并读取共享字符串（与课程系统提供的原表结构相同）")
  void FR24_firstWorksheetFollowsWorkbookRelationshipAndSharedStrings() throws Exception {
    JsonNode summary = sync("preview", sharedStringWorkbook(ROWS), rule("teacher", "student"), null, 200);
    assertEquals(5, summary.get("rows").asInt());
    assertEquals(5, summary.get("usersCreated").asInt());
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-38 员工信息同步须拥有“同步员工信息”按钮的授权")
  void FR38_syncRequiresDirectorySyncGrant() throws Exception {
    long operator =
        result(
                call(
                    post(USERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "username", "operator", "displayName", "同步员",
                                    "password", PASSWORD))),
                    201))
            .get("id")
            .asLong();
    String token = login("operator", PASSWORD, 200);
    mvc.perform(multipart(SYNC_PREVIEW_URL).file(file(workbook(ROWS))).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    long role = role("hr", "人事专员");
    long sync = db.queryForObject("select id from sys_permission where code = 'rbac.directory.sync'", Long.class);
    call(put(url(ROLE_PERMISSION_URL, role, sync)), 204);
    call(put(url(USER_ROLE_URL, operator, role)), 204);
    mvc.perform(multipart(SYNC_PREVIEW_URL).file(file(workbook(ROWS))).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @Tag("边界值")
  @DisplayName("NFR-08 一万行员工表（与课程系统员工表同规模）在一个事务内同步，部门与人员按数据库分页查询")
  void NFR08_tenThousandRows() throws Exception {
    String[] companies = {"总公司", "道路工程分公司", "桥梁工程分公司", "隧道工程分公司", "轨道交通分公司", "管网工程分公司", "水务工程分公司", "园林绿化分公司", "照明工程分公司", "市政养护分公司"};
    String[] departments = {"综合办公室", "财务部", "市场经营部", "工程技术部", "安全质量部", "物资设备部", "人力资源部", "第一项目部", "第二项目部", "第三项目部", "第四项目部"};
    String[][] rows = new String[10_000][];
    for (int i = 0; i < rows.length; i++) {
      String company = i < 190 ? companies[0] : companies[1 + (i - 190) % 9];
      rows[i] =
          new String[] {
            "员工" + i, String.format("SG%06d", i + 1), company, departments[i % departments.length], "职员", ""
          };
    }
    byte[] file = workbook(rows);
    long started = System.nanoTime();
    JsonNode result = commit(file, rule("teacher", "student"));
    long seconds = (System.nanoTime() - started) / 1_000_000_000;
    System.out.println("一万行员工信息同步耗时 " + seconds + " 秒");
    assertEquals(10_000, result.get("usersCreated").asInt());
    assertEquals(10 + 10 * 11, result.get("departmentsCreated").asInt());
    assertEquals(10_000, result.get("roleGrants").asInt());
    assertEquals(190, count("select count(*) from sys_user_role ur join sys_role r on r.id = ur.role_id where r.code = 'teacher'"));
    assertEquals(9_810, count("select count(*) from sys_user_role ur join sys_role r on r.id = ur.role_id where r.code = 'student'"));
    JsonNode again = sync("preview", file, rule("teacher", "student"), null, 200);
    assertEquals(10_000, again.get("usersUnchanged").asInt());
    assertEquals(0, again.get("roleGrants").asInt());
  }

  // ---------- 辅助 ----------

  /** 只含一个普通文件的 ZIP：能解压，但没有任何工作表。 */
  static byte[] zipWith(String name, String content) throws Exception {
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
      zip.putNextEntry(new java.util.zip.ZipEntry(name));
      zip.write(content.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }

  JsonNode commit(byte[] file, Map<String, String> rule) throws Exception {
    String digest = sync("preview", file, rule, null, 200).get("digest").asString();
    return sync("commit", file, rule, digest, 200);
  }

  JsonNode sync(String step, byte[] file, Map<String, String> rule, String digest, int status)
      throws Exception {
    MockMultipartHttpServletRequestBuilder request =
        multipart(url(SYNC_STEP_URL, step)).file(file(file));
    rule.forEach((k, v) -> request.param(k, v));
    if (digest != null) request.param("digest", digest);
    request.header("Authorization", "Bearer " + admin);
    return result(mvc.perform(request).andExpect(status().is(status)).andReturn());
  }

  /** 预览一张格式错误的员工表：断言业务码为 EMPLOYEE_SHEET_INVALID，返回提示消息。 */
  String message(String[][] table) throws Exception {
    JsonNode error = sync("preview", rawWorkbook(table), rule(null, null), null, 400);
    assertEquals("EMPLOYEE_SHEET_INVALID", error.get("code").asString());
    return error.get("message").asString();
  }

  static Map<String, String> rule(String headOfficeRole, String branchRole) {
    Map<String, String> rule = new LinkedHashMap<>();
    rule.put("headOffice", "总公司");
    if (headOfficeRole != null) rule.put("headOfficeRole", headOfficeRole);
    if (branchRole != null) rule.put("branchRole", branchRole);
    return rule;
  }

  static MockMultipartFile file(byte[] bytes) {
    return new MockMultipartFile(
        "employees",
        "员工信息表.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        bytes);
  }

  long role(String code, String name) throws Exception {
    return result(
            call(
                post(ROLES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("code", code, "name", name))),
                201))
        .get("id")
        .asLong();
  }

  boolean hasRole(String username, String role) {
    return count(
            "select count(*) from sys_user_role ur join sys_role r on r.id = ur.role_id join"
                + " sys_account a on a.user_id = ur.user_id where a.username = '"
                + username
                + "' and r.code = '"
                + role
                + "'")
        == 1;
  }

  long userId(String username) {
    return db.queryForObject("select user_id from sys_account where username = ?", Long.class, username);
  }

  long count(String sql) {
    return db.queryForObject(sql, Long.class);
  }

  MvcResult call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status) throws Exception {
    return mvc.perform(request.header("Authorization", "Bearer " + admin)).andExpect(status().is(status)).andReturn();
  }

  String login(String username, String password, int status) throws Exception {
    MvcResult result =
        mvc.perform(
                post(LOGIN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", username, "password", password))))
            .andExpect(status().is(status))
            .andReturn();
    return status == 200 ? result(result).get("accessToken").asString() : null;
  }

  JsonNode result(MvcResult result) {
    byte[] body = result.getResponse().getContentAsByteArray();
    return body.length == 0 ? json.createObjectNode() : json.readTree(new String(body, StandardCharsets.UTF_8));
  }

  Set<String> fields(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.propertyNames().forEach(names::add);
    return names;
  }

  static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  /** 员工表：表头加数据行。 */
  static byte[] workbook(String[][] rows) throws Exception {
    String[][] table = new String[rows.length + 1][];
    table[0] = HEADER;
    System.arraycopy(rows, 0, table, 1, rows.length);
    return rawWorkbook(table);
  }

  /** 最小合法 OOXML：内联字符串、固定 sheet1；表格原样写入（用于构造格式错误的表）。 */
  static byte[] rawWorkbook(String[][] table) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      StringBuilder xml =
          new StringBuilder(
              "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
      for (int i = 0; i < table.length; i++) {
        xml.append("<row r=\"").append(i + 1).append("\">");
        for (int j = 0; j < table[i].length; j++)
          xml.append("<c r=\"")
              .append((char) ('A' + j))
              .append(i + 1)
              .append("\" t=\"inlineStr\"><is><t>")
              .append(table[i][j])
              .append("</t></is></c>");
        xml.append("</row>");
      }
      xml.append("</sheetData></worksheet>");
      entry(zip, "xl/worksheets/sheet1.xml", xml.toString());
    }
    return bytes.toByteArray();
  }

  /** 与课程系统原表相同的结构：工作簿关系指向 sheet2.xml，文本放在共享字符串表中。 */
  static byte[] sharedStringWorkbook(String[][] rows) throws Exception {
    List<String> shared = new ArrayList<>();
    StringBuilder sheet =
        new StringBuilder(
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
    String[][] table = new String[rows.length + 1][];
    table[0] = HEADER;
    System.arraycopy(rows, 0, table, 1, rows.length);
    for (int i = 0; i < table.length; i++) {
      sheet.append("<row r=\"").append(i + 1).append("\">");
      for (int j = 0; j < table[i].length; j++) {
        if (table[i][j].isEmpty()) continue;
        shared.add(table[i][j]);
        sheet.append("<c r=\"").append((char) ('A' + j)).append(i + 1).append("\" t=\"s\"><v>").append(shared.size() - 1).append("</v></c>");
      }
      sheet.append("</row>");
    }
    sheet.append("</sheetData></worksheet>");
    StringBuilder strings = new StringBuilder("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
    shared.forEach(s -> strings.append("<si><t>").append(s).append("</t></si>"));
    strings.append("</sst>");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      entry(
          zip,
          "xl/workbook.xml",
          "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
              + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
              + "<sheets><sheet name=\"员工信息\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
      entry(
          zip,
          "xl/_rels/workbook.xml.rels",
          "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
              + " Target=\"worksheets/sheet2.xml\"/></Relationships>");
      entry(zip, "xl/worksheets/sheet2.xml", sheet.toString());
      entry(zip, "xl/sharedStrings.xml", strings.toString());
    }
    return bytes.toByteArray();
  }

  static void entry(ZipOutputStream zip, String path, String text) throws Exception {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
