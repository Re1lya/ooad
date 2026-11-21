package com.cdwy.permission.service.port.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** 员工信息同步的用例数据：与 HTTP 表现、Excel 格式及数据库技术无关。 */
public final class DirectoryData {
  private DirectoryData() {}

  /**
   * 员工信息表的一行。
   *
   * @param name 姓名
   * @param username 用户名，即工号或学号，同时是统一身份用户标识
   * @param company 分公司（总公司也按分公司看待），作为根部门
   * @param department 部门，作为分公司的子部门
   * @param job 岗位，可空
   * @param phone 电话号码，可空；只保存，不经统一身份接口对外提供
   */
  public record Row(
      String name, String username, String company, String department, String job, String phone) {}

  /** 已校验的员工信息表：数据行与文件内容摘要。 */
  public record Sheet(List<Row> rows, String fileDigest) {
    public Sheet {
      rows = List.copyOf(rows);
    }
  }

  /**
   * 同步规则：哪个分公司是总公司，以及总公司员工、其他分公司员工各自补授的角色编码（为空表示不授予角色）。
   *
   * <p>课程管理系统的约定是“总公司员工是教师，其余分公司员工是学生”，即 headOfficeRole=teacher、branchRole=student。
   */
  public record Rule(String headOffice, String headOfficeRole, String branchRole) {
    public Rule {
      if (headOffice == null || headOffice.isBlank())
        throw new IllegalArgumentException("请填写总公司名称");
      if (headOffice.length() > 128) throw new IllegalArgumentException("总公司名称最长 128 个字符");
      headOffice = headOffice.strip();
      headOfficeRole = blankToNull(headOfficeRole);
      branchRole = blankToNull(branchRole);
    }

    /** 某个分公司的员工应补授的角色编码；不授予时为空。 */
    public String roleFor(String company) {
      return headOffice.equals(company) ? headOfficeRole : branchRole;
    }

    private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.strip();
    }
  }

  /**
   * 预览与确认共用的汇总，不含员工个人明细。
   *
   * @param rows 员工表数据行数
   * @param companies 分公司数
   * @param departmentsCreated 新建的部门（含分公司根部门）
   * @param departmentsKept 已存在而复用的部门
   * @param usersCreated 新建的人员（默认停用、没有可用密码）
   * @param usersUpdated 姓名、部门、岗位或电话有变化而更新的人员
   * @param usersUnchanged 没有变化的人员
   * @param roleGrants 补授的角色分配
   * @param digest 文件与规则的摘要，确认时必须回传
   * @param applied 是否已写入
   */
  public record Summary(
      int rows,
      int companies,
      int departmentsCreated,
      int departmentsKept,
      int usersCreated,
      int usersUpdated,
      int usersUnchanged,
      int roleGrants,
      String digest,
      boolean applied) {}

  /** 摘要覆盖文件内容与同步规则：预览后换了文件或改了规则都必须重新预览。 */
  public static String digest(Sheet sheet, Rule rule) {
    try {
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      for (String part :
          new String[] {
            sheet.fileDigest(),
            rule.headOffice(),
            String.valueOf(rule.headOfficeRole()),
            String.valueOf(rule.branchRole())
          }) {
        hash.update(part.getBytes(StandardCharsets.UTF_8));
        hash.update((byte) 0);
      }
      return HexFormat.of().formatHex(hash.digest());
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** 确认时核对摘要。 */
  public static void requireConfirmed(String expected, String given) {
    if (!expected.equals(given))
      throw new IllegalArgumentException("员工信息表或同步规则与预览时不一致，请重新预览");
  }
}
