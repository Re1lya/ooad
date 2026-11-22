package com.cdwy.permission.infrastructure.excel;

import com.cdwy.permission.service.port.data.DirectoryData;
import com.cdwy.permission.service.port.directory.EmployeeSheetReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.cdwy.permission.service.port.error.InvalidDataException;

/**
 * 员工信息表（.xlsx）读取：表头必须是“姓名、用户名、分公司、部门、岗位、电话号码”，与课程管理系统提供的《市政公司员工信息表》一致。
 *
 * <p>逐行校验必填、长度、用户名格式与表内重复；错误信息带 Excel 行号。文件只在请求内存中处理。
 */
@Component
public class XlsxEmployeeSheetReader implements EmployeeSheetReader {
  /** 期望的表头，列名与顺序都须一致。 */
  static final List<String> HEADER = List.of("姓名", "用户名", "分公司", "部门", "岗位", "电话号码");

  /**
   * 首行须与 {@code HEADER} 完全一致，其后至少一行数据；任一行不合法即整表拒绝，抛出业务码 {@code EMPLOYEE_SHEET_INVALID}
   * 的 {@link InvalidDataException}（文件无法解析时为 {@code EXCEL_INVALID}）。返回的数据附带整个文件的 SHA-256 摘要。
   */
  @Override
  public DirectoryData.Sheet read(byte[] workbook) {
    List<List<String>> table = XlsxReader.rows(workbook);
    if (table.isEmpty() || !table.getFirst().equals(HEADER))
      throw new InvalidDataException("EMPLOYEE_SHEET_INVALID", "表头须为：" + String.join("、", HEADER));
    if (table.size() == 1) throw new InvalidDataException("EMPLOYEE_SHEET_INVALID", "员工信息表没有数据行");
    List<DirectoryData.Row> rows = new ArrayList<>();
    Set<String> usernames = new HashSet<>();
    for (int i = 1; i < table.size(); i++) {
      List<String> cells = table.get(i);
      int line = i + 1;
      String username = required(cells, 1, 64, line, "用户名");
      if (!username.matches("[A-Za-z0-9_.-]{1,64}"))
        throw new InvalidDataException("EMPLOYEE_SHEET_INVALID", "第 " + line + " 行：用户名只能包含字母、数字和 _.-");
      if (!usernames.add(username))
        throw new InvalidDataException("EMPLOYEE_SHEET_INVALID", "第 " + line + " 行：用户名 " + username + " 重复");
      rows.add(
          new DirectoryData.Row(
              required(cells, 0, 128, line, "姓名"),
              username,
              required(cells, 2, 128, line, "分公司"),
              required(cells, 3, 128, line, "部门"),
              optional(cells, 4, 128, line, "岗位"),
              optional(cells, 5, 32, line, "电话号码")));
    }
    return new DirectoryData.Sheet(rows, sha256(workbook));
  }

  /** 读取必填单元格：规则同 {@code optional}，为空时报“第 N 行：列名不能为空”。 */
  private static String required(List<String> cells, int column, int max, int line, String name) {
    String value = optional(cells, column, max, line, name);
    if (value == null) throw new InvalidDataException("EMPLOYEE_SHEET_INVALID", "第 " + line + " 行：" + name + "不能为空");
    return value;
  }

  /** 读取可空单元格：缺列或空串返回 {@code null}，超过 {@code max} 个字符时报错。 */
  private static String optional(List<String> cells, int column, int max, int line, String name) {
    String value = column < cells.size() ? cells.get(column) : "";
    if (value.length() > max)
      throw new InvalidDataException("EMPLOYEE_SHEET_INVALID", "第 " + line + " 行：" + name + "超过 " + max + " 个字符");
    return value.isEmpty() ? null : value;
  }

  /** 整个文件的 SHA-256 十六进制摘要；同步服务再与同步规则一起算出预览摘要，确认时比对。 */
  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
