#!/usr/bin/env bash
# =============================================================================
#  YDSZ PMIS · 批量构建 Docker 镜像（Linux/macOS）
# -----------------------------------------------------------------------------
#  用法:
#    bash deploy/scripts/build-images.sh [TAG] [REGISTRY]
#
#  示例:
#    # 构建所有 7 个后端服务 + 前端，tag=v1.0.0，推送到本地
#    bash deploy/scripts/build-images.sh v1.0.0
#
#    # 构建并推送到私有仓库
#    bash deploy/scripts/build-images.sh v1.0.0 registry.cn-hangzhou.aliyuncs.com/your-org
#
#  依赖:
#    - Docker 24+ (启用 BuildKit)
#    - Maven 3.9+ 与 JDK 21（仅在未使用 Docker 缓存时需要）
# =============================================================================

set -euo pipefail

TAG="${1:-v1.0.0-SNAPSHOT}"
REGISTRY="${2:-ydsz-pmis}"
PUSH="${PUSH:-false}"

# 服务列表（name:port）
SERVICES=(
    "gateway:9000"
    "userinfo:9001"
    "system:9002"
    "project:9003"
    "message:9004"
    "cronjob:9005"
    "workflow:9006"
    "agent:9007"
    "finance:9008"
    "sales:9009"
    "nextwiki:8800"
)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/ydsz-pmis-backend"
FRONTEND_DIR="${REPO_ROOT}/ydsz-pmis-frontend"

# 颜色
GREEN=$'\033[0;32m'
RED=$'\033[0;31m'
YELLOW=$'\033[1;33m'
NC=$'\033[0m'

echo "================================================================"
echo "  YDSZ PMIS · 批量构建 Docker 镜像"
echo "  TAG:       ${TAG}"
echo "  REGISTRY:  ${REGISTRY}"
echo "  PUSH:      ${PUSH}"
echo "================================================================"

# 必须启用 BuildKit
export DOCKER_BUILDKIT=1

# 构建后端服务
for SVC in "${SERVICES[@]}"; do
    NAME="${SVC%%:*}"
    PORT="${SVC##*:}"
    IMAGE="${REGISTRY}/${NAME}:${TAG}"

    echo ""
    echo "${YELLOW}▶ 构建后端镜像: ${IMAGE}${NC}"
    if docker build \
        --build-arg MODULE_NAME=ydsz-pmis-${NAME} \
        --build-arg APP_PORT=${PORT} \
        -t "${IMAGE}" \
        -f "${BACKEND_DIR}/Dockerfile" \
        "${BACKEND_DIR}"; then
        echo "${GREEN}[OK] ${IMAGE}${NC}"
        if [[ "${PUSH}" == "true" ]]; then
            docker push "${IMAGE}" && echo "${GREEN}[PUSHED] ${IMAGE}${NC}"
        fi
    else
        echo "${RED}[FAIL] ${IMAGE}${NC}"
        exit 1
    fi
done

# 构建前端镜像（可选）
if [[ -f "${FRONTEND_DIR}/Dockerfile" ]]; then
    echo ""
    echo "${YELLOW}▶ 构建前端镜像: ${REGISTRY}/frontend:${TAG}${NC}"
    if docker build \
        -t "${REGISTRY}/frontend:${TAG}" \
        -f "${FRONTEND_DIR}/Dockerfile" \
        "${FRONTEND_DIR}"; then
        echo "${GREEN}[OK] ${REGISTRY}/frontend:${TAG}${NC}"
        if [[ "${PUSH}" == "true" ]]; then
            docker push "${REGISTRY}/frontend:${TAG}" && echo "${GREEN}[PUSHED] ${REGISTRY}/frontend:${TAG}${NC}"
        fi
    else
        echo "${RED}[FAIL] ${REGISTRY}/frontend:${TAG}${NC}"
        exit 1
    fi
fi

echo ""
echo "================================================================"
echo "  构建完成"
echo "================================================================"
echo ""
echo "镜像列表:"
docker images "${REGISTRY}/*:${TAG}" --format "  {{.Repository}}:{{.Tag}}  ({{.Size}})"
echo ""
echo "下一步:"
echo "  1. 推送镜像:    PUSH=true bash $0 ${TAG} ${REGISTRY}"
echo "  2. K8s 部署:    kubectl apply -k deploy/k8s/overlays/dev"
echo "  3. Helm 部署:   helm install pmis deploy/helm/ydsz-pmis -n pmis -f deploy/helm/ydsz-pmis/values-dev.yaml"
echo "  4. 冒烟测试:    bash deploy/scripts/smoke-test.sh http://<gateway-ip>:9000"
