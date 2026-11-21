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

/**
 * 员工信息表（.xlsx）读取：表头必须是“姓名、用户名、分公司、部门、岗位、电话号码”，与课程管理系统提供的《市政公司员工信息表》一致。
 *
 * <p>逐行校验必填、长度、用户名格式与表内重复；错误信息带 Excel 行号。文件只在请求内存中处理。
 */
@Component
public class XlsxEmployeeSheetReader implements EmployeeSheetReader {
  static final List<String> HEADER = List.of("姓名", "用户名", "分公司", "部门", "岗位", "电话号码");

  @Override
  public DirectoryData.Sheet read(byte[] workbook) {
    List<List<String>> table = XlsxReader.rows(workbook);
    if (table.isEmpty() || !table.getFirst().equals(HEADER))
      throw new IllegalArgumentException("表头须为：" + String.join("、", HEADER));
    if (table.size() == 1) throw new IllegalArgumentException("员工信息表没有数据行");
    List<DirectoryData.Row> rows = new ArrayList<>();
    Set<String> usernames = new HashSet<>();
    for (int i = 1; i < table.size(); i++) {
      List<String> cells = table.get(i);
      int line = i + 1;
      String username = required(cells, 1, 64, line, "用户名");
      if (!username.matches("[A-Za-z0-9_.-]{1,64}"))
        throw new IllegalArgumentException("第 " + line + " 行：用户名只能包含字母、数字和 _.-");
      if (!usernames.add(username))
        throw new IllegalArgumentException("第 " + line + " 行：用户名 " + username + " 重复");
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

  private static String required(List<String> cells, int column, int max, int line, String name) {
    String value = optional(cells, column, max, line, name);
    if (value == null) throw new IllegalArgumentException("第 " + line + " 行：" + name + "不能为空");
    return value;
  }

  private static String optional(List<String> cells, int column, int max, int line, String name) {
    String value = column < cells.size() ? cells.get(column) : "";
    if (value.length() > max)
      throw new IllegalArgumentException("第 " + line + " 行：" + name + "超过 " + max + " 个字符");
    return value.isEmpty() ? null : value;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
