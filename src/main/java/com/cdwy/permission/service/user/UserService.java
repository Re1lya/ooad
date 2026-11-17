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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户资料、人员身份属性、凭证及账号状态；不管理角色授权。 */
@Service
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

  public UserService(
      UserAccountRepository users,
      DepartmentRepository departments,
      UserRoleRepository grants,
      PasswordEncoder encoder,
      AccessPolicy policy) {
    this.users = users;
    this.departments = departments;
    this.grants = grants;
    this.encoder = encoder;
    this.policy = policy;
  }

  public UserAccount require(Long id) {
    return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
  }

  private UserAccount lock(Long id) {
    return users.lockById(id).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
  }

  public UserResult get(Long id) {
    return UserViews.view(require(id));
  }

  public UserData.Page<UserResult> list(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new IllegalArgumentException("分页参数不合法，size 为 1 到 100");
    org.springframework.data.domain.Page<UserAccount> result =
        users.findAll(PageRequest.of(page, size, Sort.by("id")));
    return new UserData.Page<>(
        result.map(UserViews::view).getContent(), result.getTotalElements(), page, size);
  }

  @Transactional
  public UserResult create(CreateUserCommand input) {
    if (users.existsByUsername(input.getUsername())) throw new ConflictException("登录名已存在");
    PasswordPolicy.validate(input.getPassword());
    UserAccount user = CloneFactory.copy(new UserAccount(), input);
    user.setPasswordHash(encoder.encode(input.getPassword()));
    attributes(
        user, input.getDepartmentId(), PersonType.valueOf(input.getKind()), input.getExpiresOn());
    return UserViews.view(users.save(user));
  }

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

  private void attributes(UserAccount user, Long departmentId, PersonType type, LocalDate day) {
    user.setDepartment(
        departmentId == null
            ? null
            : departments
                .findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("部门不存在")));
    if (type == PersonType.EMPLOYEE) {
      if (day != null) throw new IllegalArgumentException("正式员工不设置临时有效期");
      user.setExpiresAt(null);
    } else {
      if (day == null) throw new IllegalArgumentException("外部或临时人员必须设置有效期");
      LocalDate today = LocalDate.now(policy.zoneId());
      int maximum =
          type == PersonType.EXTERNAL ? policy.externalMaxDays() : policy.temporaryMaxDays();
      if (day.isBefore(today) || day.isAfter(today.plusDays(maximum)))
        throw new IllegalArgumentException("有效期须在今天至 " + maximum + " 天以内");
      user.setExpiresAt(day.plusDays(1).atStartOfDay(policy.zoneId()).toInstant());
    }
    user.setPersonType(type);
  }

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

  @Transactional
  public void password(Long id, String password) {
    PasswordPolicy.validate(password);
    UserAccount user = lock(id);
    user.setPasswordHash(encoder.encode(password));
    user.setCredentialVersion(user.getCredentialVersion() + 1);
  }

  @Transactional
  public void delete(Long actor, Long id) {
    UserAccount user = lock(id);
    if (actor.equals(id) || user.getAdministrator()) throw new ForbiddenException("不能删除本人或管理员");
    grants.deleteAllByUser_Id(id);
    users.delete(user);
  }
}
