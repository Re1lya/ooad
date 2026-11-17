package com.cdwy.permission.service.importing;

import com.cdwy.permission.service.port.importing.CompanyImportPlan;
import com.cdwy.permission.service.port.importing.CompanyImportPlan.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 文件格式技术边界：Excel 字节经完整校验后转换为无技术依赖的业务批次。 */
public final class CompanyWorkbookParser {
  private CompanyWorkbookParser() {}

  /** 由三份文件产生可确认摘要；文件变化必须重新预览。 */
  public static CompanyImportPlan parse(byte[] employees, byte[] departments, byte[] permissions) {
    List<List<String>> d = XlsxReader.rows(departments),
        u = XlsxReader.rows(employees),
        p = XlsxReader.rows(permissions);
    header(d, 0, List.of("部门名称", "部门经理", "上级部门"));
    header(u, 0, List.of("姓名", "用户名", "分公司", "部门", "岗位", "电话号码"));
    header(p, 1, List.of("应用系统", "功能模块", "功能点", "功能说明", "系统管理员", "公司领导", "部门经理", "项目经理", "普通员工"));
    List<DepartmentRow> ds = new ArrayList<>();
    Map<String, String> parents = new LinkedHashMap<>();
    for (List<String> row : d.subList(1, d.size())) {
      String name = required(row, 0, 128);
      if (parents.putIfAbsent(name, cell(row, 2)) != null)
        throw new IllegalArgumentException("部门名称重复");
      ds.add(new DepartmentRow(name, bounded(cell(row, 1), 128), bounded(cell(row, 2), 128)));
    }
    for (DepartmentRow row : ds) {
      Set<String> seen = new HashSet<>();
      String next = row.name();
      while (!next.isEmpty()) {
        if (!seen.add(next)) throw new IllegalArgumentException("Excel部门层级存在环");
        if (!parents.containsKey(next)) throw new IllegalArgumentException("Excel存在未定义上级部门");
        next = parents.get(next);
      }
    }
    List<EmployeeRow> us = new ArrayList<>();
    Set<String> accounts = new HashSet<>();
    int qualified = 0;
    for (List<String> row : u.subList(1, u.size())) {
      String username = required(row, 1, 64);
      if (!username.matches("[A-Za-z0-9_.-]{1,64}") || !accounts.add(username))
        throw new IllegalArgumentException("员工用户名重复或格式不合法");
      String company = required(row, 2, 128), source = required(row, 3, 128);
      String name = parents.containsKey(company + "-" + source) ? company + "-" + source : source;
      if (!parents.containsKey(name)) throw new IllegalArgumentException("员工部门无法匹配，请检查所属分公司和部门");
      if (!name.equals(source)) qualified++;
      String ancestor = name;
      Set<String> seen = new HashSet<>();
      while (!ancestor.isEmpty() && !ancestor.equals(company) && seen.add(ancestor))
        ancestor = parents.getOrDefault(ancestor, "");
      if (!ancestor.equals(company)) throw new IllegalArgumentException("员工部门不属于其分公司");
      us.add(
          new EmployeeRow(
              required(row, 0, 128),
              username,
              company,
              source,
              name,
              bounded(cell(row, 4), 128),
              bounded(cell(row, 5), 32)));
    }
    List<FunctionRow> fs = new ArrayList<>();
    String system = "", module = "";
    Set<String> seen = new HashSet<>();
    for (List<String> row : p.subList(2, p.size())) {
      if (!cell(row, 0).isEmpty()) {
        system = required(row, 0, 128);
        module = "";
      }
      if (!cell(row, 1).isEmpty()) module = required(row, 1, 128);
      if (system.isEmpty() || module.isEmpty()) throw new IllegalArgumentException("功能缺少所属应用或模块");
      String name = required(row, 2, 128);
      if (!seen.add(system + "\u0000" + module + "\u0000" + name))
        throw new IllegalArgumentException("同一模块内功能名称重复");
      List<String> grants = new ArrayList<>();
      for (int i = 4; i < 9; i++) {
        String grant = bounded(cell(row, i), 128);
        CompanyImportPlan.actions(grant);
        grants.add(grant);
      }
      fs.add(
          new FunctionRow(system, module, name, bounded(cell(row, 3), 2000), List.copyOf(grants)));
    }
    if (us.isEmpty() || ds.isEmpty() || fs.isEmpty())
      throw new IllegalArgumentException("三张表均须包含数据行");
    try {
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      for (byte[] file : List.of(employees, departments, permissions)) {
        hash.update(Integer.toString(file.length).getBytes(StandardCharsets.UTF_8));
        hash.update((byte) 0);
        hash.update(file);
      }
      return new CompanyImportPlan(
          List.copyOf(ds),
          List.copyOf(us),
          List.copyOf(fs),
          List.copyOf(p.get(1).subList(4, 9)),
          HexFormat.of().formatHex(hash.digest()),
          qualified);
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** 确认三类文件顺序和原始列名。 */
  private static void header(List<List<String>> rows, int row, List<String> expected) {
    List<String> actual = rows.size() <= row ? List.of() : new ArrayList<>(rows.get(row));
    if (!actual.isEmpty() && actual.getFirst().equals("软件系统") && expected.getFirst().equals("应用系统"))
      actual.set(0, "应用系统");
    if (!actual.equals(expected)) throw new IllegalArgumentException("Excel表头不匹配，请按员工、部门、功能权限选择原表");
  }

  /** 读取空单元格，不移动列。 */
  private static String cell(List<String> row, int i) {
    return row.size() > i ? row.get(i) : "";
  }

  /** 必填与长度验证。 */
  private static String required(List<String> row, int i, int max) {
    String value = cell(row, i);
    if (value.isBlank()) throw new IllegalArgumentException("Excel存在必填字段为空");
    return bounded(value, max);
  }

  /** 字段长度在写入前统一检查。 */
  private static String bounded(String value, int max) {
    if (value.length() > max) throw new IllegalArgumentException("Excel字段过长");
    return value;
  }
}
