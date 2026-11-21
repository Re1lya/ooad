package com.cdwy.permission.service.directory;

import com.cdwy.permission.entity.organization.Department;
import com.cdwy.permission.entity.rbac.UserRole;
import com.cdwy.permission.entity.role.Role;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.organization.DepartmentRepository;
import com.cdwy.permission.repository.rbac.UserRoleRepository;
import com.cdwy.permission.repository.role.RoleRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.data.DepartmentData;
import com.cdwy.permission.service.port.data.DirectoryData;
import com.cdwy.permission.service.port.data.PersonType;
import com.cdwy.permission.service.port.directory.DirectorySyncUseCase;
import com.cdwy.permission.service.port.directory.EmployeeSheetReader;
import com.cdwy.permission.service.port.error.ConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cdwy.permission.service.port.error.InvalidDataException;

/**
 * 结构化事务脚本：员工信息同步按“读表 → 角色 → 部门 → 人员 → 角色补授”的固定步骤处理，直接使用 Spring Data Repository。
 *
 * <p>预览与确认走同一段步骤，预览只计数不写入；确认在一个事务内完成，任何一行冲突都整批回滚。
 */
@Service
@ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@RequiredArgsConstructor
public class DirectorySyncService implements DirectorySyncUseCase {
  private static final Logger log = LoggerFactory.getLogger(DirectorySyncService.class);

  /** 新人员的密码散列占位值：不是 BCrypt 格式，任何密码都无法匹配。 */
  static final String UNPROVISIONED = "!UNPROVISIONED:";

  private final EmployeeSheetReader reader;
  private final DepartmentRepository departments;
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final UserRoleRepository userRoles;

  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  /**
   * 在只读事务内走与确认相同的步骤，只计数不写入。规则中的角色不存在抛 {@code SYNC_ROLE_NOT_FOUND}（400），表中用户名属于管理员账号抛
   * {@code SYNC_ADMINISTRATOR_CONFLICT}（409）；文件不合法时由读取器抛 {@code EXCEL_INVALID} 或 {@code EMPLOYEE_SHEET_INVALID}（400）。
   */
  @Override
  @Transactional(readOnly = true)
  public DirectoryData.Summary preview(byte[] workbook, DirectoryData.Rule rule) {
    DirectoryData.Sheet sheet = reader.read(workbook);
    return run(sheet, rule, DirectoryData.digest(sheet, rule), false);
  }

  /** 重新读取文件并计算摘要，与预览返回的不一致抛 {@code SYNC_PREVIEW_MISMATCH}（400）；其余校验同预览，任何失败都整批回滚。 */
  @Override
  @Transactional
  public DirectoryData.Summary commit(byte[] workbook, DirectoryData.Rule rule, String digest) {
    DirectoryData.Sheet sheet = reader.read(workbook);
    String expected = DirectoryData.digest(sheet, rule);
    DirectoryData.requireConfirmed(expected, digest);
    return run(sheet, rule, expected, true);
  }

