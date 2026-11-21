package com.cdwy.permission.controller.organization;

import com.cdwy.permission.dto.organization.DepartmentDTO;
import com.cdwy.permission.service.port.data.DepartmentData;
import com.cdwy.permission.vo.organization.DepartmentVO;
import org.springframework.stereotype.Component;

/** 部门管理的 DTO → 命令、结果 → VO 映射。 */
@Component
public class DepartmentVoMapper {
  /** 新建或修改部门请求转为命令；编码留空表示按编号生成。 */
  public DepartmentData.Input toCommand(DepartmentDTO dto) {
    return new DepartmentData.Input(dto.name(), dto.parentId(), dto.code());
  }

  /** 部门结果转为 VO。 */
  public DepartmentVO toVO(DepartmentData.View view) {
    return new DepartmentVO(view.id(), view.name(), view.parentId(), view.code());
  }
}
