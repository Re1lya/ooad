package com.cdwy.permission.service.ddd.application.user;

import com.cdwy.permission.service.ddd.domain.model.*;
import com.cdwy.permission.service.ddd.domain.repository.*;
import com.cdwy.permission.service.port.data.*;
import com.cdwy.permission.service.port.error.*;
import com.cdwy.permission.service.port.user.UserUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DddUserService 应用服务：编排领域对象与仓储端口，禁止依赖 JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddUserService implements UserUseCase {
  /** 返回实际装载模式。 */
  @Override
  public String strategy() {
    return "ddd";
  }

  private final AccountRepository accounts;
  private final DepartmentDirectory departments;
  private final AssignmentRepository assignments;
  private final com.cdwy.permission.service.port.auth.CredentialGateway credentials;

  /** 读取账号领域对象。 */
  private Account require(Long id) {
    return accounts.find(id).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "用户不存在"));
  }

  /** 写操作锁定账号，保留凭证版本的并发语义。 */
  private Account lock(Long id) {
    return accounts.lock(id).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "用户不存在"));
  }

  /** 领域属性到结果的同型字段映射。 */
  private UserResult view(Account a) {
    UserResult result = cn.edu.xmu.clonefactory.util.CloneFactory.copy(new UserResult(), a);
    result.setKind(a.getKind());
    return result;
  }

  /** 部门存在性属于跨对象一致性。 */
  private void department(Long id) {
    if (id != null && departments.find(id).isEmpty()) throw new ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "部门不存在");
  }

  /** 当前人员资料。 */
  public UserResult get(Long id) {
    return view(require(id));
  }

  /** 员工列表与关键字检索都在数据库分页。 */
  public UserData.Page<UserResult> list(int page, int pageSize, String keyword) {
    UserData.Page<Account> values = accounts.search(keyword, page, pageSize);
    return new UserData.Page<>(
        page, pageSize, values.total(), values.items().stream().map(this::view).toList());
  }

  /** 新账号默认普通身份，角色授权由独立用例执行。 */
  @Transactional
  public UserResult create(CreateUserCommand input) {
    if (accounts.username(input.getUsername()).isPresent()) throw new ConflictException("USERNAME_EXISTS", "登录名已存在");
    credentials.validatePassword(input.getPassword());
    department(input.getDepartmentId());
    Account account = new Account();
    account.setUsername(input.getUsername());
    account.setPasswordHash(credentials.encode(input.getPassword()));
    account.profile(
        input.getDisplayName(),
        input.getDepartmentId(),
        input.getKind(),
        input.getExpiresOn(),
        credentials.window());
    return view(accounts.save(account));
  }

  /** 修改身份资料，由对象维护期限规则。 */
  @Transactional
  public UserResult update(Long id, UserData.Update input) {
    Account account = lock(id);
    department(input.departmentId());
    account.profile(
        input.displayName(),
        input.departmentId(),
        input.personType(),
        input.expiresOn(),
        credentials.window());
    return view(accounts.save(account));
  }

  /** 状态切换同步使旧 JWT 失效。 */
  @Transactional
  public UserResult status(Long actor, Long id, boolean enabled) {
    Account account = lock(id);
    account.changeStatus(actor, enabled);
    return view(accounts.save(account));
  }

  /** 设置密码不自动启用账号。 */
  @Transactional
  public void password(Long id, String password) {
    credentials.validatePassword(password);
    Account account = lock(id);
    account.resetPassword(credentials.encode(password));
    accounts.save(account);
  }

  /** 先解除个人角色再删除非受保护账号，事务原子提交。 */
  @Transactional
  public void delete(Long actor, Long id) {
    Account account = lock(id);
    account.assertDeletable(actor);
    assignments.removeUserRoles(id);
    accounts.delete(id);
  }
}
