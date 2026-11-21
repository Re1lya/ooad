package com.cdwy.permission.fixture;

import com.cdwy.permission.config.BusinessAccess;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试桩：一个登记在应用 course 下、按 RBAC1 授权的业务接口，用来验证“角色 → 按钮 → API 资源”整条授权链。
 *
 * <p>只在测试类路径中存在；生产代码不再提供演示接口，真实业务接口由接入的课程管理系统提供。
 */
@RestController
@RequestMapping("/api/demo/courses")
@BusinessAccess("course")
public class ProtectedBusinessEndpoint {
  @GetMapping
  public List<Map<String, Object>> list() {
    return List.of(Map.of("id", 1, "name", "面向对象分析与设计"));
  }
}
