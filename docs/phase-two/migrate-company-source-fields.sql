-- 仅适用于已完成 RBAC1 的当前11表 MySQL 模型。先备份并确认列尚不存在。
-- 本轮只提供迁移脚本，没有连接或修改真实 MySQL。
ALTER TABLE sys_department ADD COLUMN manager_name VARCHAR(128) NULL;
ALTER TABLE sys_user ADD COLUMN company_name VARCHAR(128) NULL,
 ADD COLUMN source_department VARCHAR(128) NULL,
 ADD COLUMN job_title VARCHAR(128) NULL, ADD COLUMN phone VARCHAR(32) NULL;
ALTER TABLE sys_permission ADD COLUMN source_description VARCHAR(2000) NULL,
 ADD COLUMN source_grants VARCHAR(2000) NULL;
