package com.cdwy.permission.dto.directory;

/** 员工信息同步的 HTTP 响应。 */
public final class DirectoryDtos {
  private DirectoryDtos() {}

  /** 预览与确认共用的汇总，不含员工个人明细；确认时须回传 digest。 */
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
}
