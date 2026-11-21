package com.cdwy.permission.service.port.organization;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/** 部门管理用例：维护以上级关系组成的部门树；同一上级下名称唯一，编码全局唯一。部门不参与授权计算。 */
public interface DepartmentUseCase extends BackendUseCase {
  /** 按标识升序返回全部部门的扁平列表（不分页），由调用方按 {@code parentId} 组成树。 */
  List<DepartmentData.View> list();

  /**
   * 新建部门，返回含标识与编码的结果：上级不存在抛 {@code DEPARTMENT_NOT_FOUND}（404）；同一上级下重名抛
   * {@code DEPARTMENT_NAME_EXISTS}，编码已被占用抛 {@code DEPARTMENT_CODE_EXISTS}（均 409）。未填编码时保存后取默认编码
   * （{@code D} 加补零到四位的标识，例如 {@code D0012}）。编码格式由 API 层校验，DDD 实现另在领域对象中校验
   * （{@code DEPARTMENT_CODE_INVALID}，400）。不写审计日志。
   */
  DepartmentData.View create(DepartmentData.Input input);

  /**
   * 修改名称、上级与编码：部门或新上级不存在抛 {@code DEPARTMENT_NOT_FOUND}（404）；新上级是自身或自身的后代抛
   * {@code DEPARTMENT_CYCLE}（400）；重名与编码规则同新建，未填编码时保留原编码。部门内人员不变，不写审计日志。
   */
  DepartmentData.View update(Long id, DepartmentData.Input input);

  /**
   * 删除部门：不存在抛 {@code DEPARTMENT_NOT_FOUND}（404）；仍有直接子部门或直属人员（含停用账号）时抛
   * {@code DEPARTMENT_IN_USE}（409），不级联删除；不写审计日志。
   */
  void delete(Long id);
}
