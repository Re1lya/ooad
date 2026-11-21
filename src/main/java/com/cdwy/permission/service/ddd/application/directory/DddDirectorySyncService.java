package com.cdwy.permission.service.ddd.application.directory;

import com.cdwy.permission.service.ddd.domain.model.Account;
import com.cdwy.permission.service.ddd.domain.model.AccessRole;
import com.cdwy.permission.service.ddd.domain.model.DepartmentNode;
import com.cdwy.permission.service.ddd.domain.model.EmployeeDirectory;
import com.cdwy.permission.service.ddd.domain.repository.AccountRepository;
import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository;
import com.cdwy.permission.service.ddd.domain.repository.AssignmentRepository.Link;
import com.cdwy.permission.service.ddd.domain.repository.DepartmentDirectory;
import com.cdwy.permission.service.ddd.domain.repository.RoleCatalog;
import com.cdwy.permission.service.port.data.DirectoryData;
import com.cdwy.permission.service.port.directory.DirectorySyncUseCase;
import com.cdwy.permission.service.port.directory.EmployeeSheetReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DDD 应用服务：加载部门、账号与角色分配，交给 {@link EmployeeDirectory} 决定变化，再经仓储端口写入。
 *
 * <p>预览时新部门只分配临时标识、账号不保存；确认在一个事务内完成，任何一行冲突都整批回滚。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddDirectorySyncService implements DirectorySyncUseCase {
  private static final Logger log = LoggerFactory.getLogger(DddDirectorySyncService.class);

  private final EmployeeSheetReader reader;
  private final DepartmentDirectory departments;
  private final AccountRepository accounts;
  private final RoleCatalog roles;
  private final AssignmentRepository assignments;

  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  @Override
  @Transactional(readOnly = true)
  public DirectoryData.Summary preview(byte[] workbook, DirectoryData.Rule rule) {
    DirectoryData.Sheet sheet = reader.read(workbook);
    return run(sheet, rule, DirectoryData.digest(sheet, rule), false);
  }

  @Override
  @Transactional
  public DirectoryData.Summary commit(byte[] workbook, DirectoryData.Rule rule, String digest) {
    DirectoryData.Sheet sheet = reader.read(workbook);
    String expected = DirectoryData.digest(sheet, rule);
    DirectoryData.requireConfirmed(expected, digest);
    return run(sheet, rule, expected, true);
  }

  private DirectoryData.Summary run(
      DirectoryData.Sheet sheet, DirectoryData.Rule rule, String digest, boolean write) {
    Map<String, Long> roleIds = roleIds(rule);
    EmployeeDirectory directory = new EmployeeDirectory(rule, sheet.rows());

    AtomicLong temporary = new AtomicLong();
    directory.placeDepartments(
        departments.all(),
        write
            ? this::create
            : node -> {
              node.setId(-temporary.incrementAndGet());
              return node;
            });

    Map<String, Account> byUsername = new HashMap<>();
    accounts.all().forEach(account -> byUsername.put(account.getUsername(), account));
    List<Account> changed = directory.reconcile(byUsername);
    if (write)
      accounts.saveAll(changed).forEach(saved -> byUsername.put(saved.getUsername(), saved));

    Map<Long, Set<Long>> holders = new HashMap<>();
    for (Link link : assignments.userRoles())
      if (roleIds.containsValue(link.right()))
        holders.computeIfAbsent(link.right(), ignored -> new HashSet<>()).add(link.left());
    List<EmployeeDirectory.Grant> grants = directory.grants(byUsername, roleIds, holders);
    if (write) {
      assignments.addUserRoles(
          grants.stream()
              .map(g -> new Link(byUsername.get(g.username()).getId(), g.roleId()))
              .toList());
      log.info("员工信息同步完成：{}", directory.summary(digest, true));
    }
    return directory.summary(digest, write);
  }

  /** 保存新部门，没有编码时取默认编码。 */
  private DepartmentNode create(DepartmentNode node) {
    DepartmentNode saved = departments.save(node);
    return saved.assignDefaultCode() ? departments.save(saved) : saved;
  }

  /** 规则中的角色必须存在。 */
  private Map<String, Long> roleIds(DirectoryData.Rule rule) {
    Map<String, Long> ids = new HashMap<>();
    for (String code : Arrays.asList(rule.headOfficeRole(), rule.branchRole()))
      if (code != null && !ids.containsKey(code))
        ids.put(
            code,
            roles.all().stream()
                .filter(role -> role.getCode().equals(code))
                .map(AccessRole::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + code)));
    return ids;
  }
}
