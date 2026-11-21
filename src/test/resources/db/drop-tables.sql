-- 仅供 test profile 使用：重建测试库前删除全部业务表，随后执行部署用建表脚本。
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS sys_audit_log, sys_role_hierarchy, permission_api, sys_role_permission, sys_user_role,
    api_resource, sys_permission, biz_system, sys_role, sys_account, sys_user, sys_department;
SET FOREIGN_KEY_CHECKS = 1;
