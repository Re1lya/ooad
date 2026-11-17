package com.cdwy.permission.service.ddd.domain.model;

import com.cdwy.permission.service.port.error.ConflictException;
import com.cdwy.permission.service.port.importing.CompanyImportPlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 确认后的来源批次：摘要、稳定身份与最小授权语义属于业务规则。 */
public record CompanyImportBatch(CompanyImportPlan plan) {
  /** 防止预览后替换文件。 */
  public void assertConfirmed(String digest) {
    if (!plan.digest().equals(digest)) throw new IllegalArgumentException("文件与预览摘要不一致，请重新预览");
  }

  /** 稳定来源标识不依赖工作表位置。 */
  public String code(String type, String value) {
    try {
      return "company_"
          + type
          + "_"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(value.getBytes(StandardCharsets.UTF_8)))
              .substring(0, 20);
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** 本人操作保持独立编码。 */
  public String actionCode(String action) {
    return switch (action) {
      case "查看" -> "view";
      case "编辑" -> "edit";
      case "新增" -> "create";
      case "审批" -> "approve";
      case "导出" -> "export";
      case "查看本人" -> "view_self";
      case "编辑本人" -> "edit_self";
      default -> throw new IllegalArgumentException("未知操作");
    };
  }

  /** 全部仅展开本功能已声明动作，不预授未来能力。 */
  public boolean grants(
      CompanyImportPlan.FunctionRow row, int index, String action, java.util.Set<String> desired) {
    return desired.contains(action)
        && (row.grants().get(index).equals("全部")
            || CompanyImportPlan.actions(row.grants().get(index)).contains(action));
  }

  /** 已有来源节点的系统和类型不得被静默替换。 */
  public void assertCompatible(FrontendPermission node, Long system, String type) {
    if (!node.getSystemId().equals(system) || !node.getKind().equals(type))
      throw new ConflictException("来源权限与已有节点定义冲突");
  }
}
