package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;
import com.cdwy.permission.service.port.error.InvalidDataException;

/** ManagedSystem 领域对象：只维护业务状态和不变量，不依赖 JPA。 */
@Getter
@Setter
public class ManagedSystem {
  private Long id;
  private String code;
  private String name;
  private String baseUrl;
  private boolean enabled = true;

  /** 修改描述信息，编码作为稳定业务标识保持不变。 */
  public void rename(String value, String url) {
    name = value;
    baseUrl = url;
  }

  /** 当前系统开关参与所有资源的有效性判断。 */
  public void changeStatus(boolean value) {
    enabled = value;
  }

  /** 防止删除仍有资源的应用。 */
  public void assertDeletable(boolean referenced) {
    if (referenced)
      throw new com.cdwy.permission.service.port.error.ConflictException("SYSTEM_IN_USE", "仍有授权或资源引用，请先解除关联");
  }

  /**
   * 令牌目标系统：请求声明的 systems 必须包含被调用的应用，且每一项都是已登记的应用；去重并保持顺序。
   *
   * @param requested 登录请求中的 systems
   * @param target 被调用的应用编码
   * @param known 已登记的全部应用
   */
  public static java.util.List<String> audience(
      java.util.List<String> requested,
      String target,
      java.util.Collection<ManagedSystem> known) {
    if (!requested.contains(target))
      throw new InvalidDataException("LOGIN_TARGET_NOT_IN_SYSTEMS", "systems 必须包含当前应用 " + target);
    java.util.Set<String> codes = new java.util.HashSet<>();
    known.forEach(app -> codes.add(app.getCode()));
    java.util.List<String> distinct = requested.stream().distinct().toList();
    for (String code : distinct)
      if (!codes.contains(code)) throw new InvalidDataException("LOGIN_SYSTEM_UNKNOWN", "未登记的目标系统：" + code);
    return distinct;
  }
}
