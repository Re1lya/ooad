package com.cdwy.permission.service.port.data;

import java.util.List;

/** 公司三表预览与提交共用的只读汇总，不包含员工个人明细。 */
public record CompanySummary(
    int departments,
    int employees,
    long systems,
    long modules,
    int functions,
    int roleTemplates,
    int qualifiedDepartments,
    String digest,
    List<String> notes) {
  /** 汇总的提示不可被调用方原地更改。 */
  public CompanySummary {
    notes = List.copyOf(notes);
  }
}
