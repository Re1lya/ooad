package com.cdwy.permission.service.ddd.domain.model;

import lombok.Getter;
import lombok.Setter;

/** ProtectedResource 领域对象：只维护业务状态和不变量，不依赖 JPA。 */
@Getter
@Setter
public class ProtectedResource {
  private Long id;
  private Long systemId;
  private String name;
  private String method;
  private String path;
  private boolean active = true;

  /** 资源创建前的路径格式验证；模式语法由共享路径端口进一步校验。 */
  public void assertPath() {
    if (path == null
        || !path.startsWith("/")
        || path.contains("?")
        || path.contains("#")
        || path.contains("**")) throw new IllegalArgumentException("资源路径须为不含查询参数的绝对路径，不支持 ** 通配符");
  }

  /** 更名不会修改既有 HTTP 资源标识。 */
  public void rename(String value) {
    name = value;
  }

  /** 停用仍优先匹配该资源，不能借更宽泛的授权绕过。 */
  public void changeStatus(boolean value) {
    active = value;
  }

  /** 前端绑定解除后才可删除。 */
  public void assertDeletable(boolean bound) {
    if (bound) throw new com.cdwy.permission.service.port.error.ConflictException("API 资源仍绑定前端权限");
  }

  /** CloneFactory 的布尔 get 访问器约定。 */
  public boolean getActive() {
    return active;
  }
}
