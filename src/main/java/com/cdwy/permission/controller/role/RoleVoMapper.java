package com.cdwy.permission.controller.role;

import com.cdwy.permission.dto.role.RoleCreateDTO;
import com.cdwy.permission.dto.role.RoleUpdateDTO;
import com.cdwy.permission.service.port.data.RoleData;
import com.cdwy.permission.vo.role.RoleVO;
import org.springframework.stereotype.Component;

/** 角色管理的 DTO → 命令、结果 → VO 映射。 */
@Component
public class RoleVoMapper {
  /** 新建角色请求转为命令。 */
  public RoleData.Create toCommand(RoleCreateDTO dto) {
    return new RoleData.Create(dto.code(), dto.name(), dto.description());
  }

  /** 修改角色请求转为命令。 */
  public RoleData.Update toCommand(RoleUpdateDTO dto) {
    return new RoleData.Update(dto.name(), dto.description());
  }

  /** 角色结果转为 VO。 */
  public RoleVO toVO(RoleData.View view) {
    return new RoleVO(view.id(), view.code(), view.name(), view.description(), view.enabled());
  }
}
