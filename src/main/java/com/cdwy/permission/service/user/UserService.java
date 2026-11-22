package com.cdwy.permission.service.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.config.AccessPolicy;
import com.cdwy.permission.entity.user.*;
import com.cdwy.permission.repository.organization.DepartmentRepository;
import com.cdwy.permission.repository.rbac.UserRoleRepository;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.data.PersonType;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.user.UserUseCase;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户资料、人员身份属性、凭证及账号状态；不管理角色授权。 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.backend-strategy",
    havingValue = "structured",
    matchIfMissing = true)
@Transactional(readOnly = true)
public class UserService implements UserUseCase {
  /** 返回事务脚本实现标识。 */
  @Override
  public String strategy() {
    return "structured";
  }

  private final UserAccountRepository users;
  private final DepartmentRepository departments;
  private final UserRoleRepository grants;
  private final PasswordEncoder encoder;
  private final AccessPolicy policy;

  /** 按标识读取账号（不加锁），不存在抛 {@code USER_NOT_FOUND}；授权服务也用它确认用户存在。 */
  public UserAccount require(Long id) {
    return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "用户不存在"));
  }

  /**
   * 以悲观写锁读取账号并持有到事务结束：同一账号的并发写入因此串行，凭证版本递增不会丢失；
   * 不存在抛 {@code USER_NOT_FOUND}。
   */
  private UserAccount lock(Long id) {
    return users.lockById(id).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "用户不存在"));
  }

  /** 人员详情；不存在抛 {@code USER_NOT_FOUND}，结果不含密码散列和凭证版本。 */
  public UserResult get(Long id) {
    return UserViews.view(require(id));
  }

  /** 步骤：数据库按关键字分页查询 → 转为结果。分页参数由 API 层校验。 */
  public UserData.Page<UserResult> list(int page, int pageSize, String keyword) {
    org.springframework.data.domain.Page<UserAccount> result =
        users.search(
            com.cdwy.permission.repository.LikePattern.of(keyword),
            PageRequest.of(page - 1, pageSize, Sort.by("id")));
    return new UserData.Page<>(
        page, pageSize, result.getTotalElements(), result.map(UserViews::view).getContent());
  }

  /**
   * 新建人员：登录名唯一（冲突 {@code USERNAME_EXISTS}），密码须符合 {@link PasswordPolicy}
   * （{@code PASSWORD_POLICY_VIOLATION}），部门与有效期规则见 {@code attributes}。新账号启用、非管理员、
   * 凭证版本为 0 且没有角色，角色由授权用例另行分配；不写审计日志。
   */
  @Transactional
  public UserResult create(CreateUserCommand input) {
    if (users.existsByUsername(input.getUsername())) throw new ConflictException("USERNAME_EXISTS", "登录名已存在");
    PasswordPolicy.validate(input.getPassword());
    UserAccount user = CloneFactory.copy(new UserAccount(), input);
    user.setPasswordHash(encoder.encode(input.getPassword()));
    attributes(
        user, input.getDepartmentId(), PersonType.valueOf(input.getKind()), input.getExpiresOn());
    return UserViews.view(users.save(user));
  }

  /**
   * 修改姓名、部门、人员类型与有效期，不改登录名、密码、启用状态和导入来源字段；账号加写锁
   * （{@code USER_NOT_FOUND}）。管理员只能是不设有效期的正式员工，否则抛 {@link ForbiddenException}；
   * 部门与有效期规则见 {@code attributes}。凭证版本不变：鉴权每次请求都检查有效期，新期限对已签发令牌立即生效。
   * 事务提交时写回，不写审计日志。
   */
  @Transactional
  public UserResult update(Long id, UserData.Update input) {
    UserAccount user = lock(id);
    if (user.getAdministrator()
        && (!input.personType().equals(PersonType.EMPLOYEE.name()) || input.expiresOn() != null))
      throw new ForbiddenException("管理员不能改为有期限的人员身份");
    user.setDisplayName(input.displayName());
    attributes(
        user, input.departmentId(), PersonType.valueOf(input.personType()), input.expiresOn());
    return UserViews.view(user);
  }

  /**
   * 写入部门与人员期限：部门为空即清除，非空须存在（{@code DEPARTMENT_NOT_FOUND}）。正式员工不能设有效期；
   * 外部、临时人员必须设，且在项目时区的今天至今天加各自上限天数之间（{@code permission.policy} 配置），
   * 违反均抛 {@code ACCESS_WINDOW_INVALID}。截止日当天仍可用，到期时刻存为次日零点。
   */
  private void attributes(UserAccount user, Long departmentId, PersonType type, LocalDate day) {
    user.setDepartment(
        departmentId == null
            ? null
            : departments
                .findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "部门不存在")));
    if (type == PersonType.EMPLOYEE) {
      if (day != null) throw new InvalidDataException("ACCESS_WINDOW_INVALID", "正式员工不设置临时有效期");
      user.setExpiresAt(null);
    } else {
      if (day == null) throw new InvalidDataException("ACCESS_WINDOW_INVALID", "外部或临时人员必须设置有效期");
      LocalDate today = LocalDate.now(policy.zoneId());
      int maximum =
          type == PersonType.EXTERNAL ? policy.externalMaxDays() : policy.temporaryMaxDays();
      if (day.isBefore(today) || day.isAfter(today.plusDays(maximum)))
        throw new InvalidDataException("ACCESS_WINDOW_INVALID", "有效期须在今天至 " + maximum + " 天以内");
      user.setExpiresAt(day.plusDays(1).atStartOfDay(policy.zoneId()).toInstant());
    }
    user.setPersonType(type);
  }

  /**
   * 启用或停用账号，{@code actor} 为当前操作人：不能停用本人或管理员（{@link ForbiddenException}），启用不受此限；
   * 账号加写锁（{@code USER_NOT_FOUND}）。状态确有变化时凭证版本加一，此前签发的令牌全部失效，重新启用也不会
   * 让旧令牌恢复；重复提交同一状态不改版本。事务提交时写回，不写审计日志。
   */
  @Transactional
  public UserResult status(Long actor, Long id, boolean enabled) {
    UserAccount user = lock(id);
    if (!enabled && (actor.equals(id) || user.getAdministrator()))
      throw new ForbiddenException("不能停用本人或管理员");
    if (user.getEnabled() != enabled) {
      user.setEnabled(enabled);
      user.setCredentialVersion(user.getCredentialVersion() + 1);
    }
    return UserViews.view(user);
  }

  /**
   * 设置新密码，管理员重置与本人改密共用（本人改密由认证服务先核对原密码）。先校验密码策略
   * （{@code PASSWORD_POLICY_VIOLATION}），再加写锁读取账号（{@code USER_NOT_FOUND}）；凭证版本加一，
   * 该账号已签发的令牌全部失效，包括发起请求的令牌。不改启用状态，事务提交时写回，不写审计日志。
   */
  @Transactional
  public void password(Long id, String password) {
    PasswordPolicy.validate(password);
    UserAccount user = lock(id);
    user.setPasswordHash(encoder.encode(password));
    user.setCredentialVersion(user.getCredentialVersion() + 1);
  }

  /**
   * 删除账号，{@code actor} 为当前操作人：账号加写锁（{@code USER_NOT_FOUND}），不能删除本人或管理员
   * （{@link ForbiddenException}）；同一事务内先删其全部角色分配再删账号。删除后其令牌在下一次请求时失效；
   * 不写审计日志。
   */
  @Transactional
  public void delete(Long actor, Long id) {
    UserAccount user = lock(id);
    if (actor.equals(id) || user.getAdministrator()) throw new ForbiddenException("不能删除本人或管理员");
    grants.deleteAllByUser_Id(id);
    users.delete(user);
  }
}
