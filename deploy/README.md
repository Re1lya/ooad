# Docker Swarm 部署文件

完整步骤见 [docs/second-review/05-deployment.adoc](../docs/second-review/05-deployment.adoc)。

| 文件 | 用途 |
|---|---|
| `stack.yml` | 一个 stack 四个服务：`rbac-frontend`、`rbac-backend`、`course-frontend`、`course-backend` |
| `.env.example` | 镜像名、端口、数据库地址、设计路线等变量（密码与密钥用 docker secret） |
| `permission/Dockerfile.backend`、`permission/Dockerfile.frontend`、`permission/nginx/default.conf` | 权限系统镜像与 Nginx 反向代理 |
| `course/build-course-images.sh` | 克隆老师课程仓库、固定 `ca90f1c`、打补丁并构建课程系统前后端镜像 |
| `course/course-project-mgt-identity.patch` | 课程系统接入统一身份的最小改动：登录页、路由守卫、Nginx `/api` 与登录转发、回移老师的两处启动修复 |

快速顺序：

```bash
PLATFORM=linux/amd64 scripts/build-permission-images.sh
PLATFORM=linux/amd64 deploy/course/build-course-images.sh ../course-build
cp deploy/.env.example deploy/.env   # 修改后
set -a; . deploy/.env; set +a
docker stack deploy --with-registry-auth -c deploy/stack.yml cdwy
RBAC_URL=http://<节点>:18080 python3 scripts/register-course-app.py
```
