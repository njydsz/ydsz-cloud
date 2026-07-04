#!/usr/bin/env bash
# =============================================================================
#  YDSZ PMIS · 一键停止脚本 (Linux / macOS)
# =============================================================================
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
LOG_DIR="$ROOT_DIR/.run-logs"

RED='\033[0;31m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; NC='\033[0m'
log() { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()  { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }

# 停止后端
if [[ -d "$LOG_DIR" ]]; then
  log "停止后端服务..."
  for pidfile in "$LOG_DIR"/*.pid; do
    [[ -f "$pidfile" ]] || continue
    name=$(basename "$pidfile" .pid)
    pid=$(cat "$pidfile" 2>/dev/null || echo "")
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      log "  停止 $name (PID $pid)"
      # 杀掉整个进程组
      pkill -P "$pid" 2>/dev/null || true
      kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$pidfile"
  done
  ok "后端服务已停止"
fi

# 停止基础设施（保留数据卷）
if [[ "${1:-}" == "--with-infra" || "${1:-}" == "-a" ]]; then
  log "停止基础设施容器..."
  cd "$ROOT_DIR/deploy/docker"
  docker compose -f docker-compose.dev.yml down
  ok "基础设施已停止（数据卷保留）"
fi

# 停止前端
pkill -f "vite" 2>/dev/null && log "前端进程已停止" || true

echo
ok "全部停止完成"
echo "  - 清理数据卷：  cd deploy/docker && docker compose -f docker-compose.dev.yml down -v"
echo "  - 重新启动：    ./deploy/ubuntu/scripts/start-all.sh"
