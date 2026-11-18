package com.cdwy.permission.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NFR-11：分层日志不得输出密码与令牌；菜单标识只接受简单字符，防止日志注入。 */
class LayerLoggingTests {
  @Test
  @DisplayName("NFR-11 令牌、登录与密码相关参数一律打码，普通标识照常输出")
  void sensitiveArgumentsAreMasked() {
    assertEquals("***", LayerLoggingAspect.arguments("verify", new Object[] {"Bearer a.b.c"}));
    assertEquals("***", LayerLoggingAspect.arguments("login", new Object[] {"admin", "secret"}));
    assertEquals(
        "***",
        LayerLoggingAspect.arguments("lookup", new Object[] {"eyJhbGciOiJI.eyJzdWIiOiIx.sig"}));
    assertEquals("***", LayerLoggingAspect.arguments("list", new Object[] {"bearer xyz"}));
    assertEquals(
        "\"cpm\", 3, true", LayerLoggingAspect.arguments("current", new Object[] {"cpm", 3L, true}));
    assertEquals("Object", LayerLoggingAspect.arguments("create", new Object[] {new Object()}));
  }

  @Test
  @DisplayName("NFR-11 X-Client-Menu 只接受简单菜单标识")
  void menuHeaderIsSanitized() {
    assertEquals("users", RequestLoggingFilter.menu("users"));
    assertEquals("-", RequestLoggingFilter.menu(null));
    assertEquals("invalid", RequestLoggingFilter.menu("users\nFAKE LOG LINE"));
  }

  @Test
  @DisplayName("NFR-11 按包名区分 Controller / Service / DDD-Application / Infrastructure 层")
  void layersFollowPackages() {
    assertEquals(
        "Controller",
        LayerLoggingAspect.layerOf(com.cdwy.permission.controller.role.RoleController.class));
    assertEquals(
        "Service", LayerLoggingAspect.layerOf(com.cdwy.permission.service.role.RoleService.class));
    assertEquals(
        "DDD-Application",
        LayerLoggingAspect.layerOf(
            com.cdwy.permission.service.ddd.application.role.DddRoleService.class));
    assertEquals(
        "Infrastructure",
        LayerLoggingAspect.layerOf(
            com.cdwy.permission.infrastructure.persistence.JpaRoleCatalogAdapter.class));
  }
}
