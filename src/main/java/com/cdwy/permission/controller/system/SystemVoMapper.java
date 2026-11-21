package com.cdwy.permission.controller.system;

import com.cdwy.permission.dto.system.SystemCreateDTO;
import com.cdwy.permission.dto.system.SystemUpdateDTO;
import com.cdwy.permission.service.port.data.SystemData;
import com.cdwy.permission.vo.system.SystemVO;
import org.springframework.stereotype.Component;

/** 应用管理的 DTO → 命令、结果 → VO 映射。 */
@Component
public class SystemVoMapper {
  /** 接入应用请求转为命令。 */
  public SystemData.Create toCommand(SystemCreateDTO dto) {
    return new SystemData.Create(dto.code(), dto.name(), dto.baseUrl());
  }

  /** 修改应用请求转为命令。 */
  public SystemData.Update toCommand(SystemUpdateDTO dto) {
    return new SystemData.Update(dto.name(), dto.baseUrl());
  }

  /** 应用结果转为 VO。 */
  public SystemVO toVO(SystemData.View view) {
    return new SystemVO(view.id(), view.code(), view.name(), view.baseUrl(), view.enabled());
  }
}
