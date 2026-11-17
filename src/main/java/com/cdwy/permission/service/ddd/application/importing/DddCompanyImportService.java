package com.cdwy.permission.service.ddd.application.importing;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository.Link;
import com.cdwy.permission.service.port.data.CompanySummary;
import com.cdwy.permission.service.port.importing.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 公司三表应用服务：一次事务合并领域对象，文件解析留在共享边界。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddCompanyImportService implements CompanyImportUseCase {
  private final DepartmentDirectory departments;
  private final AccountRepository accounts;
  private final RoleCatalog roles;
  private final SystemCatalog systems;
  private final PermissionCatalog permissions;
  private final AssignmentRepository assignments;

  /** 实际模式。 */
  public String strategy() {
    return "ddd";
  }

  /** 摘要确认、部门与员工同步、模板撤权在同一事务，失败全部回滚。 */
  @Transactional
  public CompanySummary commit(CompanyImportPlan plan, String digest) {
    CompanyImportBatch batch = new CompanyImportBatch(plan);
    batch.assertConfirmed(digest);
    Map<String, DepartmentNode> ds =
        departments.all().stream()
            .collect(Collectors.toMap(DepartmentNode::getName, Function.identity()));
    for (CompanyImportPlan.DepartmentRow row : plan.departments()) {
      DepartmentNode n = ds.get(row.name());
      if (n == null) {
        n = new DepartmentNode();
        n.setName(row.name());
        n = departments.save(n);
        ds.put(row.name(), n);
      }
      n.setManagerName(row.manager());
    }
    Map<Long, DepartmentNode> graph = new LinkedHashMap<>();
    ds.values().forEach(n -> graph.put(n.getId(), n));
    for (CompanyImportPlan.DepartmentRow row : plan.departments()) {
      DepartmentNode n = ds.get(row.name());
      n.relocate(row.name(), row.parent().isEmpty() ? null : ds.get(row.parent()).getId(), graph);
      departments.save(n);
    }
    Map<String, Account> us =
        accounts.all().stream()
            .collect(Collectors.toMap(Account::getUsername, Function.identity()));
    for (CompanyImportPlan.EmployeeRow row : plan.employees()) {
      Account a = us.get(row.username());
      if (a == null) {
        a = new Account();
        a.setUsername(row.username());
        a.setPasswordHash("!UNPROVISIONED:" + UUID.randomUUID());
        a.setEnabled(false);
        us.put(row.username(), a);
      }
      a.importSource(
          row.name(),
          ds.get(row.department()).getId(),
          row.company(),
          row.sourceDepartment(),
          row.job(),
          row.phone());
      accounts.save(a);
    }
    Map<String, AccessRole> rs =
        roles.all().stream().collect(Collectors.toMap(AccessRole::getCode, Function.identity()));
    List<AccessRole> templates = new ArrayList<>();
    for (String name : plan.roles()) {
      String code = batch.code("role", name);
      AccessRole role = rs.get(code);
      if (role == null) {
        role = new AccessRole();
        role.setCode(code);
        role.rename(name, "Excel权限矩阵模板；不代表系统管理身份或自动个人授权");
        role = roles.save(role);
        rs.put(code, role);
      }
      templates.add(role);
    }
    Map<String, ManagedSystem> apps =
        systems.all().stream()
            .collect(Collectors.toMap(ManagedSystem::getCode, Function.identity()));
    Map<String, FrontendPermission> nodes =
        permissions.all().stream()
            .collect(Collectors.toMap(FrontendPermission::getCode, Function.identity()));
    Set<Link> assigned = new HashSet<>(assignments.rolePermissions());
    for (CompanyImportPlan.FunctionRow row : plan.functions()) {
      String code = batch.code("app", row.system());
      ManagedSystem app = apps.get(code);
      if (app == null) {
        app = new ManagedSystem();
        app.setCode(code);
        app.setName(row.system());
        app = systems.save(app);
        apps.put(code, app);
      }
      FrontendPermission module =
          node(
              batch,
              nodes,
              batch.code("module", row.system() + "/" + row.module()),
              row.module(),
              FrontendPermission.MENU,
              app.getId(),
              null);
      FrontendPermission function =
          node(
              batch,
              nodes,
              batch.code("function", row.system() + "/" + row.module() + "/" + row.name()),
              row.name(),
              FrontendPermission.MENU,
              app.getId(),
              module.getId());
      function.setSourceDescription(row.description());
      StringJoiner raw = new StringJoiner("；");
      for (int i = 0; i < 5; i++) raw.add(plan.roles().get(i) + "=" + row.grants().get(i));
      function.setSourceGrants(raw.toString());
      permissions.save(function);
      Set<String> actions = new LinkedHashSet<>();
      actions.add("查看");
      row.grants().forEach(cell -> actions.addAll(CompanyImportPlan.actions(cell)));
      Set<String> desired = Set.copyOf(actions);
      nodes.values().stream()
          .filter(n -> n.getCode().startsWith(function.getCode() + ":"))
          .map(FrontendPermission::getName)
          .forEach(actions::add);
      for (String action : actions) {
        FrontendPermission button =
            node(
                batch,
                nodes,
                function.getCode() + ":" + batch.actionCode(action),
                action,
                FrontendPermission.BUTTON,
                app.getId(),
                function.getId());
        for (int i = 0; i < templates.size(); i++) {
          AccessRole role = templates.get(i);
          Link link = new Link(role.getId(), button.getId());
          boolean wanted = batch.grants(row, i, action, desired);
          if (wanted && assigned.add(link))
            assignments.addRolePermission(role.getId(), button.getId());
          else if (!wanted && assigned.remove(link))
            assignments.removeRolePermission(role.getId(), button.getId());
        }
      }
    }
    return plan.summary();
  }

  /** 来源节点复用并校验稳定业务身份，不改变手工状态或 API 绑定。 */
  private FrontendPermission node(
      CompanyImportBatch batch,
      Map<String, FrontendPermission> nodes,
      String code,
      String name,
      String type,
      Long app,
      Long parent) {
    FrontendPermission n = nodes.get(code);
    if (n == null) {
      n = new FrontendPermission();
      n.setCode(code);
      n.setName(name);
      n.setKind(type);
      n.setSystemId(app);
      n.setParentId(parent);
      n = permissions.save(n);
      nodes.put(code, n);
    } else batch.assertCompatible(n, app, type);
    return n;
  }
}
