package com.cdwy.permission.service.ddd.domain.model;

import com.cdwy.permission.service.port.data.DirectoryData;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * 员工信息同步的领域对象（信息专家）：持有员工表与同步规则，决定部门复用还是新建、人员新建还是更新、补授哪些角色，并统计变化。
 *
 * <p>不依赖数据库：新建部门交给调用方提供的函数保存并取得标识（预览时只分配临时标识）；人员是否需要保存由 {@link
 * Account#syncSource} 判断。
 */
public class EmployeeDirectory {
  /** 需要补授的一条角色分配。 */
  public record Grant(String username, Long roleId) {}

  private final DirectoryData.Rule rule;
  private final List<DirectoryData.Row> rows;
  private final Map<String, Long> departmentIds = new HashMap<>();
  private int departmentsCreated, departmentsKept, usersCreated, usersUpdated, usersUnchanged;
  private int roleGrants;

  public EmployeeDirectory(DirectoryData.Rule rule, List<DirectoryData.Row> rows) {
    this.rule = rule;
    this.rows = List.copyOf(rows);
  }

  /** 分公司为根部门、部门为其子部门；按“上级 + 名称”复用已有部门，缺少的交给 create 保存。 */
  public void placeDepartments(
      Collection<DepartmentNode> existing, UnaryOperator<DepartmentNode> create) {
    Map<String, DepartmentNode> known = new HashMap<>();
    for (DepartmentNode node : existing) known.put(key(node.getParentId(), node.getName()), node);
    for (DirectoryData.Row row : rows) {
      Long company = place(known, null, row.company(), row.company(), create);
      place(known, company, row.department(), departmentKey(row), create);
    }
  }

  private Long place(
      Map<String, DepartmentNode> known,
      Long parent,
      String name,
      String rowKey,
      UnaryOperator<DepartmentNode> create) {
    Long placed = departmentIds.get(rowKey);
    if (placed != null) return placed;
    DepartmentNode node = known.get(key(parent, name));
    if (node == null) {
      node = new DepartmentNode();
      node.setName(name);
      node.setParentId(parent);
      node = create.apply(node);
      known.put(key(parent, name), node);
      departmentsCreated++;
    } else departmentsKept++;
    departmentIds.put(rowKey, node.getId());
    return node.getId();
  }

  /**
   * 按用户名新建或更新人员，返回需要保存的账号（新建的标识为空）；existing 会补入新建的账号。
   *
   * @param existing 用户名 → 已有账号
   */
  public List<Account> reconcile(Map<String, Account> existing) {
    List<Account> changed = new ArrayList<>();
    for (DirectoryData.Row row : rows) {
      Account account = existing.get(row.username());
      boolean fresh = account == null;
      if (fresh) {
        account = Account.unprovisioned(row.username());
        existing.put(row.username(), account);
      }
      boolean modified =
          account.syncSource(
              row.name(),
              departmentIds.get(departmentKey(row)),
              row.company(),
              row.department(),
              row.job(),
              row.phone());
      if (fresh) usersCreated++;
      else if (modified) usersUpdated++;
      else usersUnchanged++;
      if (fresh || modified) changed.add(account);
    }
    return changed;
  }

  /**
   * 按分公司选择角色、只增不减的补授清单；已持有的不重复授予，尚未保存的新人员一定需要。
   *
   * @param accounts 用户名 → 账号（确认时为已保存的账号）
   * @param roleIds 角色编码 → 标识
   * @param holders 角色标识 → 已持有该角色的用户标识
   */
  public List<Grant> grants(
      Map<String, Account> accounts, Map<String, Long> roleIds, Map<Long, Set<Long>> holders) {
    List<Grant> result = new ArrayList<>();
    for (DirectoryData.Row row : rows) {
      String code = rule.roleFor(row.company());
      if (code == null) continue;
      Long role = roleIds.get(code);
      Long user = accounts.get(row.username()).getId();
      if (user == null || !holders.getOrDefault(role, Set.of()).contains(user))
        result.add(new Grant(row.username(), role));
    }
    roleGrants = result.size();
    return result;
  }

  /** 汇总本次同步的变化。 */
  public DirectoryData.Summary summary(String digest, boolean applied) {
    return new DirectoryData.Summary(
        rows.size(),
        (int) rows.stream().map(DirectoryData.Row::company).distinct().count(),
        departmentsCreated,
        departmentsKept,
        usersCreated,
        usersUpdated,
        usersUnchanged,
        roleGrants,
        digest,
        applied);
  }

  private static String departmentKey(DirectoryData.Row row) {
    return row.company() + "\u0000" + row.department();
  }

  private static String key(Long parent, String name) {
    return parent + "\u0000" + name;
  }
}
