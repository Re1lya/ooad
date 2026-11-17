package com.cdwy.permission.entity.organization;

import jakarta.persistence.*;

@cn.edu.xmu.clonefactory.CopyTo(com.cdwy.permission.service.ddd.domain.model.DepartmentNode.class)
@cn.edu.xmu.clonefactory.CopyFrom(com.cdwy.permission.service.ddd.domain.model.DepartmentNode.class)
@Entity
@Table(name = "sys_department")
public class Department {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 128)
  private String name;

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
