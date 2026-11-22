#!/usr/bin/env bash
# 编译课程项目管理系统（老师仓库 mingqcn/course-project-mgt）的前端，不改老师的代码。
# 老师的部署说明要求前端由各组自己编译打包（统一身份地址在构建时写入），后端直接用老师发布的镜像
# （deploy/course/env.example 的 BACKEND_IMAGE），所以这里只处理前端。
# 按老师 deploy/README.md 第 6 节编译：npm ci、npm run build；产物连同老师的 Dockerfile、Nginx 配置
# 打成 tar.gz，到课程节点上构建镜像（基础镜像只有 amd64 版本）：
#   scp build/course-frontend-<提交号>.tar.gz root@<课程节点>:/root/ooad-deploy/
#   在该节点：cd /root/ooad-deploy && tar xzf course-frontend-<提交号>.tar.gz && course-frontend-<提交号>/build-image.sh
#
# 用法：deploy/course/package-frontend.sh <老师仓库目录> <权限系统公网地址> [输出目录，默认 build]
#   例：deploy/course/package-frontend.sh ../course-project-mgt http://120.46.213.158
#   前端构建时写入 VITE_IDENTITY_BASE_URL=<权限系统公网地址>/api/apps/course-project-management
# 依赖：Node 22.22.2 或 24.15+
set -euo pipefail

REPO="$(cd "${1:?用法: package-frontend.sh <老师仓库目录> <权限系统公网地址> [输出目录]}" && pwd)"
PUBLIC_URL="${2:?缺少权限系统公网地址，例如 http://120.46.213.158}"
OUT_DIR="${3:-build}"
mkdir -p "${OUT_DIR}"
OUT_DIR="$(cd "${OUT_DIR}" && pwd)"
IDENTITY_URL="${PUBLIC_URL%/}/api/apps/course-project-management"
TAG="$(git -C "${REPO}" rev-parse --short=7 HEAD)"
if [ -n "$(git -C "${REPO}" status --porcelain -- web)" ]; then
  echo "老师仓库的 web 目录有未提交的改动，镜像标签 ${TAG} 会对不上源码" >&2
  exit 1
fi

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

NAME="course-frontend-${TAG}"
STAGE="$(mktemp -d)"
trap 'rm -rf "${STAGE}"' EXIT
CTX="${STAGE}/${NAME}"
mkdir -p "${CTX}/web"
cp -R "${REPO}/web/Dockerfile" "${REPO}/web/nginx" "${REPO}/web/dist" "${CTX}/web/"
cat > "${CTX}/build-image.sh" <<SCRIPT
#!/bin/sh
# 在课程节点上按老师的 Dockerfile 构建课程前端镜像（老师仓库提交 ${TAG}；统一身份地址 ${IDENTITY_URL}）
set -eu
cd "\$(dirname "\$0")"
docker build -t course-mgt-frontend:${TAG} web
echo "FRONTEND_IMAGE=course-mgt-frontend:${TAG}"
SCRIPT
chmod +x "${CTX}/build-image.sh"
# 不带 macOS 扩展属性，免得服务器上的 GNU tar 解包时逐个告警
COPYFILE_DISABLE=1 tar --no-xattrs -czf "${OUT_DIR}/${NAME}.tar.gz" -C "${STAGE}" "${NAME}"
echo "已打包 ${OUT_DIR}/${NAME}.tar.gz"
