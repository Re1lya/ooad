package com.cdwy.permission.service.port.user;

import com.cdwy.permission.service.port.BackendUseCase;
import com.cdwy.permission.service.port.data.*;

/**
 * 用户（人员账号）用例：人员资料、人员类型与有效期、启停、密码和删除；角色分配见 {@code AuthorizationUseCase}。
 *
 * <p>修改、启停、设置密码和删除都先对账号加写锁，同一账号的并发写入串行执行；启停与设置密码会递增凭证版本，使已签发的
 * 令牌失效。结果不含密码散列和凭证版本。
 */
public interface UserUseCase extends BackendUseCase {
  /** 人员详情；不存在抛 {@code USER_NOT_FOUND}（404）。 */
  UserResult get(Long id);

  /** 按登录名（工号、学号）或姓名模糊查询，数据库分页，页码从 1 开始；关键字为空时不过滤。 */
  UserData.Page<UserResult> list(int page, int pageSize, String keyword);

  /**
   * 新建人员，返回含新标识的结果：登录名已存在抛 {@code USERNAME_EXISTS}（409，含停用和过期账号）；密码不符合项目密码
   * 规则抛 {@code PASSWORD_POLICY_VIOLATION}（400）；部门非空时须存在（{@code DEPARTMENT_NOT_FOUND}，404）；正式员工不能
   * 设有效期，外部、临时人员必须设且在项目时区的今天至各自上限天数之间（{@code ACCESS_WINDOW_INVALID}，400），截止日当天
   * 仍可用。新账号启用、非管理员、凭证版本为 0、没有角色；不写审计日志。
   */
  UserResult create(CreateUserCommand input);

  /**
   * 修改姓名、部门、人员类型与有效期，不改登录名、密码、启用状态及分公司、岗位、电话等员工信息同步字段；不存在抛
   * {@code USER_NOT_FOUND}（404）。管理员只能是不设有效期的正式员工，否则抛 {@code FORBIDDEN}（403）；部门与有效期规则同
   * 新建。凭证版本不变：鉴权每次请求都检查有效期，新期限对已签发令牌立即生效。不写审计日志。管理员限制与部门不存在同时
   * 出现时，结构化实现先查前者返回 403，DDD 实现先查后者返回 404。
   */
  UserResult update(Long id, UserData.Update input);

  /**
   * 启用或停用账号，{@code actor} 为当前操作人：不存在抛 {@code USER_NOT_FOUND}（404）；不能停用本人或管理员
   * （{@code FORBIDDEN}，403），启用不受此限。状态确有变化时凭证版本加一，此前签发的令牌全部失效，重新启用也不恢复旧令牌；
   * 重复提交同一状态幂等，不改版本。返回更新后的资料，不写审计日志。
   */
  UserResult status(Long actor, Long id, boolean enabled);

  /**
   * 管理员重置密码（本人改密见 {@code AuthenticationUseCase#changePassword}）：先校验项目密码规则
   * （{@code PASSWORD_POLICY_VIOLATION}，400），再读取账号（{@code USER_NOT_FOUND}，404）；凭证版本加一，该账号已签发的
   * 令牌全部失效。不改启用状态，员工信息同步新建的停用人员设置密码后还须另行启用；不写审计日志。
   */
  void password(Long id, String password);

  /**
   * 删除账号，{@code actor} 为当前操作人：不存在抛 {@code USER_NOT_FOUND}（404）；不能删除本人或管理员（{@code FORBIDDEN}，
   * 403）。同一事务内先删除其全部角色分配再删账号；其令牌在下一次请求时因账号不存在而失效，审计记录中的操作人快照保留。
   * 不写审计日志。
   */
  void delete(Long actor, Long id);
}
