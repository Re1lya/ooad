package com.cdwy.permission.entity.organization;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;
import com.cdwy.permission.service.ddd.domain.model.DepartmentNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 部门（表 {@code sys_department}），以 {@code parent} 组成部门树；DDD 路线的持久化适配器按 {@code @CopyTo}/{@code
 * @CopyFrom} 与 {@link DepartmentNode} 互相拷贝。
 */
@CopyTo(DepartmentNode.class)
@CopyFrom(DepartmentNode.class)
@Entity
@Table(
    name = "sys_department",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_department_sibling",
          columnNames = {"parent_id", "name"}),
      @UniqueConstraint(name = "uk_department_code", columnNames = "code")
    })
@Getter
@Setter
public class Department {
  /** 主键由数据库生成：不提供 setter，从领域对象拷回实体时也不覆盖。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  @CopyFrom.Exclude(DepartmentNode.class)
  private Long id;

  /** 同一上级下名称唯一：各分公司都有“综合办公室”“财务部”等同名部门。 */
  @Column(nullable = false, length = 128)
  private String name;

  /** 部门编码，统一身份接口对外提供；全局唯一。 */
  @lombok.Getter
  @lombok.Setter
  @Column(length = 64)
  private String code;

  /** 上级部门；为空表示根部门，员工信息同步时分公司即根部门、部门为其子部门。 */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Department parent;

}
