# Docker Swarm 部署证据（2026-11-21）

当前部署的权限系统镜像为 `b56e81c`：在按课程代码规范整改的 `5c58665` 之上，增加了“内置应用、管理员角色、菜单按钮与管理接口不能停用或删除”的保护（滚动更新）。以下状态、日志、验收输出和截图均在更新后重新采集。

部署方式见 [05-deployment.adoc](../../05-deployment.adoc)。验收都从集群外部经公网地址执行：权限系统 http://120.46.213.158 ，课程系统 http://124.70.102.248 。

| 文件 | 内容 |
|---|---|
| `swarm-services.txt` | `docker stack ls`、两个 stack 的服务，以及 4 个任务所在节点与状态 |
| `health.txt` | 4 个容器的健康检查状态 |
| `register-course-app.txt` | 登记课程系统、同步一万名员工（约 20 秒）、启用演示工号的输出 |
| `verify-public.txt` | 接口验收脚本，42/42 通过 |
| `menu-switch-log.txt` | 浏览器走查在权限控制台依次进入“工作台”（`overview`）、“有效权限”（`effective`）、“审计日志”（`audit`）时，`docker service logs cdwy_rbac-backend` 中带 `[menu=...]` 标记的请求日志，含 Controller、Service 两层（时间为 UTC） |
| `01-course-admin-menu.png` | 课程管理员从课程系统教师入口登录后的“管理”菜单 |
| `06-picker-selected.png` | 添加教师：从权限系统的组织架构逐级选到人员 |
| `09-menu-revoked.png` | 在权限系统撤销“课程督导”的“课程管理”后，课程管理员侧栏中的“课程”变灰 |
| `13-rbac-audit-course-app.png` | 权限控制台按应用查看课程系统写入的审计记录 |

另外，浏览器走查脚本 `frontend/tests/course-walkthrough.cjs` 在部署环境上完整跑通（13 步）。日志、输出和截图中没有令牌和密码。课程前端 Nginx 的访问日志含访问者 IP，没有放进来。
