package com.cdwy.permission.service.port.permission;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/**
 * 菜单与按钮（前端权限）用例：维护各应用的权限树和生效状态，以及菜单、按钮与 API 资源的绑定。
 *
 * <p>编码全局唯一，按钮必须隶属同一应用的菜单。改动不写入令牌，鉴权按请求实时计算，已签发令牌的下一次请求即受影响。
 */
public interface PermissionUseCase extends BackendUseCase {
  /** 按标识升序列出菜单与按钮，含已失效的；{@code systemId} 为空时返回全部应用，未知应用得到空列表。 */
  List<PermissionData.View> list(Long systemId);

  /**
   * 新建菜单或按钮，默认生效，返回含新标识的结果。编码已存在抛 {@code PERMISSION_CODE_EXISTS}（409），所属应用不存在抛
   * {@code SYSTEM_NOT_FOUND}、上级不存在抛 {@code PERMISSION_NOT_FOUND}（均 404）；按钮没有上级抛
   * {@code BUTTON_PARENT_REQUIRED}，上级不是同一应用的菜单抛 {@code PERMISSION_PARENT_INVALID}，按钮填了菜单地址抛
   * {@code BUTTON_PATH_NOT_ALLOWED}（均 400）。空白菜单地址存为 null；授予角色后才进入有效权限。不写审计日志。
   */
  PermissionData.View create(PermissionData.Input input);

  /**
   * 修改名称、类型、菜单地址与上级，编码、所属应用和生效状态不变；不存在抛 {@code PERMISSION_NOT_FOUND}（404）。结构
   * 规则同新建，另外：沿新上级链回到自身（含以自身为上级）抛 {@code PERMISSION_CYCLE}（400），有下级的菜单改为按钮抛
   * {@code PERMISSION_HAS_CHILDREN}（409）。已有角色授权与 API 绑定保留，不写审计日志。
   */
  PermissionData.View update(Long id, PermissionData.Update input);

  /**
   * 设置生效状态，重复设置幂等；不存在抛 {@code PERMISSION_NOT_FOUND}（404）。失效后该节点及全部下级不再计入任何人的
   * 有效权限（经其绑定得到的 API 访问随之失去），也不能再授予角色；已有授权、绑定和下级自身的状态都保留，重新生效即恢复。
   * 凭证版本不变，已签发令牌的下一次请求即按新状态鉴权；不写审计日志。返回更新后的节点。
   */
  PermissionData.View status(Long id, boolean active);

  /**
   * 删除菜单或按钮：不存在抛 {@code PERMISSION_NOT_FOUND}（404）；仍有下级、角色授权或 API 绑定时抛
   * {@code PERMISSION_IN_USE}（409），不级联删除；不写审计日志。
   */
  void delete(Long id);

  /**
   * 把 API 资源绑定到菜单或按钮：两端须存在（{@code PERMISSION_NOT_FOUND}、{@code API_RESOURCE_NOT_FOUND}，404）且属于
   * 同一应用（{@code BINDING_SYSTEM_MISMATCH}，400）；不检查两端是否生效，已绑定时幂等。拥有该菜单或按钮的用户下一次
   * 请求即可调用该资源；不写审计日志。
   */
  void bind(Long permissionId, Long resourceId);

  /**
   * 解除绑定，保留 API 资源登记：两端须存在（404，错误码同绑定），未绑定时幂等；已签发令牌的下一次请求即失去经此绑定
   * 得到的访问。不写审计日志。
   */
  void unbind(Long permissionId, Long resourceId);

  /** 菜单或按钮绑定的 API 资源，含已停用的；权限不存在抛 {@code PERMISSION_NOT_FOUND}（404）。 */
  List<ResourceData.View> resources(Long permissionId);

  /**
   * 管理端权限目录树，含已失效节点，每个节点的 {@code granted} 都为 true；{@code systemId} 为空时返回全部应用，未知应用
   * 得到空列表。
   */
  List<PermissionData.Tree> tree(Long systemId);
}
