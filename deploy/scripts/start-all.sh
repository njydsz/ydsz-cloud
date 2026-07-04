#!/usr/bin/env bash
# =============================================================================
#  YDSZ PMIS · 一键启动脚本 (Linux / macOS)
# -----------------------------------------------------------------------------
#  启动顺序：
#    1. 环境检查（docker / java / maven / node）
#    2. 加载 deploy/.env
#    3. 启动基础设施容器（Nacos / PG / Redis / MinIO）
#    4. 等待基础设施健康
#    5. 编译并后台启动 7 个后端微服务
#    6. 启动前端开发服务器
#
#  用法：
#    ./deploy/scripts/start-all.sh            # 全量启动
#    ./deploy/scripts/start-all.sh --backend  # 只启动后端
#    ./deploy/scripts/start-all.sh --frontend # 只启动前端
#    ./deploy/scripts/start-all.sh --infra    # 只启动基础设施
# =============================================================================
set -e

# 颜色
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $*"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*"; }

# 路径：脚本所在 deploy/scripts，回退两级到仓库根
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/ydsz-pmis-backend"
FRONTEND_DIR="$ROOT_DIR/ydsz-pmis-frontend"
LOG_DIR="$ROOT_DIR/.run-logs"
mkdir -p "$LOG_DIR"

cd "$ROOT_DIR"

# -----------------------------------------------------------------------------
#  1. 环境检查
# -----------------------------------------------------------------------------
log "步骤 1/6 - 环境检查"

check_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    err "未找到 $1，请先安装：$2"
    exit 1
  fi
}
check_cmd docker "https://docs.docker.com/engine/install/"
check_cmd java "JDK 21 (https://adoptium.net/)"
JAVA_VER=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}' | awk -F. '{ if ($1>=21) print "OK"; else print "LOW" }')
[[ "$JAVA_VER" == "OK" ]] || { err "需要 JDK 21+，当前：$(java -version 2>&1 | head -n1)"; exit 1; }
check_cmd mvn "Maven 3.9+ (https://maven.apache.org/download.cgi)"

if [[ "$1" != "--backend" && "$1" != "--infra" ]]; then
  check_cmd node "Node.js 20+ (https://nodejs.org/)"
  check_cmd pnpm "pnpm 9+ (npm i -g pnpm)"
fi

ok "环境检查通过"

# -----------------------------------------------------------------------------
#  2. 加载环境变量
# -----------------------------------------------------------------------------
log "步骤 2/6 - 加载环境变量"
if [[ ! -f "$ROOT_DIR/deploy/.env" ]]; then
  warn "deploy/.env 不存在，从 deploy/.env.example 复制"
  cp "$ROOT_DIR/deploy/.env.example" "$ROOT_DIR/deploy/.env"
fi
# shellcheck disable=SC1091
set -a; source "$ROOT_DIR/deploy/.env"; set +a
ok "环境变量已加载"

# -----------------------------------------------------------------------------
#  3. 启动基础设施
# -----------------------------------------------------------------------------
if [[ "$1" != "--backend" && "$1" != "--frontend" ]]; then
  log "步骤 3/6 - 启动基础设施 (PostgreSQL / Redis / Nacos / MinIO)"
  cd "$ROOT_DIR/deploy/docker"
  docker compose -f docker-compose.dev.yml up -d

  log "等待基础设施健康（最长 120s）..."
  for i in {1..24}; do
    sleep 5
    HEALTHY=$(docker compose -f docker-compose.dev.yml ps --format json 2>/dev/null \
      | grep -c '"Health":"healthy"' || echo 0)
    if [[ "$HEALTHY" -ge 4 ]]; then
      ok "基础设施已就绪（4/4 healthy）"
      break
    fi
    log "等待中... ($i/24) 当前健康: $HEALTHY/4"
  done
  cd "$ROOT_DIR"

  # 检查 Nacos 是否真的能访问
  if curl -sf http://$NACOS_SERVER_ADDR/nacos/actuator/health >/dev/null; then
    ok "Nacos 健康：$NACOS_SERVER_ADDR"
  else
    warn "Nacos 健康检查失败，请稍后重试或查看日志：$LOG_DIR/nacos.log"
  fi
