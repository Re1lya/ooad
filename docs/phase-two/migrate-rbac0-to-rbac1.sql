-- 仅适用于第一次检查整改十表模型；审核与备份后执行。
-- 本次未在真实 MySQL 执行，不转换归档版本数据。
-- RBAC1：角色继承边，高级角色获得 inherited_role_id 的权限。
CREATE TABLE sys_role_hierarchy (
  id BIGINT NOT NULL AUTO_INCREMENT,
  senior_role_id BIGINT NOT NULL,
  inherited_role_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_hierarchy (senior_role_id, inherited_role_id),
  CONSTRAINT fk_hierarchy_senior FOREIGN KEY (senior_role_id) REFERENCES sys_role(id),
  CONSTRAINT fk_hierarchy_inherited FOREIGN KEY (inherited_role_id) REFERENCES sys_role(id),
  CONSTRAINT ck_hierarchy_distinct CHECK (senior_role_id <> inherited_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
