#!/usr/bin/env bash
# =============================================================================
# YDSZ 开发者本地环境一键启动
# -----------------------------------------------------------------------------
# P2-6: 一键启动全部基础设施 + 后端微服务 + 前端
#
# 用法:
#   bash deploy/scripts/dev-start.sh              # 启动全部
#   bash deploy/scripts/dev-start.sh --infra      # 仅启动基础设施
#   bash deploy/scripts/dev-start.sh --backend     # 仅启动后端
#   bash deploy/scripts/dev-start.sh --frontend    # 仅启动前端
#
# 前提条件:
#   - Docker 24+ / Docker Compose v2+
#   - JDK 21
#   - Node.js 20+ / pnpm 9+
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/deploy/docker/docker-compose.dev.yml"

MODE="${1:---all}"

echo "=========================================="
echo "  YDSZ Dev Environment Starter"
echo "  Mode: ${MODE}"
echo "  Project: ${PROJECT_ROOT}"
echo "=========================================="

# ============================================================
# 1. 启动基础设施（PostgreSQL / Redis / Nacos / MinIO 等）
# ============================================================
start_infra() {
    echo ">>> Starting Infrastructure (Docker Compose)..."
    if [ ! -f "${COMPOSE_FILE}" ]; then
        echo "⚠️  Docker Compose 文件不存在: ${COMPOSE_FILE}"
        echo "   请先创建 deploy/docker/docker-compose.dev.yml"
        return 1
    fi
    docker compose -f "${COMPOSE_FILE}" up -d
    echo "✅ Infrastructure started"
    echo "   PostgreSQL: localhost:5432"
    echo "   Redis:      localhost:6379"
    echo "   Nacos:      localhost:8848"
    echo "   MinIO:      localhost:9100"
    echo ""
    echo "   Waiting for services to be ready..."
    sleep 10
}

# ============================================================
# 2. 启动后端微服务（Maven spring-boot:run 并行启动）
# ============================================================
start_backend() {
    echo ">>> Starting Backend Microservices..."
    cd "${PROJECT_ROOT}/ydsz-backend"

    # 服务列表（端口分配）
    SERVICES=(
        "ydsz-gateway:9000"
        "ydsz-userinfo:9001"
        "ydsz-system:9002"
        "ydsz-project:9003"
        "ydsz-message:9004"
        "ydsz-cronjob:9005"
        "ydsz-workflow:9006"
        "ydsz-agent:9007"
        "ydsz-nextwiki:8800"
    )

    for svc_port in "${SERVICES[@]}"; do
        svc=$(echo "${svc_port}" | cut -d: -f1)
        port=$(echo "${svc_port}" | cut -d: -f2)
        echo "  Starting ${svc} on port ${port}..."
        # 后台启动，日志输出到 /tmp/ydsz-{svc}.log
        nohup mvn -B -pl "${svc}" -am spring-boot:run \
            -Dspring-boot.run.jvmArguments="-Xmx512m" \
            > "/tmp/ydsz-${svc}.log" 2>&1 &
        echo "  ✅ ${svc} started (PID: $!, log: /tmp/ydsz-${svc}.log)"
        # 间隔启动避免端口冲突
        sleep 3
    done
    echo "✅ Backend services starting (check logs in /tmp/ydsz-*.log)"
}

# ============================================================
# 3. 启动前端（pnpm dev）
# ============================================================
start_frontend() {
    echo ">>> Starting Frontend..."
    cd "${PROJECT_ROOT}/ydsz-frontend"
    pnpm dev &
    echo "✅ Frontend starting at http://localhost:8080"
}

# ============================================================
# 主逻辑
# ============================================================
case "${MODE}" in
    --infra)
        start_infra
        ;;
    --backend)
        start_backend
        ;;
    --frontend)
        start_frontend
        ;;
    --all)
        start_infra
        start_backend
        start_frontend
        ;;
    *)
        echo "用法: bash deploy/scripts/dev-start.sh [--infra|--backend|--frontend|--all]"
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "  ✅ Dev environment started!"
echo "  Frontend: http://localhost:8080"
echo "  Gateway:  http://localhost:9000"
echo "  Nacos:    http://localhost:8848/nacos"
echo "=========================================="
