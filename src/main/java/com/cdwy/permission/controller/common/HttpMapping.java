package com.cdwy.permission.controller.common;

import com.cdwy.permission.dto.auth.AuthDtos;
import com.cdwy.permission.dto.organization.DepartmentDtos;
import com.cdwy.permission.dto.permission.PermissionDtos;
import com.cdwy.permission.dto.resource.ResourceDtos;
import com.cdwy.permission.dto.role.*;
import com.cdwy.permission.dto.system.SystemDtos;
import com.cdwy.permission.dto.user.*;
import com.cdwy.permission.service.port.data.*;

/** 控制器使用的显式边界映射，同型可变对象由 CloneFactory 生成。 */
public final class HttpMapping {
  private HttpMapping() {}

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static DepartmentData.Input command(DepartmentDtos.Input v) {
    return new DepartmentData.Input(v.name(), v.parentId(), v.code());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static DepartmentDtos.View view(DepartmentData.View v) {
    return new DepartmentDtos.View(v.id(), v.name(), v.parentId(), v.managerName(), v.code());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static SystemData.Create command(SystemDtos.Create v) {
    return new SystemData.Create(v.code(), v.name(), v.baseUrl());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static SystemData.Update command(SystemDtos.Update v) {
    return new SystemData.Update(v.name(), v.baseUrl());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static SystemDtos.View view(SystemData.View v) {
    return new SystemDtos.View(v.id(), v.code(), v.name(), v.baseUrl(), v.enabled());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static RoleData.Create command(RoleDtos.Create v) {
    return new RoleData.Create(v.code(), v.name(), v.description());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static RoleData.Update command(RoleDtos.Update v) {
    return new RoleData.Update(v.name(), v.description());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static RoleDtos.View view(RoleData.View v) {
    return new RoleDtos.View(v.id(), v.code(), v.name(), v.description(), v.enabled());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static ResourceData.Create command(ResourceDtos.Create v) {
    return new ResourceData.Create(
        v.systemId(),
        v.name(),
        v.method(),
        v.path(),
        blankToNull(v.code()),
        blankToNull(v.operationId()));
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static ResourceData.Update command(ResourceDtos.Update v) {
    return new ResourceData.Update(v.name());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static ResourceDtos.View view(ResourceData.View v) {
    return new ResourceDtos.View(
        v.id(), v.systemId(), v.name(), v.method(), v.path(), v.active(), v.code(), v.operationId());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static PermissionData.Input command(PermissionDtos.Input v) {
    return new PermissionData.Input(
        v.systemId(), v.code(), v.name(), v.type().name(), v.menuPath(), v.parentId());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static PermissionData.Update command(PermissionDtos.Update v) {
    return new PermissionData.Update(v.name(), v.type().name(), v.menuPath(), v.parentId());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static PermissionDtos.View view(PermissionData.View v) {
    return new PermissionDtos.View(
        v.id(),
        v.systemId(),
        v.code(),
        v.name(),
        v.type(),
        v.menuPath(),
        v.parentId(),
        v.active(),
        v.sourceDescription(),
        v.sourceGrants());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static RoleHierarchyDtos.Edge view(HierarchyData.Edge v) {
    return new RoleHierarchyDtos.Edge(
        v.seniorRoleId(), v.seniorRoleName(), v.inheritedRoleId(), v.inheritedRoleName());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static RoleHierarchyDtos.Origin view(HierarchyData.Origin v) {
    return new RoleHierarchyDtos.Origin(
        v.assignedRoleId(),
        v.assignedRoleName(),
        v.permissionRoleId(),
        v.permissionRoleName(),
        v.inherited(),
        v.path());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static RoleHierarchyDtos.PermissionOrigin view(HierarchyData.PermissionOrigin v) {
    return new RoleHierarchyDtos.PermissionOrigin(
        view(v.permission()), v.sources().stream().map(HttpMapping::view).toList());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static AuthData.Login command(AuthDtos.Login v) {
    return new AuthData.Login(v.username(), v.password());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static AuthData.PasswordChange command(AuthDtos.PasswordChange v) {
    return new AuthData.PasswordChange(v.currentPassword(), v.newPassword());
  }

  /** HTTP 与用例数据显式转换，record 不依赖字段写入。 */
  public static AuthDtos.Token view(AuthData.Token v) {
    return new AuthDtos.Token(v.accessToken(), v.tokenType(), v.expiresIn(), view(v.user()));
  }

  /** 创建命令只复制同型字段，人员类型显式转换。 */
  public static CreateUserCommand command(CreateUserRequest v) {
    CreateUserCommand result =
        cn.edu.xmu.clonefactory.util.CloneFactory.copy(new CreateUserCommand(), v);
    result.setKind(v.getPersonType().name());
    return result;
  }

  /** 身份修改命令。 */
  public static UserData.Update command(UserDtos.Update v) {
    return new UserData.Update(
        v.displayName(), v.departmentId(), v.personType().name(), v.expiresOn());
  }

  /** 账号结果不包含密码散列及版本号。 */
  public static UserView view(UserResult v) {
    UserView result = cn.edu.xmu.clonefactory.util.CloneFactory.copy(new UserView(), v);
    result.setPersonType(com.cdwy.permission.service.port.data.PersonType.valueOf(v.getKind()));
    return result;
  }

  /** 保留既有分页 JSON 契约。 */
  public static com.cdwy.permission.dto.common.CommonDtos.PageResult<UserView> view(
      UserData.Page<UserResult> v) {
    return new com.cdwy.permission.dto.common.CommonDtos.PageResult<>(
        v.items().stream().map(HttpMapping::view).toList(), v.total(), v.page(), v.size());
  }

  /** 递归映射导航树。 */
  public static PermissionDtos.Tree view(PermissionData.Tree v) {
    return new PermissionDtos.Tree(
        view(v.permission()), v.granted(), v.children().stream().map(HttpMapping::view).toList());
  }

  /** 导入汇总不返回真实员工明细。 */
  public static com.cdwy.permission.vo.importing.CompanyImportSummaryVO view(CompanySummary v) {
    return new com.cdwy.permission.vo.importing.CompanyImportSummaryVO(
        v.departments(),
        v.employees(),
        v.systems(),
        v.modules(),
        v.functions(),
        v.roleTemplates(),
        v.qualifiedDepartments(),
        v.digest(),
        v.notes());
  }

  /** 可选文本字段：空白视为未填写。 */
  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
