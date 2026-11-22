package com.cdwy.permission.service.port.system;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/**
 * 接入应用用例：登记接入统一身份与权限管理的业务应用（含权限系统自身 {@code rbac}），维护名称、访问地址与启停。
 *
 * <p>应用编码是统一身份接口地址 {@code /api/apps/{应用编码}} 中的应用段，也是令牌 {@code aud} 的取值。
 */
public interface SystemUseCase extends BackendUseCase {
  /** 按标识升序列出全部应用，含已停用的和权限系统自身（{@code rbac}）。 */
  List<SystemData.View> list();

  /**
   * 登记新应用，默认启用，返回含新标识的结果；编码全局唯一，已存在抛 {@code SYSTEM_CODE_EXISTS}（409），创建后不可修改。
   * 不写审计日志。
   */
  SystemData.View create(SystemData.Create input);

  /** 修改名称与访问地址，编码不变；不存在抛 {@code SYSTEM_NOT_FOUND}（404），不写审计日志。 */
  SystemData.View update(Long id, SystemData.Update input);

  /**
   * 启用或停用应用，重复设置幂等，已有菜单、按钮、API 资源与授权关系都保留；不存在抛 {@code SYSTEM_NOT_FOUND}（404）。
   * 停用后该应用的菜单、按钮与 API 资源在鉴权和统一身份接口中都实时视为无效，用户在该应用内没有任何权限；凭证版本不变，
   * 仍可登录并通过认证。不写审计日志。
   */
  SystemData.View status(Long id, boolean enabled);

  /**
   * 删除应用：不存在抛 {@code SYSTEM_NOT_FOUND}（404）；仍有菜单、按钮或 API 资源时抛 {@code SYSTEM_IN_USE}（409），
   * 不级联删除；不写审计日志。
   */
  void delete(Long id);
}
