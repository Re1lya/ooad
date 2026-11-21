package com.cdwy.permission.config;

import java.lang.annotation.*;

/**
 * 按 RBAC1 有效授权访问接口：value 是登记该接口的应用编码；请求的方法与路径须匹配该应用中某个启用的 API 资源，且该资源绑定的菜单或按钮在用户的有效权限中。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessAccess {
  /** 权限系统自身（管理控制台）的应用编码；控制台令牌的 aud 也取这个值。 */
  String CONSOLE = "rbac";

  /** 登记该接口的应用编码；权限系统自身的管理接口为 {@link #CONSOLE}。 */
  String value();
}
