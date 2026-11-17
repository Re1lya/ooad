package com.cdwy.permission.service.port.importing;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;

/** CompanyImportUseCase：两种业务实现共用的用例边界。 */
public interface CompanyImportUseCase extends BackendUseCase {
  /** 执行已校验输入对应的业务用例。 */
  CompanySummary commit(CompanyImportPlan plan, String digest);
}
