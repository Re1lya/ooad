package com.cdwy.permission.service.port.data;

/**
 * 人员类型，决定账号是否限期。请求 DTO、VO 与结构化实现的实体使用本枚举；用例数据以枚举名称字符串传递，DDD 领域模型使用同名字符串常量。
 *
 * <p>限期人员的有效期按项目时区计算：截止日当天仍可用，次日零点起失效。
 */
public enum PersonType {
  /** 正式员工：不设有效期；管理员只能是此类型，员工信息同步新建的人员也是此类型。 */
  EMPLOYEE,
  /** 外部人员：必须设置有效期，最长 {@code permission.policy.external-max-days} 天（默认 365）。 */
  EXTERNAL,
  /** 临时人员：必须设置有效期，最长 {@code permission.policy.temporary-max-days} 天（默认 90）。 */
  TEMPORARY
}
