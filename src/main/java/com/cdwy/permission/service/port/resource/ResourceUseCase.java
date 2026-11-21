package com.cdwy.permission.service.port.resource;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;
import java.util.List;

/**
 * API 资源用例：登记各应用的后端接口（HTTP 方法 + 路径模式），供接口鉴权匹配，经菜单或按钮的绑定授予用户。
 *
 * <p>方法、路径、权限码与 operationId 登记后不可修改，只能改名称或启停。
 */
public interface ResourceUseCase extends BackendUseCase {
  /** 按标识升序列出所有应用的 API 资源，含已停用的。 */
  List<ResourceData.View> list();

  /**
   * 登记 API 资源，默认生效，返回含新标识的结果。路径须能按 Spring {@code PathPattern} 解析，否则抛 {@code BAD_REQUEST}
   * （400）；同一应用内“方法 + 路径”已登记抛 {@code API_RESOURCE_EXISTS}，operationId 已登记抛
   * {@code API_OPERATION_EXISTS}（均 409）；应用不存在抛 {@code SYSTEM_NOT_FOUND}（404）。路径、权限码与 operationId 的
   * 格式由 API 层校验，DDD 实现另在领域对象中校验，分别抛 {@code API_RESOURCE_PATH_INVALID}、
   * {@code API_RESOURCE_CODE_INVALID}、{@code API_OPERATION_ID_INVALID}（400）。不写审计日志。
   */
  ResourceData.View create(ResourceData.Create input);

  /** 只改显示名称，不影响鉴权与绑定；不存在抛 {@code API_RESOURCE_NOT_FOUND}（404），不写审计日志。 */
  ResourceData.View update(Long id, ResourceData.Update input);

  /**
   * 设置生效状态，重复设置幂等，绑定关系保留；不存在抛 {@code API_RESOURCE_NOT_FOUND}（404）。停用后接口鉴权仍优先匹配到
   * 它并直接拒绝，不会退回到更宽泛的资源；接入应用查询时它既不提供权限码，也判为不可调用。下一次请求即生效，不写审计日志。
   */
  ResourceData.View status(Long id, boolean active);

  /**
   * 删除 API 资源：不存在抛 {@code API_RESOURCE_NOT_FOUND}（404）；仍绑定任何菜单或按钮时抛 {@code API_RESOURCE_IN_USE}
   * （409），须先解绑。删除后同一请求改由其余匹配的资源决定，没有匹配时拒绝访问；不写审计日志。
   */
  void delete(Long id);
}
