package com.cdwy.permission.fixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 测试桩：没有标注任何访问规则的接口，用来验证“未登记访问规则的接口一律拒绝”。只在测试类路径中存在。 */
@RestController
public class UnregisteredEndpoint {
  @GetMapping("/api/test/unregistered")
  public String value() {
    return "不应被访问";
  }
}
