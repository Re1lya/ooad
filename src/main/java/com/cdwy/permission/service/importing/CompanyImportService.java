package com.cdwy.permission.service.importing;

import com.cdwy.permission.entity.organization.Department;
import com.cdwy.permission.entity.permission.*;
import com.cdwy.permission.entity.rbac.RolePermission;
import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.entity.system.BusinessSystem;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.organization.DepartmentRepository;
import com.cdwy.permission.repository.permission.PermissionRepository;
import com.cdwy.permission.repository.rbac.RolePermissionRepository;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.repository.system.BusinessSystemRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.data.PermissionType;
import com.cdwy.permission.service.port.error.ConflictException;
import com.cdwy.permission.service.port.importing.CompanyImportPlan;
import com.cdwy.permission.service.port.importing.CompanyImportUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员确认后原子导入公司目录与权限矩阵；不自动启用账号或推导个人授权。 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@RequiredArgsConstructor
public class CompanyImportService implements CompanyImportUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final DepartmentRepository departments;
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final BusinessSystemRepository systems;
  private final PermissionRepository permissions;
  private final RolePermissionRepository grants;

  /** 以自然键复用记录；保留已存在账号的密码、状态、管理员身份和原授权。 */
  @Transactional
  public CompanySummary commit(CompanyImportPlan plan, String digest) {
    if (!plan.digest().equals(digest)) throw new IllegalArgumentException("文件与预览摘要不一致，请重新预览");
    Map<String, Department> ds =
        departments.findAll().stream()
            .collect(Collectors.toMap(Department::getName, Function.identity()));
    for (CompanyImportPlan.DepartmentRow row : plan.departments()) {
      Department entity = ds.get(row.name());
      if (entity == null) {
        entity = new Department();
        entity.setName(row.name());
        ds.put(row.name(), departments.save(entity));
      }
      entity.setManagerName(row.manager());
    }
    for (CompanyImportPlan.DepartmentRow row : plan.departments()) {
      Department entity = ds.get(row.name());
      Department parent = row.parent().isEmpty() ? null : ds.get(row.parent());
      for (Department next = parent; next != null; next = next.getParent())
        if (next.getId().equals(entity.getId())) throw new IllegalArgumentException("与现有部门合并后形成循环");
      entity.setParent(parent);
    }
    Map<String, UserAccount> us =
        users.findAll().stream()
            .collect(Collectors.toMap(UserAccount::getUsername, Function.identity()));
    for (CompanyImportPlan.EmployeeRow row : plan.employees()) {
      UserAccount entity = us.get(row.username());
      if (entity != null && entity.getAdministrator()) throw new ConflictException("导入账号与管理员登录名冲突");
      if (entity == null) {
        entity = new UserAccount();
        entity.setUsername(row.username());
        entity.setPasswordHash("!UNPROVISIONED:" + UUID.randomUUID());
        entity.setEnabled(false);
        us.put(row.username(), entity);
      }
      entity.setDisplayName(row.name());
      entity.setDepartment(ds.get(row.department()));
      entity.setCompanyName(row.company());
      entity.setSourceDepartment(row.sourceDepartment());
      entity.setJobTitle(row.job());
      entity.setPhone(row.phone());
      users.save(entity);
    }
    Map<String, Role> rs =
        roles.findAll().stream().collect(Collectors.toMap(Role::getCode, Function.identity()));
    List<Role> templates = new ArrayList<>();
    for (String name : plan.roles()) {
      String code = code("role", name);
      Role role = rs.get(code);
      if (role == null) {
        role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setDescription("Excel权限矩阵模板；不代表系统管理身份或自动个人授权");
        role = roles.save(role);
        rs.put(code, role);
      }
      templates.add(role);
    }
    Map<String, BusinessSystem> apps =
        systems.findAll().stream()
            .collect(Collectors.toMap(BusinessSystem::getCode, Function.identity()));
    Map<String, Permission> nodes =
        permissions.findAll().stream()
            .collect(Collectors.toMap(Permission::getCode, Function.identity()));
    Set<String> assigned =
        grants.findAll().stream()
            .map(g -> g.getRole().getId() + ":" + g.getPermission().getId())
            .collect(Collectors.toSet());
    for (CompanyImportPlan.FunctionRow row : plan.functions()) {
      String appCode = code("app", row.system());
      BusinessSystem app = apps.get(appCode);
      if (app == null) {
        app = new BusinessSystem();
        app.setCode(appCode);
        app.setName(row.system());
        app = systems.save(app);
        apps.put(appCode, app);
      }
      Permission module =
          node(
              nodes,
              code("module", row.system() + "/" + row.module()),
              row.module(),
              PermissionType.MENU,
              app,
              null);
      Permission function =
          node(
              nodes,
              code("function", row.system() + "/" + row.module() + "/" + row.name()),
              row.name(),
              PermissionType.MENU,
              app,
              module);
      function.setSourceDescription(row.description());
      StringJoiner raw = new StringJoiner("；");
      for (int i = 0; i < 5; i++) raw.add(plan.roles().get(i) + "=" + row.grants().get(i));
      function.setSourceGrants(raw.toString());
      Set<String> actions = new LinkedHashSet<>();
      actions.add("查看");
      row.grants().forEach(cell -> actions.addAll(CompanyImportPlan.actions(cell)));
      Set<String> desired = Set.copyOf(actions);
      nodes.values().stream()
          .filter(n -> n.getCode().startsWith(function.getCode() + ":"))
          .map(Permission::getName)
          .forEach(actions::add);
      for (String action : actions) {
        Permission button =
            node(
                nodes,
                function.getCode() + ":" + actionCode(action),
                action,
                PermissionType.BUTTON,
                app,
                function);
        for (int i = 0; i < templates.size(); i++) {
          Role role = templates.get(i);
          String key = role.getId() + ":" + button.getId();
          boolean shouldGrant =
              desired.contains(action)
                  && (row.grants().get(i).equals("全部")
                      || CompanyImportPlan.actions(row.grants().get(i)).contains(action));
          if (shouldGrant && assigned.add(key)) grants.save(new RolePermission(role, button));
          else if (!shouldGrant && assigned.remove(key))
            grants.deleteByRole_IdAndPermission_Id(role.getId(), button.getId());
        }
      }
    }
    return plan.summary();
  }

  /** 稳定的来源自然键，重复导入不依赖工作表行号。 */
  private static String code(String type, String value) {
    try {
      return "company_"
          + type
          + "_"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(value.getBytes(StandardCharsets.UTF_8)))
              .substring(0, 20);
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** 映射操作编码，self 与全量操作保持区分。 */
  private static String actionCode(String action) {
    return switch (action) {
      case "查看" -> "view";
      case "编辑" -> "edit";
      case "新增" -> "create";
      case "审批" -> "approve";
      case "导出" -> "export";
      case "查看本人" -> "view_self";
      case "编辑本人" -> "edit_self";
      default -> throw new IllegalArgumentException("未知操作");
    };
  }

  /** 复用来源权限节点，不改用户手工添加的状态及 API 绑定。 */
  private Permission node(
      Map<String, Permission> nodes,
      String code,
      String name,
      PermissionType type,
      BusinessSystem app,
      Permission parent) {
    Permission node = nodes.get(code);
    if (node == null) {
      node = new Permission();
      node.setCode(code);
      node.setName(name);
      node.setType(type);
      node.setSystem(app);
      node.setParent(parent);
      node = permissions.save(node);
      nodes.put(code, node);
    } else if (!node.getSystem().getId().equals(app.getId()) || node.getType() != type)
      throw new ConflictException("来源权限与已有节点定义冲突");
    return node;
  }
}
