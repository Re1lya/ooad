package com.cdwy.permission.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NFR-11：分层日志不得输出密码与令牌；菜单标识只接受简单字符，防止日志注入。 */
class LayerLoggingTests {
  @Test
  @DisplayName("NFR-11 令牌、登录与密码相关参数一律打码，普通标识照常输出")
  void NFR11_sensitiveArgumentsAreMasked() {
    assertEquals("***", LayerLoggingAspect.arguments("verify", null, new Object[] {"Bearer a.b.c"}));
    assertEquals("***", LayerLoggingAspect.arguments("login", null, new Object[] {"admin", "secret"}));
    assertEquals(
        "***",
        LayerLoggingAspect.arguments("lookup", null, new Object[] {"eyJhbGciOiJI.eyJzdWIiOiIx.sig"}));
    assertEquals("***", LayerLoggingAspect.arguments("list", null, new Object[] {"bearer xyz"}));
    assertEquals(
        "\"cpm\", 3, true", LayerLoggingAspect.arguments("current", null, new Object[] {"cpm", 3L, true}));
    assertEquals("Object", LayerLoggingAspect.arguments("create", null, new Object[] {new Object()}));
  }

  @Test
  @DisplayName("NFR-11 方法名不敏感时，参数名含 password、secret、token、credential 的参数单独打码")
  void NFR11_sensitiveParameterNamesAreMasked() {
    assertEquals(
        "\"admin\", ***",
        LayerLoggingAspect.arguments(
            "initialize", new String[] {"username", "password"}, new Object[] {"admin", "x"}));
    assertEquals(
        "***, 7",
        LayerLoggingAspect.arguments(
            "rotate", new String[] {"clientSecret", "days"}, new Object[] {"s", 7}));
    assertEquals(
        "\"a\"", LayerLoggingAspect.arguments("find", new String[] {}, new Object[] {"a"}));
  }

  @Test
  @DisplayName("NFR-11 参数中的换行等控制字符被替换，不能伪造日志行")
  void NFR11_controlCharactersCannotForgeLogLines() {
    assertEquals(
        "\"a?b?c\"", LayerLoggingAspect.arguments("search", null, new Object[] {"a\nb\rc"}));
  }

  @Test
  @DisplayName("NFR-11 X-Client-Menu 只接受简单菜单标识")
  void NFR11_menuHeaderIsSanitized() {
    assertEquals("users", RequestLoggingFilter.menu("users"));
    assertEquals("-", RequestLoggingFilter.menu(null));
    assertEquals("invalid", RequestLoggingFilter.menu("users\nFAKE LOG LINE"));
  }

  @Test
  @DisplayName("NFR-11 按包名区分 Controller / Service / DDD-Application / Infrastructure 层")
  void NFR11_layersFollowPackages() {
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
