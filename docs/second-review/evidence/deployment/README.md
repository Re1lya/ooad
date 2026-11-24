# Docker Swarm 部署证据（2026-11-21 部署，2026-11-22、11-24 更新）

当前部署的权限系统镜像为 `39ba5ec`：在 `b56e81c`（按课程代码规范整改，并保护内置应用、管理员角色、菜单按钮与管理接口不被停用或删除）之上，
并入同组重构的前端“权限台账”并补齐缺失的功能。首页 http://120.46.213.158/ 进入权限台账，原管理控制台仍在 `/ui/`；后端代码与 `b56e81c` 相同。

2026-11-22 按老师的部署通知更新了课程系统：

* 后端改用老师发布的镜像 `swr.cn-north-4.myhuaweicloud.com/oomall-javaee/course-mgt-backend:0.0.1`，按摘要 `sha256:ed56582e…` 固定，与老师仓库 master `990cea2` 是同一份代码。
* 前端 `course-mgt-frontend:990cea2` 由我们按老师仓库编译。
* 员工信息表的一万名员工都启用为测试账号：总公司员工是教师，分公司员工是学生。

2026-11-24 跟进老师 11-23 重新发布的课程后端（同一标签 `0.0.1`，摘要改为 `sha256:88a476df…`，对应老师仓库 master `24a4d7d`）：新版以权限码作为 operationId 调 `/auth/api-permissions/{operationId}`，我们把课程系统 API 资源的 operationId 改为权限码后换上新镜像，重跑了接口验收（`verify-public.txt`，46/46）。其余文件仍是 11-22 采集的。

2026-11-22 晚权限台账上线后，重新采集了 Swarm 状态、健康检查、接口验收、菜单日志和走查截图；登记输出、账号核对与课程后端的调用日志是当天上午课程系统更新时采集的，权限台账上线不影响它们。

部署方式见 [05-deployment.adoc](../../05-deployment.adoc)。验收都从集群外部经公网地址执行：权限系统 http://120.46.213.158 ，课程系统 http://124.70.102.248 。

| 文件 | 内容 |
|---|---|
| `swarm-services.txt` | `docker stack ls`、两个 stack 的服务，以及 4 个任务所在节点与状态 |
| `health.txt` | 4 个容器的健康检查状态 |
| `register-course-app.txt` | 11-21 首次登记课程系统、同步一万名员工（约 20 秒）、启用演示工号的输出 |
| `register-course-app-20261122.txt` | 11-22 重跑登记脚本：应用、角色、部门和人员都沿用已有的，为尚未启用的 9996 名员工设置测试密码并启用（约 12 分钟） |
| `test-accounts.txt` | 两个库的核对：一万名员工全部启用；总公司 190 人都有 `teacher`，分公司 9810 人都有 `student`；课程系统的教师、学生名单与之对应 |
| `verify-public.txt` | 接口验收脚本（11-24 换新镜像后），46/46 通过 |
| `course-identity-calls-log.txt` | 课程后端（老师镜像）调用统一身份的 DEBUG 日志：一次登录（经 `rbac-backend` 调 `/auth/login`、`/auth/me`），一次添加教师（先用当前令牌调 `/auth/me`，再调 `/auth/users/{userId}` 取姓名和工号）；日志里没有令牌 |
| `menu-switch-log.txt` | 浏览器走查在权限台账依次进入“总览”（`overview`）、“权限来源”（`effective`）、“审计日志”（`audit`）时，`docker service logs cdwy_rbac-backend` 中带 `[menu=...]` 标记的请求日志，含 Controller、Service 两层（时间为 UTC） |
| `01-course-admin-menu.png` | 课程管理员从课程系统教师入口登录后的“管理”菜单 |
| `06-picker-selected.png` | 添加教师：从权限系统的组织架构逐级选到人员 |
| `09-menu-revoked.png` | 在权限系统撤销“课程督导”的“课程管理”后，课程管理员侧栏中的“课程”变灰 |
| `11-rbac-effective-permissions.png` | 权限台账按人员查看薛峰的有效权限：直接持有的按钮与经“课程管理员 → 课程督导”继承的菜单 |
| `13-rbac-audit-course-app.png` | 权限台账按应用查看课程系统写入的审计记录 |

另外，浏览器走查脚本 `frontend/tests/course-walkthrough.cjs` 在部署环境上完整跑通（13 步）。日志、输出和截图中没有令牌和密码。课程前端 Nginx 的访问日志含访问者 IP，没有放进来。
