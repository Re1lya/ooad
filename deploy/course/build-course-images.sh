#!/usr/bin/env bash
# 构建课程项目管理系统（老师仓库 mingqcn/course-project-mgt）的前后端镜像，并接入 Cdwy 权限系统。
#
# 只做两件事：在固定提交上打 course-project-mgt-identity.patch（统一身份登录页 + nginx 转发），
# 然后按老师仓库自带的 Dockerfile 构建镜像。课程系统业务代码不改动。
#
# 固定在 ca90f1c（"add mock"）：2026-11-18 核对时老师仓库 master 4a721ba 引用了未提交的
# GroupAuditRecorder、MilestoneAuditRecorder、ScoreAuditRecorder，后端无法编译。
# 老师修复后可设置 COURSE_COMMIT 并重新生成补丁。
#
# 用法：deploy/course/build-course-images.sh <工作目录> [后端镜像名] [前端镜像名]
# 依赖：git、JDK 21 + Maven（本地已安装课程要求的 CloneFactory 0.0.2）、Node 22/24、docker
# 跨架构构建（如 Apple 芯片构建 x86 服务器镜像）：PLATFORM=linux/amd64 deploy/course/build-course-images.sh ...
set -euo pipefail

WORK_DIR="${1:?用法: build-course-images.sh <工作目录> [后端镜像名] [前端镜像名]}"
BACKEND_IMAGE="${2:-cdwy/course-backend:second-review}"
FRONTEND_IMAGE="${3:-cdwy/course-frontend:second-review}"
COURSE_REPO="${COURSE_REPO:-https://gitee.com/mingqcn/course-project-mgt.git}"
COURSE_COMMIT="${COURSE_COMMIT:-ca90f1cb7faa7d73b71b93a125460513f46b730e}"
PLATFORM="${PLATFORM:-}"
PATCH_FILE="$(cd "$(dirname "$0")" && pwd)/course-project-mgt-identity.patch"

mkdir -p "${WORK_DIR}"
if [ ! -d "${WORK_DIR}/course-project-mgt/.git" ]; then
  git clone "${COURSE_REPO}" "${WORK_DIR}/course-project-mgt"
fi
cd "${WORK_DIR}/course-project-mgt"
git fetch --quiet origin
git checkout --quiet --force "${COURSE_COMMIT}"
git clean -fdq web/src
git apply --whitespace=nowarn "${PATCH_FILE}"
echo "已在 ${COURSE_COMMIT} 上应用统一身份接入补丁"

platform_args=()
if [ -n "${PLATFORM}" ]; then platform_args=(--platform "${PLATFORM}"); fi

# 课程后端依赖课程提供的 CloneFactory（pom 中为 0.0.2）；本机只有仓库附带的 0.0.1-SNAPSHOT 时：
#   mvn -f third-party/clonefactory/pom.xml install && CLONEFACTORY_VERSION=0.0.1-SNAPSHOT ...
(cd backend && ./mvnw -B -q -Dclonefactory.version="${CLONEFACTORY_VERSION:-0.0.2}" -Dmaven.test.skip=true clean package)
docker build "${platform_args[@]}" -t "${BACKEND_IMAGE}" backend

(cd web && npm ci --no-audit --no-fund && VITE_API_BASE_URL=/api npm run build)
docker build "${platform_args[@]}" -t "${FRONTEND_IMAGE}" web

echo "课程后端镜像：${BACKEND_IMAGE}"
echo "课程前端镜像：${FRONTEND_IMAGE}"
