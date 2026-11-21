package com.cdwy.permission.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.cdwy.permission.entity.user.UserAccount;
import com.cdwy.permission.repository.user.UserAccountRepository;
import com.cdwy.permission.service.ddd.domain.model.Account;
import com.cdwy.permission.service.ddd.domain.repository.AccountRepository;
import com.cdwy.permission.service.port.error.ConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** AccountRepository 的 JPA 适配器：实体不离开基础设施边界。 */
@Repository
@RequiredArgsConstructor
public class JpaAccountRepositoryAdapter implements AccountRepository {
  private final UserAccountRepository data;
  private final com.cdwy.permission.repository.organization.DepartmentRepository departments;

  /** 根据标识读取领域快照。 */
  public Optional<Account> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按稳定顺序读取，供管理与导入合并。 */
  public List<Account> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 同类型字段交由 CloneFactory，关联及枚举显式转换。 */
  private Account model(UserAccount e) {
    Account v = CloneFactory.copy(new Account(), e);
    v.setKind(e.getPersonType().name());
    v.setDepartmentId(e.getDepartmentId());
    return v;
  }

  /** 身份不由调用方替换；当前事务捕获唯一约束并翻译为业务冲突。 */
  public Account save(Account v) {
    UserAccount e =
        v.getId() == null
            ? new UserAccount()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "对象不存在"));
    CloneFactory.copy(e, v);
    e.setPersonType(com.cdwy.permission.service.port.data.PersonType.valueOf(v.getKind()));
    e.setDepartment(
        v.getDepartmentId() == null ? null : departments.getReferenceById(v.getDepartmentId()));
    try {
      return model(data.saveAndFlush(e));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /** 批量保存：已有账号一次读取，全部写入后统一刷新。 */
  public List<Account> saveAll(List<Account> values) {
    java.util.Map<Long, UserAccount> current = new java.util.HashMap<>();
    data.findAllById(
            values.stream().map(Account::getId).filter(java.util.Objects::nonNull).toList())
        .forEach(e -> current.put(e.getId(), e));
    List<UserAccount> entities = new java.util.ArrayList<>();
    for (Account v : values) {
      UserAccount e = v.getId() == null ? new UserAccount() : current.get(v.getId());
      CloneFactory.copy(e, v);
      e.setPersonType(com.cdwy.permission.service.port.data.PersonType.valueOf(v.getKind()));
      e.setDepartment(
          v.getDepartmentId() == null ? null : departments.getReferenceById(v.getDepartmentId()));
      entities.add(e);
    }
    try {
      List<UserAccount> saved = data.saveAllAndFlush(entities);
      return saved.stream().map(this::model).toList();
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /** 删除前由领域服务检查业务引用，数据库保留最终约束。 */
  public void delete(Long id) {
    data.deleteById(id);
  }

  /** 锁定账号，串行化状态与密码变更。 */
  public Optional<Account> lock(Long id) {
    return data.lockById(id).map(this::model);
  }

  /** 按自然登录名读取。 */
  public Optional<Account> username(String name) {
    return data.findByUsername(name).map(this::model);
  }

  /** 关键字检索的数据库分页。 */
  public com.cdwy.permission.service.port.data.UserData.Page<Account> search(
      String keyword, int page, int size) {
    org.springframework.data.domain.Page<UserAccount> rows =
        data.search(
            com.cdwy.permission.repository.LikePattern.of(keyword),
            org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("id")));
    return new com.cdwy.permission.service.port.data.UserData.Page<>(
        rows.map(this::model).getContent(), rows.getTotalElements(), page, size);
  }

  /** 部门成员数据库分页。 */
  public com.cdwy.permission.service.port.data.UserData.Page<Account> members(
      Long departmentId, String keyword, int page, int size) {
    org.springframework.data.domain.Page<UserAccount> rows =
        data.members(
            departmentId,
            com.cdwy.permission.repository.LikePattern.of(keyword),
            org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("username")));
    return new com.cdwy.permission.service.port.data.UserData.Page<>(
        rows.map(this::model).getContent(), rows.getTotalElements(), page, size);
  }

  /** 部门删除的反向引用检查。 */
  public boolean inDepartment(Long id) {
    return data.existsByDepartment_Id(id);
  }
}
