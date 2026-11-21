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

  /** 按主键读取并拷贝为领域对象，不加锁；不存在时返回空 Optional。 */
  public Optional<Account> find(Long id) {
    return data.findById(id).map(this::model);
  }

  /** 按主键升序读取整张账号表并逐个拷贝，不分页；列表和检索走数据库分页的 {@link #search}。 */
  public List<Account> all() {
    return data.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .map(this::model)
        .toList();
  }

  /** 实体转领域对象：同名同类型字段由 CloneFactory 拷贝，人员身份枚举转为名称字符串，部门关联只保留标识。 */
  private Account model(UserAccount e) {
    Account v = CloneFactory.copy(new Account(), e);
    v.setKind(e.getPersonType().name());
    v.setDepartmentId(e.getDepartmentId());
    return v;
  }

  /**
   * 标识为空时新建实体，否则读出已有实体（不存在抛 {@code USER_NOT_FOUND}），再由 CloneFactory 覆盖，
   * 主键不参与拷贝；人员身份按枚举名转换，部门只取引用、不额外查询。{@code saveAndFlush} 让登录名唯一约束
   * 和部门外键冲突当场暴露并翻译为 {@code DATA_CONFLICT}，当前事务随之标记为回滚。
   */
  public Account save(Account v) {
    UserAccount e =
        v.getId() == null
            ? new UserAccount()
            : data.findById(v.getId())
                .orElseThrow(
                    () ->
                        new com.cdwy.permission.service.port.error.ResourceNotFoundException(
                            "USER_NOT_FOUND", "用户不存在"));
    CloneFactory.copy(e, v);
    e.setPersonType(com.cdwy.permission.service.port.data.PersonType.valueOf(v.getKind()));
    e.setDepartment(
        v.getDepartmentId() == null ? null : departments.getReferenceById(v.getDepartmentId()));
    try {
      return model(data.saveAndFlush(e));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("DATA_CONFLICT", "数据重复或关联约束冲突，请刷新后重试");
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
      throw new ConflictException("DATA_CONFLICT", "数据重复或关联约束冲突，请刷新后重试");
    }
  }

  /**
   * {@code deleteById} 先查后删，记录不存在时静默返回；不立即刷新，未解除的用户角色等造成的外键冲突
   * 要到后续刷新（通常是事务提交）时才抛出，不经本适配器翻译。
   */
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
      String keyword, int page, int pageSize) {
    org.springframework.data.domain.Page<UserAccount> rows =
        data.search(
            com.cdwy.permission.repository.LikePattern.of(keyword),
            org.springframework.data.domain.PageRequest.of(
                page - 1, pageSize, org.springframework.data.domain.Sort.by("id")));
    return new com.cdwy.permission.service.port.data.UserData.Page<>(
        page, pageSize, rows.getTotalElements(), rows.map(this::model).getContent());
  }

  /** 部门成员数据库分页。 */
  public com.cdwy.permission.service.port.data.UserData.Page<Account> members(
      Long departmentId, String keyword, int page, int pageSize) {
    org.springframework.data.domain.Page<UserAccount> rows =
        data.members(
            departmentId,
            com.cdwy.permission.repository.LikePattern.of(keyword),
            org.springframework.data.domain.PageRequest.of(
                page - 1, pageSize, org.springframework.data.domain.Sort.by("username")));
    return new com.cdwy.permission.service.port.data.UserData.Page<>(
        page, pageSize, rows.getTotalElements(), rows.map(this::model).getContent());
  }

  /** 部门删除的反向引用检查。 */
  public boolean inDepartment(Long id) {
    return data.existsByDepartment_Id(id);
  }
}
