package com.cdwy.permission.vo.directory;

/**
 * 员工信息同步的预览或确认汇总，不含员工个人明细；确认时须回传 digest。
 *
 * @param rows 数据行数
 * @param companies 公司数
 * @param departmentsCreated 新建部门数
 * @param departmentsKept 沿用的部门数
 * @param usersCreated 新建人员数
 * @param usersUpdated 更新人员数
 * @param usersUnchanged 未变人员数
 * @param roleGrants 补授角色条数
 * @param digest 文件与规则的摘要
 * @param applied 是否已写入
 */
public record SyncSummaryVO(
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
