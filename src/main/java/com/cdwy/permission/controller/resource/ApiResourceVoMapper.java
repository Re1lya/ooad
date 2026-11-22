package com.cdwy.permission.controller.resource;

import com.cdwy.permission.dto.resource.ApiResourceCreateDTO;
import com.cdwy.permission.dto.resource.ApiResourceUpdateDTO;
import com.cdwy.permission.service.port.data.ResourceData;
import com.cdwy.permission.vo.resource.ApiResourceVO;
import org.springframework.stereotype.Component;

/** API 资源的 DTO → 命令、结果 → VO 映射。 */
@Component
public class ApiResourceVoMapper {
  /** 登记 API 资源请求转为命令；权限码与 operationId 留空视为不设置。 */
  public ResourceData.Create toCommand(ApiResourceCreateDTO dto) {
    return new ResourceData.Create(
        dto.systemId(),
        dto.name(),
        dto.method(),
        dto.path(),
        blankToNull(dto.code()),
        blankToNull(dto.operationId()));
  }

  /** 修改 API 资源名称请求转为命令。 */
  public ResourceData.Update toCommand(ApiResourceUpdateDTO dto) {
    return new ResourceData.Update(dto.name());
  }

  /** API 资源结果转为 VO。 */
  public ApiResourceVO toVO(ResourceData.View view) {
    return new ApiResourceVO(
        view.id(),
        view.systemId(),
        view.name(),
        view.method(),
        view.path(),
        view.active(),
        view.code(),
        view.operationId());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
