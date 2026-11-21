-- 第二次检查增量迁移：在已按第一次检查 DDL 建好的库上执行（新库直接执行 docs/first-review/database-mysql.sql）。
-- 需要具有 ALTER / CREATE 权限的账号（应用账号只有 SELECT/INSERT/UPDATE/DELETE）。

-- 1) API 资源增加可选的后端权限码（课程系统 TC、CV 等），同一权限码可覆盖多个接口，因此不建唯一键。
ALTER TABLE api_resource ADD COLUMN code VARCHAR(64) NULL AFTER name;

-- 2) 审计日志技术表：接入应用写入的关键操作与本系统角色继承变更；不进入核心 ER，不设外键。
CREATE TABLE sys_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  system_code VARCHAR(64) NOT NULL,
  request_id VARCHAR(64) NULL,
  occurred_at DATETIME(6) NOT NULL,
  actor_id BIGINT NULL,
  actor_name VARCHAR(128) NULL,
  actor_roles VARCHAR(255) NULL,
  type VARCHAR(64) NOT NULL,
  object_type VARCHAR(64) NULL,
  object_id VARCHAR(128) NULL,
  target VARCHAR(255) NULL,
  before_json VARCHAR(4000) NULL,
  after_json VARCHAR(4000) NULL,
  term_id VARCHAR(128) NULL,
  course_id VARCHAR(128) NULL,
  class_id VARCHAR(128) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_audit_request UNIQUE (system_code,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- 3) 统一身份接口：部门编码（对外提供，全局唯一）；部门名称改为同一上级下唯一（各分公司都有“综合办公室”“财务部”等同名部门）。
--    既有部门按 D + 四位标识补编码。MySQL 的唯一键允许多个 NULL，根部门同名由应用层拒绝。
ALTER TABLE sys_department ADD COLUMN code VARCHAR(64) NULL AFTER name;
UPDATE sys_department SET code = CONCAT('D', LPAD(id, 4, '0')) WHERE code IS NULL;
ALTER TABLE sys_department DROP INDEX uk_department_name;
ALTER TABLE sys_department ADD CONSTRAINT uk_department_sibling UNIQUE (parent_id,name);
ALTER TABLE sys_department ADD CONSTRAINT uk_department_code UNIQUE (code);

-- 4) API 资源可登记接入应用 OpenAPI 的 operationId（同一应用内唯一），供“能否调用指定接口”的查询。
ALTER TABLE api_resource ADD COLUMN operation_id VARCHAR(128) NULL AFTER code;
ALTER TABLE api_resource ADD CONSTRAINT uk_api_operation UNIQUE (system_id,operation_id);

-- 5) 公司三表导入改为员工信息同步：部门经理与功能权限模板的原始文本不再由任何功能写入，删除这些列。
ALTER TABLE sys_department DROP COLUMN manager_name;
ALTER TABLE sys_permission DROP COLUMN source_description;
ALTER TABLE sys_permission DROP COLUMN source_grants;
