package com.cdwy.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.auth.AdminBootstrapService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** FR-24：真实 XLSX 预览、原字段保留、幂等和导入失败零写入。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:company_import_tests;DB_CLOSE_DELAY=-1",
      "permission.jwt.secret=company-import-tests-signing-secret-32-bytes"
    })
@AutoConfigureMockMvc
class CompanyImportTests {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AdminBootstrapService bootstrap;
  @Autowired UserAccountRepository users;
  String token;
  static final String PASS = "Fixture-2026!";

  @BeforeEach
  void before() throws Exception {
    db.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table :
        db.queryForList(
            "select table_name from information_schema.tables where table_schema='PUBLIC' and"
                + " table_type='BASE TABLE' and table_name not like 'HT%'",
            String.class)) db.execute("DELETE FROM " + table);
    db.execute("SET REFERENTIAL_INTEGRITY TRUE");
    bootstrap.initialize("import-admin", PASS);
    String body =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            java.util.Map.of("username", "import-admin", "password", PASS))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    token = json.readTree(body).get("accessToken").asText();
  }

  /** 无视错误的 worksheet dimension，前向填充合并表头并验证不落库。 */
  @Test
  void FR24_previewReadsActualRowsAndCompanyQualifiedDepartment() throws Exception {
    mvc.perform(
            multipart("/api/imports/company/preview")
                .file(employees(false))
                .file(departments())
                .file(permissions())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employees").value(2))
        .andExpect(jsonPath("$.departments").value(2))
        .andExpect(jsonPath("$.systems").value(1))
        .andExpect(jsonPath("$.functions").value(2))
        .andExpect(jsonPath("$.qualifiedDepartments").value(1))
        .andExpect(jsonPath("$.digest").isNotEmpty());
    assertEquals(1, db.queryForObject("select count(*) from sys_user", Integer.class));
    assertEquals(0, db.queryForObject("select count(*) from sys_department", Integer.class));
  }

  /** 确认摘要绑定原文件，提交保留原字段且重复导入不增加记录。 */
  @Test
  void FR25_commitIsAtomicIdempotentAndPreservesSourceFields() throws Exception {
    String preview =
        mvc.perform(
                multipart("/api/imports/company/preview")
                    .file(employees(false))
                    .file(departments())
                    .file(permissions())
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String digest = json.readTree(preview).get("digest").asText();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              multipart("/api/imports/company/commit")
                  .file(employees(false))
                  .file(departments())
                  .file(permissions())
                  .param("digest", digest)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.employees").value(2));
    assertEquals(3, db.queryForObject("select count(*) from sys_user", Integer.class));
    String body =
        mvc.perform(get("/api/users?size=100").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(body.contains("01000000001"));
    assertTrue(body.contains("工程师"));
    assertEquals(
        0,
        db.queryForObject(
            "select count(*) from sys_user where display_name='测试甲' and enabled=true",
            Integer.class));
    assertEquals(5, db.queryForObject("select count(*) from sys_role where code <> 'rbac-admin'", Integer.class));
    assertEquals(0, db.queryForObject(
            "select count(*) from permission_api pa join api_resource r on r.id = pa.resource_id"
                + " join biz_system s on s.id = r.system_id where s.code <> 'rbac'",
            Integer.class));
    assertEquals(0, db.queryForObject(
            "select count(*) from sys_user_role ur join sys_role r on r.id = ur.role_id"
                + " where r.code <> 'rbac-admin'",
            Integer.class));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from sys_permission where code like '%view_self'", Integer.class));
  }

  /** 存在未匹配部门或摘要变化时拒绝整个批次；普通用户不能导入。 */
  @Test
  void FR25_invalidBatchWritesNothingAndRequiresAdministrator() throws Exception {
    mvc.perform(
            multipart("/api/imports/company/commit")
                .file(employees(true))
                .file(departments())
                .file(permissions())
                .param("digest", "wrong")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    assertEquals(0, db.queryForObject("select count(*) from sys_department", Integer.class));
    mvc.perform(
            multipart("/api/imports/company/preview")
                .file(employees(false))
                .file(departments())
                .file(permissions()))
        .andExpect(status().isUnauthorized());
    String preview =
        mvc.perform(
                multipart("/api/imports/company/preview")
                    .file(employees(false))
                    .file(departments())
                    .file(permissions())
                    .header("Authorization", "Bearer " + token))
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            multipart("/api/imports/company/commit")
                .file(employees(false))
                .file(departments())
                .file(permissions())
                .param("digest", "wrong")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    assertEquals(0, db.queryForObject("select count(*) from sys_department", Integer.class));
  }

  /** 已写入前面的部门和员工后遇到管理员冲突，事务必须全部回滚。 */
  @Test
  void FR25_existingAdministratorConflictRollsBackEarlierWrites() throws Exception {
    MockMultipartFile employee =
        xlsx(
            "employees",
            new String[][] {
              {"姓名", "用户名", "分公司", "部门", "岗位", "电话号码"},
              {"先写入员工", "BEFORE001", "测试分公司", "项目部", "工程师", "01000000000"},
              {"冲突员工", "import-admin", "测试分公司", "项目部", "职员", "01000000001"}
            });
    String response =
        mvc.perform(
                multipart("/api/imports/company/preview")
                    .file(employee)
                    .file(departments())
                    .file(permissions())
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            multipart("/api/imports/company/commit")
                .file(employee)
                .file(departments())
                .file(permissions())
                .param("digest", json.readTree(response).get("digest").asText())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
    assertEquals(1, db.queryForObject("select count(*) from sys_user", Integer.class));
    assertEquals(0, db.queryForObject("select count(*) from sys_department", Integer.class));
  }

  /** 模板缩减权限会撤回旧操作；本人操作始终不扩成全量查看。 */
  @Test
  void FR25_templateReductionRevokesStaleGrantsWithoutExpandingSelf() throws Exception {
    for (boolean reduce : new boolean[] {false, true}) {
      MockMultipartFile permission = permissionMatrix(reduce ? "无" : "view/编辑");
      String response =
          mvc.perform(
                  multipart("/api/imports/company/preview")
                      .file(employees(false))
                      .file(departments())
                      .file(permission)
                      .header("Authorization", "Bearer " + token))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      mvc.perform(
              multipart("/api/imports/company/commit")
                  .file(employees(false))
                  .file(departments())
                  .file(permission)
                  .param("digest", json.readTree(response).get("digest").asText())
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isOk());
      assertEquals(
          reduce ? 0 : 2,
          db.queryForObject(
              "select count(*) from sys_role_permission rp join sys_role r on r.id=rp.role_id where"
                  + " r.name='公司领导' and rp.permission_id in (select id from sys_permission where"
                  + " parent_id in (select id from sys_permission where name='功能乙'))",
              Integer.class));
    }
    assertEquals(
        java.util.List.of("查看本人"),
        db.queryForList(
            "select p.name from sys_role_permission rp join sys_role r on r.id=rp.role_id join"
                + " sys_permission p on p.id=rp.permission_id where r.name='普通员工'",
            String.class));
  }

  /** 已登录普通员工也必须被管理员导入边界拒绝。 */
  @Test
  void FR24_authenticatedEmployeeCannotPreviewOrCommit() throws Exception {
    mvc.perform(
            post("/api/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        java.util.Map.of(
                            "username", "ordinary", "displayName", "合成普通员工", "password", PASS))))
        .andExpect(status().isCreated());
    String response =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            java.util.Map.of("username", "ordinary", "password", PASS))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ordinary = json.readTree(response).get("accessToken").asText();
    for (String endpoint : new String[] {"preview", "commit"})
      mvc.perform(
              multipart("/api/imports/company/" + endpoint)
                  .file(employees(false))
                  .file(departments())
                  .file(permissions())
                  .param("digest", "irrelevant")
                  .header("Authorization", "Bearer " + ordinary))
          .andExpect(status().isForbidden());
  }

  MockMultipartFile employees(boolean invalid) throws Exception {
    return xlsx(
        "employees",
        new String[][] {
          {"姓名", "用户名", "分公司", "部门", "岗位", "电话号码"},
          {"测试甲", "SGFIX001", "测试分公司", "项目部", "工程师", "01000000001"},
          {"测试乙", "SGFIX002", "测试分公司", invalid ? "不存在" : "测试分公司", "职员", "01000000002"}
        });
  }

  MockMultipartFile departments() throws Exception {
    return xlsx(
        "departments",
        new String[][] {
          {"部门名称", "部门经理", "上级部门"}, {"测试分公司", "负责人甲", ""}, {"测试分公司-项目部", "负责人乙", "测试分公司"}
        });
  }

  MockMultipartFile permissions() throws Exception {
    return permissionMatrix("view/编辑");
  }

  MockMultipartFile permissionMatrix(String leader) throws Exception {
    return xlsx(
        "permissions",
        new String[][] {
          {"市政公司权限清单"},
          {"软件系统", "功能模块", "功能点", "功能说明", "系统管理员", "公司领导", "部门经理", "项目经理", "普通员工"},
          {"测试应用", "测试模块", "功能甲", "功能说明甲", "全部", "查看", "无", "无", "查看本人"},
          {"", "", "功能乙", "功能说明乙", "全部", leader, "查看", "无", "无"}
        });
  }

  /** 工作簿首张表可能叫 sheet2.xml，必须按关系解析，不能假定 sheet1。 */
  @Test
  void FR24_firstWorksheetUsesWorkbookRelationshipInsteadOfFixedFilename() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream output = new ZipOutputStream(bytes);
        java.util.zip.ZipInputStream input =
            new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(employees(false).getBytes()))) {
      ZipEntry next;
      while ((next = input.getNextEntry()) != null) {
        String name = next.getName().replace("sheet1.xml", "sheet2.xml");
        entry(output, name, new String(input.readAllBytes(), StandardCharsets.UTF_8));
      }
      entry(
          output,
          "xl/workbook.xml",
          "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
              + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet"
              + " name=\"员工\" sheetId=\"2\" r:id=\"rId5\"/></sheets></workbook>");
      entry(
          output,
          "xl/_rels/workbook.xml.rels",
          "<Relationships"
              + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
              + " Id=\"rId5\" Target=\"worksheets/sheet2.xml\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"/></Relationships>");
    }
    MockMultipartFile employee =
        new MockMultipartFile(
            "employees",
            "renamed.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes.toByteArray());
    mvc.perform(
            multipart("/api/imports/company/preview")
                .file(employee)
                .file(departments())
                .file(permissions())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employees").value(2));
  }

  /** 空白角色单元格明确提示并保持未授权，不能猜测为全部。 */
  @Test
  void FR24_blankGrantIsReportedAndDoesNotAssignPermission() throws Exception {
    MockMultipartFile permission =
        xlsx(
            "permissions",
            new String[][] {
              {"权限清单"},
              {"软件系统", "功能模块", "功能点", "功能说明", "系统管理员", "公司领导", "部门经理", "项目经理", "普通员工"},
              {"合成应用", "合成模块", "合成功能", "说明", "全部", "查看", "无", "无"}
            });
    String response =
        mvc.perform(
                multipart("/api/imports/company/preview")
                    .file(employees(false))
                    .file(departments())
                    .file(permission)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains("1 个空白"));
    mvc.perform(
            multipart("/api/imports/company/commit")
                .file(employees(false))
                .file(departments())
                .file(permission)
                .param("digest", json.readTree(response).get("digest").asText())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertEquals(
        0,
        db.queryForObject(
            "select count(*) from sys_role_permission rp join sys_role r on r.id=rp.role_id where"
                + " r.name='普通员工'",
            Integer.class));
  }

  /** 构建合成数据的最小合法 OOXML；不包含真实员工信息。 */
  static MockMultipartFile xlsx(String part, String[][] rows) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      entry(
          zip,
          "[Content_Types].xml",
          "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default"
              + " Extension=\"xml\" ContentType=\"application/xml\"/></Types>");
      StringBuilder xml =
          new StringBuilder(
              "<worksheet"
                  + " xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><dimension"
                  + " ref=\"A1\"/><sheetData>");
      for (int i = 0; i < rows.length; i++) {
        xml.append("<row r=\"").append(i + 1).append("\">");
        for (int j = 0; j < rows[i].length; j++)
          xml.append("<c r=\"")
              .append((char) ('A' + j))
              .append(i + 1)
              .append("\" t=\"inlineStr\"><is><t>")
              .append(rows[i][j])
              .append("</t></is></c>");
        xml.append("</row>");
      }
      xml.append("</sheetData></worksheet>");
      entry(zip, "xl/worksheets/sheet1.xml", xml.toString());
    }
    return new MockMultipartFile(
        part,
        part + ".xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        bytes.toByteArray());
  }

  static void entry(ZipOutputStream zip, String path, String text) throws Exception {
    ZipEntry entry = new ZipEntry(path);
    entry.setTime(0);
    zip.putNextEntry(entry);
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
