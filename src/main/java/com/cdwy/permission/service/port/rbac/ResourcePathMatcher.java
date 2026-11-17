package com.cdwy.permission.service.port.rbac;

/** HTTP 路径模式技术端口。 */
public interface ResourcePathMatcher {
  /** 检查已注册模式语法。 */
  void validate(String pattern);

  /** 匹配真实请求路径。 */
  boolean matches(String pattern, String path);

  /** 比较特异性，越具体越靠前。 */
  int compare(String first, String second);
}
