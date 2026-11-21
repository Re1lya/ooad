package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;
import com.cdwy.permission.service.port.error.InvalidDataException;

/** ProtectedResource 领域对象：只维护业务状态和不变量，不依赖 JPA。 */
@Getter
@Setter
public class ProtectedResource {
  private Long id;
  private Long systemId;
  private String name;
  private String code;
  private String operationId;
  private String method;
  private String path;
  private boolean active = true;

  /** 资源创建前的路径格式验证；模式语法由共享路径端口进一步校验。 */
  public void assertPath() {
    if (path == null
        || !path.startsWith("/")
        || path.contains("?")
        || path.contains("#")
        || path.contains("**")) throw new InvalidDataException("API_RESOURCE_PATH_INVALID", "资源路径须为不含查询参数的绝对路径，不支持 ** 通配符");
  }

  /** 后端权限码可选；登记后与方法、路径一样固定，供接入应用做方法级授权。 */
  public void assertCode() {
    if (code != null && !code.matches("[A-Za-z][A-Za-z0-9_:.-]{0,63}"))
      throw new InvalidDataException("API_RESOURCE_CODE_INVALID", "权限码须以字母开头，只含字母、数字和 _:.-，最长 64 位");
  }

  /** operationId 可选；登记后用于“能否调用指定接口”的查询。 */
  public void assertOperationId() {
    if (operationId != null && !operationId.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}"))
      throw new InvalidDataException("API_OPERATION_ID_INVALID", "operationId 须以字母或下划线开头，只含字母、数字和 _.-，最长 128 位");
  }

  /** 启用且登记了权限码的资源才向接入应用提供后端权限码（信息专家：资源自己知道是否有效）。 */
  public java.util.Optional<String> authorityCode() {
    return active && code != null ? java.util.Optional.of(code) : java.util.Optional.empty();
  }

  /** 启用且绑定的前端权限中至少有一项在用户有效权限内时才可调用（信息专家：资源知道自己是否启用）。 */
  public boolean callableBy(
      java.util.Set<Long> grantedPermissions, java.util.Collection<Long> boundPermissions) {
    return active && boundPermissions.stream().anyMatch(grantedPermissions::contains);
  }

  /** 更名不会修改既有 HTTP 资源标识。 */
  public void rename(String value) {
    name = value;
  }

  /**
   * 启停 API 资源；停用后仍优先匹配该资源，不能借更宽泛的授权绕过。控制台的内置管理接口不能停用，否则所有人（包括管理员）都调用不了。
   *
   * @param value 生效为 true
   * @param builtIn 是否为控制台的内置管理接口，由应用服务按内置目录判断
   */
  public void changeStatus(boolean value, boolean builtIn) {
    if (!value) assertNotBuiltIn(builtIn);
    active = value;
  }

  /**
   * 删除前检查：内置管理接口抛 {@code CONSOLE_BUILT_IN}；仍绑定前端权限时抛 {@code API_RESOURCE_IN_USE}（均 409）。
   *
   * @param builtIn 是否为控制台的内置管理接口
   * @param bound 是否仍绑定菜单或按钮
   */
  public void assertDeletable(boolean builtIn, boolean bound) {
    assertNotBuiltIn(builtIn);
    if (bound) throw new com.cdwy.permission.service.port.error.ConflictException("API_RESOURCE_IN_USE", "API 资源仍绑定前端权限");
  }

  private static void assertNotBuiltIn(boolean builtIn) {
    if (builtIn)
      throw new com.cdwy.permission.service.port.error.ConflictException("CONSOLE_BUILT_IN", "权限管理系统的内置管理接口不能停用或删除");
  }
}
