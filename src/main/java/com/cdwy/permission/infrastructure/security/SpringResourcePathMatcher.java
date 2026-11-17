package com.cdwy.permission.infrastructure.security;

import com.cdwy.permission.service.port.rbac.ResourcePathMatcher;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.*;

/** 隔离 Spring 路径语法与特异性比较。 */
@Component
public class SpringResourcePathMatcher implements ResourcePathMatcher {
  /** 语法错误按统一400返回。 */
  public void validate(String pattern) {
    PathPatternParser.defaultInstance.parse(pattern);
  }

  /** 使用框架的路径解析而非字符串前缀。 */
  public boolean matches(String pattern, String path) {
    return PathPatternParser.defaultInstance.parse(pattern).matches(PathContainer.parsePath(path));
  }

  /** 越具体的路径排在前面。 */
  public int compare(String a, String b) {
    return PathPattern.SPECIFICITY_COMPARATOR.compare(
        PathPatternParser.defaultInstance.parse(a), PathPatternParser.defaultInstance.parse(b));
  }
}