  /** 步骤 1～4；write 为 false 时只计数，不改动任何实体。 */
  private DirectoryData.Summary run(
      DirectoryData.Sheet sheet, DirectoryData.Rule rule, String digest, boolean write) {
    List<DirectoryData.Row> rows = sheet.rows();

    // 步骤 1：规则中的角色必须存在。
    Map<String, Role> roleByCode = new HashMap<>();
    for (String code : Arrays.asList(rule.headOfficeRole(), rule.branchRole()))
      if (code != null && !roleByCode.containsKey(code))
        roleByCode.put(
            code,
            roles
                .findByCode(code)
                .orElseThrow(() -> new InvalidDataException("SYNC_ROLE_NOT_FOUND", "角色不存在：" + code)));

    // 步骤 2：分公司为根部门、部门为其子部门，按“上级 + 名称”复用已有部门。
    Map<String, Department> existing = new HashMap<>();
    for (Department d : departments.findAll())
      existing.put(key(d.getParent() == null ? null : d.getParent().getId(), d.getName()), d);
    Map<String, Department> placed = new HashMap<>();
    int departmentsCreated = 0, departmentsKept = 0;
    for (DirectoryData.Row row : rows) {
      String companyKey = row.company();
      if (!placed.containsKey(companyKey)) {
        Department company = existing.get(key(null, row.company()));
        if (company == null) {
          company = create(row.company(), null, write);
          departmentsCreated++;
        } else departmentsKept++;
        placed.put(companyKey, company);
      }
      String departmentKey = row.company() + "\u0000" + row.department();
      if (!placed.containsKey(departmentKey)) {
        Department company = placed.get(companyKey);
        Department department =
            company.getId() == null ? null : existing.get(key(company.getId(), row.department()));
        if (department == null) {
          department = create(row.department(), company, write);
          departmentsCreated++;
        } else departmentsKept++;
        placed.put(departmentKey, department);
      }
    }

    // 步骤 3：按用户名新建或更新人员；只更新来源字段，保留凭证、状态、期限与授权。
    Map<String, UserAccount> people = new HashMap<>();
    for (UserAccount user : users.findAll()) people.put(user.getUsername(), user);
    List<UserAccount> created = new ArrayList<>();
    int usersUpdated = 0, usersUnchanged = 0;
    for (DirectoryData.Row row : rows) {
      Department department = placed.get(row.company() + "\u0000" + row.department());
      UserAccount user = people.get(row.username());
      if (user == null) {
        user = new UserAccount();
        user.setUsername(row.username());
        user.setPasswordHash(UNPROVISIONED + UUID.randomUUID());
        user.setEnabled(false);
        user.setPersonType(PersonType.EMPLOYEE);
        apply(user, row, department);
        people.put(row.username(), user);
        created.add(user);
      } else if (user.getAdministrator())
        throw new ConflictException(
            "SYNC_ADMINISTRATOR_CONFLICT", "用户名 " + row.username() + " 是管理员账号，员工信息不能覆盖它");
      else if (differs(user, row, department)) {
        if (write) apply(user, row, department);
        usersUpdated++;
      } else usersUnchanged++;
    }
    if (write) users.saveAll(created);

    // 步骤 4：按分公司补授角色，只增不减。
    Map<Long, Set<Long>> holders = new HashMap<>();
    for (Role role : roleByCode.values())
      holders.put(role.getId(), new HashSet<>(userRoles.holders(role.getId())));
    List<UserRole> grants = new ArrayList<>();
    for (DirectoryData.Row row : rows) {
      String code = rule.roleFor(row.company());
      if (code == null) continue;
      Role role = roleByCode.get(code);
      UserAccount user = people.get(row.username());
      if (user.getId() == null || !holders.get(role.getId()).contains(user.getId()))
        grants.add(new UserRole(user, role));
    }
    if (write) userRoles.saveAll(grants);

    long companies = rows.stream().map(DirectoryData.Row::company).distinct().count();
    if (write)
      log.info(
          "员工信息同步完成：{} 行，新建部门 {}、人员 {}，更新人员 {}，补授角色 {}",
          rows.size(),
          departmentsCreated,
          created.size(),
          usersUpdated,
          grants.size());
    return new DirectoryData.Summary(
        rows.size(),
        (int) companies,
        departmentsCreated,
        departmentsKept,
        created.size(),
        usersUpdated,
        usersUnchanged,
        grants.size(),
        digest,
        write);
  }

  /** 新部门：写入时立即保存并取默认编码；预览时只构造对象。 */
  private Department create(String name, Department parent, boolean write) {
    Department value = new Department();
    value.setName(name);
    value.setParent(parent);
    if (!write) return value;
    departments.save(value);
    value.setCode(DepartmentData.defaultCode(value.getId()));
    return value;
  }

  /** 来源字段（姓名、部门、分公司、来源部门、岗位、电话）是否与员工表不同，决定计入“更新”还是“未变化”。 */
  private static boolean differs(UserAccount user, DirectoryData.Row row, Department department) {
    return !Objects.equals(user.getDisplayName(), row.name())
        || !Objects.equals(user.getDepartmentId(), department.getId())
        || !Objects.equals(user.getCompanyName(), row.company())
        || !Objects.equals(user.getSourceDepartment(), row.department())
        || !Objects.equals(user.getJobTitle(), row.job())
        || !Objects.equals(user.getPhone(), row.phone());
  }

  /** 只覆盖员工表的来源字段；密码、启用状态、人员类型、有效期和角色授权保持不变。 */
  private static void apply(UserAccount user, DirectoryData.Row row, Department department) {
    user.setDisplayName(row.name());
    user.setDepartment(department);
    user.setCompanyName(row.company());
    user.setSourceDepartment(row.department());
    user.setJobTitle(row.job());
    user.setPhone(row.phone());
  }

  private static String key(Long parentId, String name) {
    return parentId + "\u0000" + name;
  }
}
