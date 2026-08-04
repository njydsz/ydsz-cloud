#!/usr/bin/env bash
# =============================================================================
#  YDSZ 本地开发环境启动脚本（P1-9）
# -----------------------------------------------------------------------------
#  用法：
#    ./deploy/scripts/dev-start.sh               # 启动基础设施 + 全部微服务
#    ./deploy/scripts/dev-start.sh --infra-only  # 仅启动基础设施
#    ./deploy/scripts/dev-start.sh --service project  # 只启动 project 服务
#    ./deploy/scripts/dev-start.sh --stop        # 停止全部
#
#  前置条件：
#    1. docker compose 可用
#    2. Nacos 中已配置 ydsz-common.yaml 等共享配置
#    3. 本机 JDK 21 + Maven 3.9+
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${BACKEND_DIR}/deploy/docker-compose.dev.yml"
MVN="${MVN:-mvn}"

INFRA_ONLY=false
TARGET_SERVICE=""
STOP=false

# ---- 参数解析 ----
while [[ $# -gt 0 ]]; do
  case "$1" in
    --infra-only) INFRA_ONLY=true; shift ;;
    --service)    TARGET_SERVICE="$2"; shift 2 ;;
    --stop)       STOP=true; shift ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

if [[ "$STOP" == "true" ]]; then
  echo "🛑 停止全部本地服务..."
  docker compose -f "${COMPOSE_FILE}" down
  echo "✅ 已停止"
  exit 0
fi

# ---- 启动基础设施 ----
echo "🚀 启动基础设施（PostgreSQL/Redis/Nacos/RocketMQ/MinIO/OTel）..."
docker compose -f "${COMPOSE_FILE}" up -d

# 等待基础设施就绪
echo "⏳ 等待基础设施就绪..."
for i in $(seq 1 60); do
  PG_OK=$(docker exec ydsz-dev-postgres pg_isready -U ydsz -d ydsz 2>/dev/null || echo "no")
  REDIS_OK=$(docker exec ydsz-dev-redis redis-cli ping 2>/dev/null || echo "no")
  if [[ "$PG_OK" == *"accepting"* ]] && [[ "$REDIS_OK" == "PONG" ]]; then
    break
  fi
  sleep 2
done
echo "✅ 基础设施就绪（PG:${PG_OK} Redis:${REDIS_OK}）"

if [[ "$INFRA_ONLY" == "true" ]]; then
  echo "✅ 基础设施启动完成（--infra-only 模式，跳过微服务）"
  exit 0
fi

# ---- 启动微服务 ----
SERVICES=(gateway system userinfo project message cronjob workflow agent literule nextwiki)
if [[ -n "$TARGET_SERVICE" ]]; then
  SERVICES=("$TARGET_SERVICE")
fi

cd "${BACKEND_DIR}"
for svc in "${SERVICES[@]}"; do
  MODULE="ydsz-${svc}"
  if [[ ! -d "$MODULE" ]]; then
    MODULE="ydsz-${svc}"  # 某些服务名不同，如 literule
  fi
  echo "▶ 启动 ${MODULE} ..."
  # 后台启动（日志输出到 deploy/logs/）
  mkdir -p "${BACKEND_DIR}/deploy/logs"
  nohup ${MVN} -pl "${MODULE}" spring-boot:run \
    -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev" \
    > "${BACKEND_DIR}/deploy/logs/${MODULE}.log" 2>&1 &
  sleep 3
done

echo ""
echo "=============================================="
echo "  🎉 本地开发环境已启动"
echo "  Nacos Console: http://localhost:8848/nacos"
echo "  RocketMQ Dashboard: http://localhost:8080"
echo "  MinIO Console: http://localhost:9003 (minioadmin/minioadmin)"
echo "  Jaeger UI: http://localhost:16686"
echo "  日志目录: deploy/logs/"
echo "=============================================="
echo "  停止全部: ./deploy/scripts/dev-start.sh --stop"
echo "  查看日志: tail -f deploy/logs/ydsz-gateway.log"
