#!/usr/bin/env bash
# 打包权限系统镜像的构建上下文：本机编译后端 JAR 和前端，连同 Dockerfile、Nginx 配置打成 tar.gz。
# 服务器是 x86_64，基础镜像（SWR oomall-javaee）只有 amd64 版本，所以镜像在服务器上构建：
#   scp build/rbac-images-<提交号>.tar.gz root@<权限系统节点>:/root/ooad-deploy/
#   在该节点：cd /root/ooad-deploy && tar xzf rbac-images-<提交号>.tar.gz && rbac-images-<提交号>/build-images.sh
#
# 用法：deploy/permission/package-images.sh [输出目录，默认 build]
# 环境变量：SKIP_TESTS=1 打包时跳过测试；MVN_ARGS 传给 Maven（如 -Dmaven.repo.local=...）
set -euo pipefail
cd "$(dirname "$0")/../.."

OUT_DIR="${1:-build}"
TAG="$(git rev-parse --short=7 HEAD)"
if [ -n "$(git status --porcelain -- src frontend/src frontend/package.json pom.xml)" ]; then
  echo "src、frontend 或 pom.xml 有未提交的改动，镜像标签 ${TAG} 会对不上源码" >&2
  exit 1
fi
read -r -a mvn_args <<< "${MVN_ARGS:-}"

mvn -B -q "${mvn_args[@]}" -f third-party/clonefactory/pom.xml install -DskipTests
(cd frontend && npm ci --no-audit --no-fund && npm run build)
if [ "${SKIP_TESTS:-0}" = 1 ]; then
  mvn -B -q "${mvn_args[@]}" clean package -DskipTests
else
  mvn -B -q "${mvn_args[@]}" clean verify
fi

NAME="rbac-images-${TAG}"
STAGE="$(mktemp -d)"
trap 'rm -rf "${STAGE}"' EXIT
CTX="${STAGE}/${NAME}"
mkdir -p "${CTX}/target" "${CTX}/src/main/resources/static" "${CTX}/deploy/permission"
cp target/nified-permission-system-*.jar "${CTX}/target/"
cp -R src/main/resources/static/ui "${CTX}/src/main/resources/static/ui"
cp -R deploy/permission/Dockerfile.backend deploy/permission/Dockerfile.frontend deploy/permission/nginx \
  "${CTX}/deploy/permission/"
cat > "${CTX}/build-images.sh" <<EOF
#!/bin/sh
# 在服务器上构建权限系统镜像（源码提交 ${TAG}）
set -eu
cd "\$(dirname "\$0")"
docker build -f deploy/permission/Dockerfile.backend -t cdwy/rbac-backend:${TAG} .
docker build -f deploy/permission/Dockerfile.frontend -t cdwy/rbac-frontend:${TAG} .
echo "RBAC_BACKEND_IMAGE=cdwy/rbac-backend:${TAG}"
echo "RBAC_FRONTEND_IMAGE=cdwy/rbac-frontend:${TAG}"
EOF
chmod +x "${CTX}/build-images.sh"
mkdir -p "${OUT_DIR}"
# 不带 macOS 扩展属性，免得服务器上的 GNU tar 解包时逐个告警
COPYFILE_DISABLE=1 tar --no-xattrs -czf "${OUT_DIR}/${NAME}.tar.gz" -C "${STAGE}" "${NAME}"
echo "已打包 ${OUT_DIR}/${NAME}.tar.gz"
