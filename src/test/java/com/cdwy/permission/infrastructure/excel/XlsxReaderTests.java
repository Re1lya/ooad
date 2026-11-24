package com.cdwy.permission.infrastructure.excel;

import static org.junit.jupiter.api.Assertions.*;

import com.cdwy.permission.service.port.error.InvalidDataException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 员工信息表的 Excel 读取（FR-24）：按单元格地址放置列、读取内联与共享字符串，并拒绝超限和不安全的文件。
 *
 * <p>直接调用 {@link XlsxReader#rows(byte[])}，用内存中拼出的最小 XLSX（ZIP + 工作表 XML）构造各种输入，不启动 Spring。
 */
class XlsxReaderTests {
  private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final String RELATIONSHIPS =
      "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
  private static final String WORKSHEET_TYPE = RELATIONSHIPS + "/worksheet";

  @Test
  @Tag("等价类")
  @DisplayName("FR-24 内联字符串与共享字符串都能读出；按单元格地址放列，缺的列补空；全空的行跳过")
  void FR24_readsInlineAndSharedStringsByAddress() {
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put(
        "xl/sharedStrings.xml",
        bytes("<sst xmlns=\"" + MAIN + "\"><si><t>共享</t></si><si><r><t>富</t></r><r><t>文本</t></r></si></sst>"));
    files.put(
        "xl/worksheets/sheet1.xml",
        sheet(
            "<row><c r=\"A1\" t=\"inlineStr\"><is><t> 内联 </t></is></c><c r=\"C1\" t=\"s\"><v>0</v></c></row>"
                + "<row><c r=\"B2\" t=\"inlineStr\"><is><t> </t></is></c></row>"
                + "<row><c r=\"B3\" t=\"s\"><v>1</v></c></row>"));
    assertEquals(
        List.of(List.of("内联", "", "共享"), List.of("", "富文本")), XlsxReader.rows(zip(files)));
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-24 列位置上限为第 64 列：BL 列可以，BM 列拒绝")
  void FR24_columnLimit() {
    assertEquals(64, XlsxReader.rows(simple("<row>" + cell("BL1", "x") + "</row>")).getFirst().size());
    assertEquals("Excel列位置不合法", error(simple("<row>" + cell("BM1", "x") + "</row>")));
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-24 行数上限为表头加 20001 行：20002 行可以，20003 行拒绝")
  void FR24_rowLimit() {
    assertEquals(20002, XlsxReader.rows(simple(rows(20002))).size());
    assertEquals("Excel最多20000条数据", error(simple(rows(20003))));
  }

  @Test
  @Tag("边界值")
  @DisplayName("FR-24 文件大小上限 8,000,000 字节：恰好上限时继续解析，多一个字节直接拒绝")
  void FR24_fileSizeLimit() {
    assertNotEquals("单个Excel不能超过8MB", error(new byte[8_000_000]));
    assertEquals("单个Excel不能超过8MB", error(new byte[8_000_001]));
  }

  @Test
  @Tag("边界值")
  @Tag("异常")
  @DisplayName("FR-24 解压后总大小上限 40,000,000 字节：恰好上限可以读取，多一个字节按压缩炸弹拒绝")
  void FR24_expandedSizeLimit() {
    byte[] sheet = sheet("<row>" + cell("A1", "x") + "</row>");
    Map<String, byte[]> atLimit = new LinkedHashMap<>();
    atLimit.put("xl/worksheets/sheet1.xml", sheet);
    atLimit.put("xl/media/padding.bin", new byte[40_000_000 - sheet.length]);
    assertEquals(List.of(List.of("x")), XlsxReader.rows(zip(atLimit)));
    atLimit.put("xl/media/padding.bin", new byte[40_000_000 - sheet.length + 1]);
    assertEquals("Excel展开内容过大", error(zip(atLimit)));
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-24 工作簿关系不安全或不完整时拒绝：缺关系文件、外部链接、不是工作表、路径越出 xl/worksheets、找不到首张表")
  void FR24_rejectsUnsafeWorkbookRelationships() {
    assertEquals("Excel工作簿缺少工作表关系", error(workbook(null)));
    assertEquals("Excel首张表关系不合法", error(workbook(relation("rId1", WORKSHEET_TYPE, "http://evil.example/a.xml", true))));
    assertEquals("Excel首张表关系不合法", error(workbook(relation("rId1", RELATIONSHIPS + "/styles", "styles.xml", false))));
    assertEquals("Excel工作表位置不合法", error(workbook(relation("rId1", WORKSHEET_TYPE, "../../etc/passwd", false))));
    assertEquals("Excel未找到首张工作表", error(workbook(relation("rId9", WORKSHEET_TYPE, "worksheets/sheet1.xml", false))));
    assertEquals(List.of(List.of("x")), XlsxReader.rows(workbook(relation("rId1", WORKSHEET_TYPE, "worksheets/sheet1.xml", false))));
  }

  @Test
  @Tag("异常")
  @DisplayName("FR-24 带 DTD 实体的工作表（XXE）、指向不存在的共享字符串、缺少工作表都按无法读取拒绝")
  void FR24_rejectsMalformedContent() {
    String entity =
        "<?xml version=\"1.0\"?><!DOCTYPE worksheet [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
            + "<worksheet xmlns=\"" + MAIN + "\"><sheetData><row><c r=\"A1\" t=\"inlineStr\"><is><t>&x;</t></is></c></row></sheetData></worksheet>";
    assertEquals("无法读取Excel，请检查文件格式与内容", error(zip(Map.of("xl/worksheets/sheet1.xml", bytes(entity)))));
    assertEquals(
        "无法读取Excel，请检查文件格式与内容",
        error(simple("<row><c r=\"A1\" t=\"s\"><v>5</v></c></row>")));
    assertEquals("Excel缺少第一张工作表", error(zip(Map.of("docProps/app.xml", bytes("<Properties/>")))));
  }

  // ---------- 构造 XLSX ----------

  /** 只有 xl/worksheets/sheet1.xml 的工作簿。 */
  static byte[] simple(String rows) {
    return zip(Map.of("xl/worksheets/sheet1.xml", sheet(rows)));
  }

  /** 带 workbook.xml 的工作簿：首张表的关系编号为 rId1；relations 为 null 时不放关系文件。 */
  static byte[] workbook(String relations) {
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put(
        "xl/workbook.xml",
        bytes("<workbook xmlns=\"" + MAIN + "\" xmlns:r=\"" + RELATIONSHIPS + "\"><sheets>"
            + "<sheet name=\"员工\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"));
    if (relations != null)
      files.put(
          "xl/_rels/workbook.xml.rels",
          bytes("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + relations + "</Relationships>"));
    files.put("xl/worksheets/sheet1.xml", sheet("<row>" + cell("A1", "x") + "</row>"));
    return zip(files);
  }

  static String relation(String id, String type, String target, boolean external) {
    return "<Relationship Id=\"" + id + "\" Type=\"" + type + "\" Target=\"" + target + "\""
        + (external ? " TargetMode=\"External\"" : "") + "/>";
  }

  static byte[] sheet(String rows) {
    return bytes("<worksheet xmlns=\"" + MAIN + "\"><sheetData>" + rows + "</sheetData></worksheet>");
  }

  static String cell(String address, String text) {
    return "<c r=\"" + address + "\" t=\"inlineStr\"><is><t>" + text + "</t></is></c>";
  }

  static String rows(int count) {
    StringBuilder rows = new StringBuilder();
    for (int i = 1; i <= count; i++) rows.append("<row>").append(cell("A" + i, "r")).append("</row>");
    return rows.toString();
  }

  static byte[] zip(Map<String, byte[]> files) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(out)) {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
          zip.putNextEntry(new ZipEntry(file.getKey()));
          zip.write(file.getValue());
          zip.closeEntry();
        }
      }
      return out.toByteArray();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  /** 读取应失败：断言业务码为 EXCEL_INVALID，返回提示消息。 */
  static String error(byte[] file) {
    InvalidDataException error = assertThrows(InvalidDataException.class, () -> XlsxReader.rows(file));
    assertEquals("EXCEL_INVALID", error.getCode());
    return error.getMessage();
  }
}
