package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;

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
      throw new com.cdwy.permission.service.port.error.ConflictException("仍有授权或资源引用，请先解除关联");
  }

  /** CloneFactory 的布尔 get 访问器约定。 */
  public boolean getEnabled() {
    return enabled;
  }
}
