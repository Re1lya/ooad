package com.cdwy.permission.controller.directory;

import com.cdwy.permission.service.port.data.DirectoryData;
import com.cdwy.permission.vo.directory.SyncSummaryVO;
import org.springframework.stereotype.Component;

/** 员工信息同步的结果 → VO 映射。 */
@Component
public class DirectoryVoMapper {
  /** 同步规则参数转为命令；角色编码留空表示不补授。 */
  public DirectoryData.Rule toCommand(String headOffice, String headOfficeRole, String branchRole) {
    return new DirectoryData.Rule(headOffice, headOfficeRole, branchRole);
  }

  /** 预览或确认汇总转为 VO。 */
  public SyncSummaryVO toVO(DirectoryData.Summary summary) {
    return new SyncSummaryVO(
        summary.rows(),
        summary.companies(),
        summary.departmentsCreated(),
        summary.departmentsKept(),
        summary.usersCreated(),
        summary.usersUpdated(),
        summary.usersUnchanged(),
        summary.roleGrants(),
        summary.digest(),
        summary.applied());
  }
}
