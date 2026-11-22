# 课程项目管理系统接入：本地端到端证据

2026-11-21 在本机完成。两套系统都用 MySQL 8.4，权限系统按 `docs/first-review/database-mysql.sql` 建库并以 `ddl-auto=validate` 启动；课程系统用老师仓库 master（e9a6dff）的后端、前端构建和课程库 dump，未改老师代码。课程前端构建时 `VITE_IDENTITY_BASE_URL` 指向 `<权限系统>/api/apps/course-project-management`，权限系统的 CORS 白名单只放行课程前端的源地址。

每种路线都从空库开始，依次运行：

1. `scripts/register-course-app.py`：登记应用、菜单与按钮、API 资源（含 operationId）、角色与继承；同步老师的员工信息表（一万人）；启用四个演示工号。
2. `scripts/verify-course-integration.py`：42 项接口验收，课程接口经课程前端的 `/api` 代理调用，与浏览器路径一致。
3. `frontend/tests/course-walkthrough.cjs`：用 Chrome 实际操作课程前端和权限控制台，逐步截图。

| 文件 | 内容 |
|---|---|
| `register-course-app.txt` | 登记与员工同步的输出 |
| `verify-structured.txt`、`verify-ddd.txt` | 两个系统都用结构化路线、都用 DDD 路线时的验收结果，各 42/42 |
| `course-backend-tests-mysql.txt` | 老师课程后端自带的 495 个测试连 MySQL 的结果 |
| `01`–`10` | 课程前端：课程管理员的菜单、两级部门选人添加教师、课程日志、撤销与恢复菜单 |
| `11`–`13` | 权限控制台：薛峰的有效权限及继承路径、两个应用分开的审计日志 |

演示工号：SG000001 薛峰（课程管理员）、SG000002 严天信（课程督导）、SG000003 秦哲（教师）、SG000191 曾禄（学生）。角色继承为“课程管理员 → 课程督导 → 教师”。

日志、输出和截图中没有令牌和密码；账号密码只从环境变量读取。
