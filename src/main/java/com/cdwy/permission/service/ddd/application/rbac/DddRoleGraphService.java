package com.cdwy.permission.service.ddd.application.rbac;

import com.cdwy.permission.service.ddd.domain.model.RoleGraph;
import com.cdwy.permission.service.port.rbac.RoleGraphUseCase;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 面向对象应用服务只编排纯角色图，不接触 JPA 仓储或实体。 */
@Service
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddRoleGraphService implements RoleGraphUseCase {
  /** 让领域图维护无环规则。 */
  public void validate(Long senior, Long inherited, Map<Long, List<Node>> graph) {
    new RoleGraph(graph).assertCanInherit(senior, inherited);
  }

  /** 将图遍历交给领域对象，返回共享结果类型。 */
  public List<Path> resolve(Node assigned, Map<Long, List<Node>> graph) {
    return new RoleGraph(graph).effectivePaths(assigned);
  }

  /** 标识面向对象实现。 */
  public String strategy() {
    return "ddd";
  }
}
