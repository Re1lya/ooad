# Docker Swarm 部署文件

完整说明见 [docs/second-review/05-deployment.adoc](../docs/second-review/05-deployment.adoc)。当前部署（2026-11-22 更新）：

- 统一权限管理系统：http://120.46.213.158:8080/ （stack `cdwy`，CDWY3）：首页进权限台账，原管理控制台在 `/ui/`
- 课程项目管理系统：http://124.70.102.248/ （stack `course-project`，CDWY4）：后端用老师发布的镜像 `course-mgt-backend:0.0.1`（11-23 重新发布版，按摘要固定），前端按老师仓库 master `24a4d7d` 编译，未改老师代码
- 测试账号：老师员工信息表中的一万名员工，总公司员工从教师入口登录，分公司员工从学生入口登录，统一使用测试密码（不写进仓库）

| 文件 | 用途 |
|---|---|
| `stack.yml`、`.env.example` | 权限系统 stack：`rbac-backend`、`rbac-frontend`，接入外部网络 `javaee-proxy` |
| `permission/Dockerfile.backend`、`permission/Dockerfile.frontend`、`permission/nginx/default.conf` | 权限系统镜像；Nginx 托管权限台账（`/access-registry/`，首页跳转）与原管理控制台（`/ui/`），并把 `/api/` 转发到后端 |
| `permission/package-images.sh` | 本机构建两个前端、编译后端，打包权限系统的镜像构建上下文，到服务器上构建 |
| `course/stack.yml`、`course/env.example` | 课程系统 stack：由老师的 `deploy/stack.yml` 改写，后端用老师的镜像（按摘要固定），数据库密码改用 secret，统一身份指向权限系统，登录服务日志调到 INFO 以免记下令牌 |
| `course/package-frontend.sh` | 按老师部署文档编译课程前端（构建时写入统一身份地址），打包老师的 Dockerfile 与产物，到课程节点上构建 |

服务器是 x86_64，SWR 上的基础镜像只有 amd64 版本，所以本机只编译，镜像在服务所在节点上构建；课程后端直接拉取老师的公开镜像：

```bash
deploy/permission/package-images.sh                                             # build/rbac-images-<提交号>.tar.gz → 权限系统节点
deploy/course/package-frontend.sh ../course-project-mgt http://120.46.213.158:8080  # build/course-frontend-<提交号>.tar.gz → 课程节点
# 权限系统节点：tar xzf <包> && <包目录>/build-images.sh；课程节点：tar xzf <包> && <包目录>/build-image.sh
# 课程节点：docker pull swr.cn-north-4.myhuaweicloud.com/oomall-javaee/course-mgt-backend:0.0.1
```

在管理节点部署（`.env` 里只有镜像名、端口和地址；密码在 docker secret 里）：

```bash
cd /root/ooad-deploy/rbac   && (set -a; . ./.env; set +a; docker stack deploy --resolve-image never -c stack.yml cdwy)
cd /root/ooad-deploy/course && (set -a; . ./.env; set +a; docker stack deploy --resolve-image never -c stack.yml course-project)
```

登记课程系统、把一万名员工启用为测试账号并验收（密码只从环境变量读取；启用一万个账号约 12 分钟，重复执行只处理尚未启用的）：

```bash
RBAC_URL=http://120.46.213.158:8080 RBAC_ADMIN=admin RBAC_ADMIN_PASSWORD=... DEMO_PASSWORD=... \
  EMPLOYEE_XLSX=市政公司员工信息表.xlsx python3 scripts/register-course-app.py
RBAC_URL=http://120.46.213.158:8080 COURSE_URL=http://124.70.102.248/api COURSE_ORIGIN=http://124.70.102.248 \
  RBAC_ADMIN=admin RBAC_ADMIN_PASSWORD=... DEMO_PASSWORD=... python3 scripts/verify-course-integration.py
```
