package com.cdwy.permission.controller.permission;

import com.cdwy.permission.dto.permission.PermissionCreateDTO;
import com.cdwy.permission.dto.permission.PermissionUpdateDTO;
import com.cdwy.permission.service.port.data.PermissionData;
import com.cdwy.permission.vo.permission.PermissionTreeVO;
import com.cdwy.permission.vo.permission.PermissionVO;
import org.springframework.stereotype.Component;

/** 菜单与按钮的 DTO → 命令、结果 → VO 映射。 */
@Component
public class PermissionVoMapper {
  /** 新建菜单或按钮请求转为命令；类型以枚举名传给用例。 */
  public PermissionData.Input toCommand(PermissionCreateDTO dto) {
    return new PermissionData.Input(
        dto.systemId(), dto.code(), dto.name(), dto.type().name(), dto.menuPath(), dto.parentId());
  }

  /** 修改菜单或按钮请求转为命令。 */
  public PermissionData.Update toCommand(PermissionUpdateDTO dto) {
    return new PermissionData.Update(dto.name(), dto.type().name(), dto.menuPath(), dto.parentId());
  }

  /** 菜单或按钮结果转为 VO。 */
  public PermissionVO toVO(PermissionData.View view) {
    return new PermissionVO(
        view.id(),
        view.systemId(),
        view.code(),
        view.name(),
        view.type(),
        view.menuPath(),
        view.parentId(),
        view.active());
  }

  /** 递归转换菜单树。 */
  public PermissionTreeVO toVO(PermissionData.Tree tree) {
    return new PermissionTreeVO(
        toVO(tree.permission()),
        tree.granted(),
        tree.children().stream().map(this::toVO).toList());
  }
}
