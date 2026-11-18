#!/usr/bin/env bash
# 构建权限系统前后端镜像：先 mvn clean verify（含测试与 JaCoCo）和前端构建，再打两个镜像。
# 用法：scripts/build-permission-images.sh [后端镜像名] [前端镜像名]
# 跨架构：PLATFORM=linux/amd64 scripts/build-permission-images.sh
set -euo pipefail
cd "$(dirname "$0")/.."

BACKEND_IMAGE="${1:-cdwy/rbac-backend:second-review}"
FRONTEND_IMAGE="${2:-cdwy/rbac-frontend:second-review}"
PLATFORM="${PLATFORM:-}"
MVN="${MVN:-mvn}"

"${MVN}" -B -q -f third-party/clonefactory/pom.xml install -DskipTests
(cd frontend && npm ci --no-audit --no-fund && npm run build)
"${MVN}" -B clean verify

platform_args=()
if [ -n "${PLATFORM}" ]; then platform_args=(--platform "${PLATFORM}"); fi
docker build "${platform_args[@]}" -f deploy/permission/Dockerfile.backend -t "${BACKEND_IMAGE}" .
docker build "${platform_args[@]}" -f deploy/permission/Dockerfile.frontend -t "${FRONTEND_IMAGE}" .

echo "权限系统后端镜像：${BACKEND_IMAGE}"
echo "权限系统前端镜像：${FRONTEND_IMAGE}"
echo "JaCoCo 报告：target/site/jacoco/index.html"
