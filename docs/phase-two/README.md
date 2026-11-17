# RBAC1 与前端增强

本轮在现有面向对象方案上补齐角色继承，并重做前端管理与个人工作区。数据库、API、需求、类图、顺序图及验证材料同步更新。10 组业务用例及角色图算法均完成结构化／面向对象双实现，按启动配置切换，技术基础设施共享。

## 变更

| 项目 | 原版本 | 当前版本 |
|---|---|---|
| 授权模型 | RBAC0，多角色权限并集 | RBAC1，多级/多继承与可解释来源 |
| 数据结构 | 10 表、12 外键 | 11 表、14 外键，新增 sys_role_hierarchy |
| HTTP 操作 | 47 | 56 |
| Spring Service | 10 | 24 个定义，每模式装载 13 个 |
| 后端验收与领域测试 | 21 | 81 |
| 前端 | 基础表格与授权页 | 工作台、资源目录、继承配置、三步授权、个人空间 |

新增 API：查询继承图、建立/解除继承、角色有效权限、管理员查用户来源、本人来源。详细字段见 [OpenAPI](../first-review/openapi.json)。

## 数据库升级

新空库使用[完整 DDL](../first-review/database-mysql.sql)。已经运行**第一次检查整改十表模型**的 MySQL 库，可在备份并核对 schema 后审核[增量 SQL](migrate-rbac0-to-rbac1.sql)，增加继承关联表；默认没有继承边，因此原有直接授权语义不变。

该增量脚本不适用于归档的 25/38 表模型，不能据此转换历史业务数据。本次未连接真实 MySQL，也未对任何业务库执行脚本。prod 继续 `validate`，不依赖 Hibernate update 自动改表。脚本带双外键、组合唯一、自继承 CHECK；多级环仍需服务事务检查，CHECK 不能验证全图无环。

## 验收入口

- [前端操作与演示](../first-review/demo.md)
- [面向对象设计](object-oriented-design.md)
- [交互说明](frontend-interaction.md)
- [后端与浏览器验证](../first-review/testing.md)
- [图与页面截图](../design/README.md)

默认运行是 H2 内存演示；其并发与 DDL 兼容测试不能替代真实 MySQL、跨实例并发、生产压测或服务器部署验收。

## 双实现与真实三表

已新增[双实现和导入说明](dual-design-and-import.md)、[来源字段迁移](migrate-company-source-fields.sql)，及管理员“数据导入”页面。全部十组业务端口可在 structured / ddd 间切换，前端、HTTP、数据库与技术适配器共享。

[完整双设计与验收说明](full-dual-design.md)。
