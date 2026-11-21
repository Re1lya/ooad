#!/usr/bin/env bash
# 打包课程项目管理系统（老师仓库 mingqcn/course-project-mgt）镜像的构建上下文，不改老师的代码。
# 按老师 deploy/README.md 第 5、6 节编译：后端 mvn package，前端 npm run build；
# 产物连同老师的 Dockerfile、Nginx 配置打成 tar.gz，到服务器上构建镜像（基础镜像只有 amd64 版本）：
#   scp build/course-images-<提交号>.tar.gz root@<课程节点>:/root/ooad-deploy/
#   在该节点：cd /root/ooad-deploy && tar xzf course-images-<提交号>.tar.gz && course-images-<提交号>/build-images.sh
#
# 用法：deploy/course/package-images.sh <老师仓库目录> <权限系统公网地址> [输出目录，默认 build]
#   例：deploy/course/package-images.sh ../course-project-mgt http://120.46.213.158
#   前端构建时写入 VITE_IDENTITY_BASE_URL=<权限系统公网地址>/api/apps/course-project-management
# 依赖：JDK 21、Maven、Node 22.22.2 或 24.15+；CloneFactory 0.0.2 已装进 Maven 仓库
#   （gitee mingqcn/spring-fundamentals 的 clonefactory 目录：mvn -f clonefactory/pom.xml install）
# 环境变量：MVN_ARGS 传给 Maven（如 -Dmaven.repo.local=...）
set -euo pipefail

REPO="$(cd "${1:?用法: package-images.sh <老师仓库目录> <权限系统公网地址> [输出目录]}" && pwd)"
PUBLIC_URL="${2:?缺少权限系统公网地址，例如 http://120.46.213.158}"
OUT_DIR="${3:-build}"
mkdir -p "${OUT_DIR}"
OUT_DIR="$(cd "${OUT_DIR}" && pwd)"
IDENTITY_URL="${PUBLIC_URL%/}/api/apps/course-project-management"
TAG="$(git -C "${REPO}" rev-parse --short=7 HEAD)"
if [ -n "$(git -C "${REPO}" status --porcelain -- backend/src backend/pom.xml web/src web/package.json web/nginx)" ]; then
  echo "老师仓库有未提交的改动，镜像标签 ${TAG} 会对不上源码" >&2
  exit 1
fi
read -r -a mvn_args <<< "${MVN_ARGS:-}"

(cd "${REPO}/backend" && mvn -B -q ${mvn_args[@]+"${mvn_args[@]}"} clean package -Dmaven.test.skip=true)
(cd "${REPO}/web" && npm ci --no-audit --no-fund \
  && VITE_API_BASE_URL=/api VITE_IDENTITY_BASE_URL="${IDENTITY_URL}" VITE_MOCK_ENABLED=false npm run build)

# 老师文档 6.3 节的检查：产物不含开发地址和 Mock 凭据；另外确认写入了统一身份地址
if grep -a -l -E "http://localhost:8080|mock-teacher-token|mock-student-token|mock123456" "${REPO}"/web/dist/assets/*.js; then
  echo "前端产物含开发地址或 Mock 信息" >&2
  exit 1
fi
if ! grep -a -q -F "${IDENTITY_URL}" "${REPO}"/web/dist/assets/*.js; then
  echo "前端产物里没有 ${IDENTITY_URL}" >&2
  exit 1
fi

NAME="course-images-${TAG}"
STAGE="$(mktemp -d)"
trap 'rm -rf "${STAGE}"' EXIT
CTX="${STAGE}/${NAME}"
mkdir -p "${CTX}/backend/target" "${CTX}/web"
cp "${REPO}/backend/Dockerfile" "${CTX}/backend/"
cp "${REPO}"/backend/target/course-project-backend-*.jar "${CTX}/backend/target/"
cp -R "${REPO}/web/Dockerfile" "${REPO}/web/nginx" "${REPO}/web/dist" "${CTX}/web/"
cat > "${CTX}/build-images.sh" <<EOF
#!/bin/sh
# 在服务器上按老师的 Dockerfile 构建课程系统镜像（老师仓库提交 ${TAG}；前端统一身份地址 ${IDENTITY_URL}）
set -eu
cd "\$(dirname "\$0")"
docker build -t course-mgt-backend:${TAG} backend
docker build -t course-mgt-frontend:${TAG} web
echo "BACKEND_IMAGE=course-mgt-backend:${TAG}"
echo "FRONTEND_IMAGE=course-mgt-frontend:${TAG}"
EOF
chmod +x "${CTX}/build-images.sh"
# 不带 macOS 扩展属性，免得服务器上的 GNU tar 解包时逐个告警
COPYFILE_DISABLE=1 tar --no-xattrs -czf "${OUT_DIR}/${NAME}.tar.gz" -C "${STAGE}" "${NAME}"
echo "已打包 ${OUT_DIR}/${NAME}.tar.gz"
