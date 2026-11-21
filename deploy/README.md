# Docker Swarm 部署文件

完整说明见 [docs/second-review/05-deployment.adoc](../docs/second-review/05-deployment.adoc)。当前部署（2026-11-21）：

- 统一权限管理系统：http://120.46.213.158/ui/index.html （stack `cdwy`，CDWY3）
- 课程项目管理系统：http://124.70.102.248/ （stack `course-project`，CDWY4，老师仓库 master `e9a6dff`，未改老师代码）

| 文件 | 用途 |
|---|---|
| `stack.yml`、`.env.example` | 权限系统 stack：`rbac-backend`、`rbac-frontend`，接入外部网络 `javaee-proxy` |
| `permission/Dockerfile.backend`、`permission/Dockerfile.frontend`、`permission/nginx/default.conf` | 权限系统镜像与 Nginx 反向代理 |
| `permission/package-images.sh` | 本机编译并打包权限系统的镜像构建上下文，到服务器上构建 |
| `course/stack.yml`、`course/env.example` | 课程系统 stack：由老师的 `deploy/stack.yml` 改写，数据库密码改用 secret，统一身份指向权限系统 |
| `course/package-images.sh` | 按老师部署文档编译课程系统，打包老师的 Dockerfile 与产物，到服务器上构建 |

服务器是 x86_64，SWR 上的基础镜像只有 amd64 版本，所以本机只编译，镜像在服务所在节点上构建：

```bash
deploy/permission/package-images.sh                                           # build/rbac-images-<提交号>.tar.gz → 权限系统节点
deploy/course/package-images.sh ../course-project-mgt http://120.46.213.158  # build/course-images-<提交号>.tar.gz → 课程节点
# 在各节点：tar xzf <包> && <包目录>/build-images.sh
```

在管理节点部署（`.env` 里只有镜像名、端口和地址；密码在 docker secret 里）：

```bash
cd /root/ooad-deploy/rbac   && (set -a; . ./.env; set +a; docker stack deploy --resolve-image never -c stack.yml cdwy)
cd /root/ooad-deploy/course && (set -a; . ./.env; set +a; docker stack deploy --resolve-image never -c stack.yml course-project)
```

登记课程系统并验收（密码只从环境变量读取）：

```bash
RBAC_URL=http://120.46.213.158 RBAC_ADMIN=admin RBAC_ADMIN_PASSWORD=... DEMO_PASSWORD=... \
  EMPLOYEE_XLSX=市政公司员工信息表.xlsx python3 scripts/register-course-app.py
RBAC_URL=http://120.46.213.158 COURSE_URL=http://124.70.102.248/api COURSE_ORIGIN=http://124.70.102.248 \
  RBAC_ADMIN=admin RBAC_ADMIN_PASSWORD=... DEMO_PASSWORD=... python3 scripts/verify-course-integration.py
```
