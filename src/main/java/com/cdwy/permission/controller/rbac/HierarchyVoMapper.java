package com.cdwy.permission.controller.rbac;

import com.cdwy.permission.controller.permission.PermissionVoMapper;
import com.cdwy.permission.service.port.data.HierarchyData;
import com.cdwy.permission.vo.role.PermissionOriginVO;
import com.cdwy.permission.vo.role.PermissionSourceVO;
import com.cdwy.permission.vo.role.RoleInheritanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 角色继承与权限来源的结果 → VO 映射。 */
@Component
@RequiredArgsConstructor
public class HierarchyVoMapper {
  private final PermissionVoMapper permissions;

  /** 一条直接继承关系转为 VO。 */
  public RoleInheritanceVO toVO(HierarchyData.Edge edge) {
    return new RoleInheritanceVO(
        edge.seniorRoleId(), edge.seniorRoleName(), edge.inheritedRoleId(), edge.inheritedRoleName());
  }

  /** 一项有效权限及其全部来源转为 VO。 */
  public PermissionOriginVO toVO(HierarchyData.PermissionOrigin origin) {
    return new PermissionOriginVO(
        permissions.toVO(origin.permission()),
        origin.sources().stream()
            .map(
                source ->
                    new PermissionSourceVO(
                        source.assignedRoleId(),
                        source.assignedRoleName(),
                        source.permissionRoleId(),
                        source.permissionRoleName(),
                        source.inherited(),
                        source.path()))
            .toList());
  }
}