fi

# -----------------------------------------------------------------------------
#  4. 编译后端公共模块
# -----------------------------------------------------------------------------
if [[ "$1" != "--frontend" && "$1" != "--infra" ]]; then
  log "步骤 4/6 - 编译后端公共模块（首次约 3-5 分钟）"
  cd "$BACKEND_DIR"
  mvn -q -pl ydsz-pmis-common,ydsz-pmis-literule -am install -DskipTests
  ok "公共模块编译完成"
fi

# -----------------------------------------------------------------------------
#  5. 启动 7 个后端服务（后台）
# -----------------------------------------------------------------------------
if [[ "$1" != "--frontend" && "$1" != "--infra" ]]; then
  log "步骤 5/6 - 启动 7 个后端微服务（后台）"

  # 启动顺序：依赖在前
  # gateway(9000) → userinfo(9002) / system(9001) / project(9003) / workflow(9005) → cronjob(9004) / agent(9006)
  declare -A SERVICES=(
    ["ydsz-pmis-gateway"]="9000"
    ["ydsz-pmis-system"]="9001"
    ["ydsz-pmis-userinfo"]="9002"
    ["ydsz-pmis-project"]="9003"
    ["ydsz-pmis-cronjob"]="9004"
    ["ydsz-pmis-workflow"]="9005"
    ["ydsz-pmis-agent"]="9006"
  )

  for module in "${!SERVICES[@]}"; do
    port="${SERVICES[$module]}"
    log "  → 启动 $module (端口 $port)"
    cd "$BACKEND_DIR"
    nohup mvn -pl "$module" spring-boot:run \
      -Dspring-boot.run.jvmArguments="-Xms256m -Xmx512m" \
      > "$LOG_DIR/${module}.log" 2>&1 &
    echo $! > "$LOG_DIR/${module}.pid"
  done

  ok "7 个后端服务已在后台启动，日志：$LOG_DIR/{module}.log"

  log "等待所有服务健康（约 60-120s）..."
  for i in {1..24}; do
    sleep 5
    HEALTHY=0
    for module in "${!SERVICES[@]}"; do
      port="${SERVICES[$module]}"
      if curl -sf "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
        HEALTHY=$((HEALTHY+1))
      fi
    done
    if [[ "$HEALTHY" -ge 7 ]]; then
      ok "全部 7 个后端服务健康"
      break
    fi
    log "  当前健康: $HEALTHY/7"
  done
fi

# -----------------------------------------------------------------------------
#  6. 启动前端
# -----------------------------------------------------------------------------
if [[ "$1" != "--backend" && "$1" != "--infra" ]]; then
  log "步骤 6/6 - 启动前端"
  cd "$FRONTEND_DIR"
  if [[ ! -d node_modules ]]; then
    log "  首次安装依赖（约 1-2 分钟）..."
    pnpm install
  fi
  nohup pnpm dev > "$LOG_DIR/frontend.log" 2>&1 &
  echo $! > "$LOG_DIR/frontend.pid"
  ok "前端已启动，日志：$LOG_DIR/frontend.log"
fi

echo
echo "============================================================"
ok "PMIS 启动完成！"
echo
echo "  前端地址:        http://localhost:5173"
echo "  API 网关:        http://localhost:9000"
echo "  Nacos 控制台:    http://$NACOS_SERVER_ADDR/nacos  (nacos/nacos)"
echo "  MinIO 控制台:    http://127.0.0.1:9101  (minioadmin/minioadmin)"
echo
echo "  日志目录:        $LOG_DIR"
echo "  停止命令:        ./deploy/scripts/stop-all.sh"
echo "============================================================"
