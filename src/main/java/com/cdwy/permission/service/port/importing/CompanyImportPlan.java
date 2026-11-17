package com.cdwy.permission.service.port.importing;

import com.cdwy.permission.service.port.data.CompanySummary;
import java.util.*;

/** 已验证、无副作用的来源批次；原始行只在服务器内使用，不输出员工清单。 */
public record CompanyImportPlan(
    List<DepartmentRow> departments,
    List<EmployeeRow> employees,
    List<FunctionRow> functions,
    List<String> roles,
    String digest,
    int qualifiedDepartments) {
  /** 原始组织名称及经理说明，不按姓名擅自关联账号。 */
  public record DepartmentRow(String name, String manager, String parent) {}

  /** 员工来源属性与已解析的部门名称。 */
  public record EmployeeRow(
      String name,
      String username,
      String company,
      String sourceDepartment,
      String department,
      String job,
      String phone) {}

  /** 每个功能的原始五类权限单元格，不将仅本人扩成全量。 */
  public record FunctionRow(
      String system, String module, String name, String description, List<String> grants) {}

  /** 给界面的无敏感明细摘要。 */
  public CompanySummary summary() {
    return new CompanySummary(
        departments.size(),
        employees.size(),
        functions.stream().map(FunctionRow::system).distinct().count(),
        functions.stream().map(f -> f.system() + "/" + f.module()).distinct().count(),
        functions.size(),
        roles.size(),
        qualifiedDepartments,
        digest,
        List.of(
            "新员工账号停用且无可用初始密码；管理员重置密码后启用",
            "只导入角色模板，不按岗位自动授权；部门经理名称不自动关联同名员工",
            "本人范围保留为独立操作；没有真实API定义，不生成或绑定外部API",
            "权限矩阵有 "
                + functions.stream()
                    .flatMap(f -> f.grants().stream())
                    .filter(String::isBlank)
                    .count()
                + " 个空白单元格，保持未授权，原始空白值保留"));
  }

  /** 仅接受来源中明确支持的操作，不把未知文本默认为全权。 */
  public static Set<String> actions(String cell) {
    if (cell.isBlank() || cell.equals("无") || cell.equals("全部")) return Set.of();
    Set<String> result = new LinkedHashSet<>();
    for (String word : cell.split("/")) {
      String normalized = word.strip().equals("view") ? "查看" : word.strip();
      if (!List.of("查看", "新增", "编辑", "审批", "导出", "查看本人", "编辑本人").contains(normalized))
        throw new IllegalArgumentException("不识别的权限动作，请检查原表");
      result.add(normalized);
    }
    return result;
  }
}
