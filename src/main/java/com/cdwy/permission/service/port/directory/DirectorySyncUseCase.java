package com.cdwy.permission.service.port.directory;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.DirectoryData;

/**
 * 员工信息同步：把员工信息表装入统一身份目录。管理员可以重复执行，结果幂等。
 *
 * <ul>
 *   <li>分公司为根部门、部门为其子部门，按“上级 + 名称”复用已有部门；
 *   <li>按用户名（工号或学号）新建或更新人员：新人员停用且没有可用密码；已有人员只更新姓名、部门、岗位和电话，
 *       保留密码、启用状态、期限和授权；管理员账号不参与同步；
 *   <li>按规则给总公司、其他分公司的员工补授角色，只增不减；不在表中的人员不受影响。
 * </ul>
 */
public interface DirectorySyncUseCase extends BackendUseCase {
  /** 只校验并计算将发生的变化，不写数据库。 */
  DirectoryData.Summary preview(byte[] workbook, DirectoryData.Rule rule);

  /** 核对摘要后在一个事务内写入；任何一行冲突都整批回滚。 */
  DirectoryData.Summary commit(byte[] workbook, DirectoryData.Rule rule, String digest);
}
