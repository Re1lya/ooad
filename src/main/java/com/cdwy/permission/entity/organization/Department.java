package com.cdwy.permission.entity.organization;

import jakarta.persistence.*;

@cn.edu.xmu.clonefactory.CopyTo(com.cdwy.permission.service.ddd.domain.model.DepartmentNode.class)
@cn.edu.xmu.clonefactory.CopyFrom(com.cdwy.permission.service.ddd.domain.model.DepartmentNode.class)
@Entity
@Table(
    name = "sys_department",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_department_sibling",
          columnNames = {"parent_id", "name"}),
      @UniqueConstraint(name = "uk_department_code", columnNames = "code")
    })
public class Department {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 同一上级下名称唯一：各分公司都有“综合办公室”“财务部”等同名部门。 */
  @Column(nullable = false, length = 128)
  private String name;

  /** 部门编码，统一身份接口对外提供；全局唯一。 */
  @lombok.Getter
  @lombok.Setter
  @Column(length = 64)
  private String code;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Department parent;

  public Department() {}

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }

  public Department getParent() {
    return parent;
  }

  public void setParent(Department value) {
    parent = value;
  }

  /** 导入表中的原始来源字段，普通编辑不隐式覆盖。 */
  @lombok.Getter
  @lombok.Setter
  @Column(length = 128)
  private String managerName;
}
