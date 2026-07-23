#!/usr/bin/env bash
# =============================================================================
#  YDSZ · Ubuntu 中间件统一管理脚本
# -----------------------------------------------------------------------------
#  用法:   ./infra-manager.sh {start|stop|status|restart} [middleware]
#          middleware: postgres | redis | nacos | minio | seata |
#                     rocketmq | xxl-job | all
#  示例:   ./infra-manager.sh start all         # 启动全部
#          ./infra-manager.sh stop postgres     # 只停 postgres
#          ./infra-manager.sh status            # 看全部状态
# 路径:   默认从 /opt/<middleware> 读取
# 自定义: export YDSZ_INFRA_HOME=/your/path    # 改安装位置
# 注:    Elasticsearch 已移除（P2-19 起改用 PostgreSQL tsvector）
# =============================================================================
set -e

# ---------- 默认配置 ----------
export YDSZ_INFRA_HOME=${YDSZ_INFRA_HOME:-/opt}
YDSZ_DATA_HOME=${YDSZ_DATA_HOME:-/var/lib}
YDSZ_LOG_HOME=${YDSZ_LOG_HOME:-/var/log/ydsz}
mkdir -p "$YDSZ_LOG_HOME"

# ---------- 颜色 ----------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $*"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*"; }

ACTION=${1:-status}
TARGET=${2:-all}

# =============================================================================
#  PostgreSQL
# =============================================================================
pg_start() {
  if systemctl is-active --quiet postgresql; then
    log "PostgreSQL 已在运行"; return
  fi
  log "启动 PostgreSQL..."
  sudo systemctl start postgresql
  sleep 2
  ok "PostgreSQL 已启动"
}
pg_stop() {
  log "停止 PostgreSQL..."
  sudo systemctl stop postgresql
  ok "PostgreSQL 已停止"
}
pg_status() {
  if systemctl is-active --quiet postgresql; then
    ok "PostgreSQL: 运行中"
    sudo -u postgres psql -c "SELECT version();" 2>/dev/null | head -3
  else
    err "PostgreSQL: 未运行"
  fi
}

# =============================================================================
#  Redis
# =============================================================================
redis_start() {
  if systemctl is-active --quiet redis; then
    log "Redis 已在运行"; return
  fi
  log "启动 Redis..."
  sudo systemctl start redis
  sleep 1
  ok "Redis 已启动"
}
redis_stop() {
  log "停止 Redis..."
  sudo systemctl stop redis
  ok "Redis 已停止"
}
redis_status() {
  if systemctl is-active --quiet redis; then
    ok "Redis: 运行中"
    redis-cli -a ${REDIS_PASSWORD:-ydsz123} ping 2>/dev/null
  else
    err "Redis: 未运行"
  fi
}

# =============================================================================
#  Nacos（systemd 单元名为 nacos）
# =============================================================================
nacos_start() {
  if systemctl is-active --quiet nacos; then
    log "Nacos 已在运行"; return
  fi
  log "启动 Nacos..."
  sudo systemctl start nacos
  log "等待 Nacos 就绪（首次启动约 30-60s）..."
  for i in {1..30}; do
    if curl -sf http://127.0.0.1:8848/nacos/actuator/health >/dev/null 2>&1; then
      ok "Nacos 已启动"
      return
    fi
    sleep 2
  done
  warn "Nacos 启动超时，请查看日志：$YDSZ_LOG_HOME/nacos/"
}
nacos_stop() {
  log "停止 Nacos..."
  sudo systemctl stop nacos
  ok "Nacos 已停止"
}
nacos_status() {
  if systemctl is-active --quiet nacos; then
    ok "Nacos: 运行中"
    curl -s http://127.0.0.1:8848/nacos/actuator/health 2>/dev/null
  else
    err "Nacos: 未运行"
  fi
}

# =============================================================================
#  MinIO
# =============================================================================
minio_start() {
  if systemctl is-active --quiet minio; then
    log "MinIO 已在运行"; return
  fi
  log "启动 MinIO..."
  sudo systemctl start minio
  sleep 2
  ok "MinIO 已启动"
}
minio_stop() {
  log "停止 MinIO..."
  sudo systemctl stop minio
  ok "MinIO 已停止"
}
minio_status() {
  if systemctl is-active --quiet minio; then
    ok "MinIO: 运行中"
    curl -s http://127.0.0.1:9100/minio/health/live 2>/dev/null && echo " (API 健康)"
  else
    err "MinIO: 未运行"
  fi
}

# =============================================================================
#  Seata
# =============================================================================
seata_start() {
  if systemctl is-active --quiet seata; then
    log "Seata 已在运行"; return
  fi
  log "启动 Seata..."
  sudo systemctl start seata
  log "等待 Seata 就绪..."
  for i in {1..20}; do
    if nc -z 127.0.0.1 8091 2>/dev/null; then
      ok "Seata 已启动"; return
    fi
    sleep 2
  done
  warn "Seata 启动超时"
}
seata_stop() {
  log "停止 Seata..."
  sudo systemctl stop seata
  ok "Seata 已停止"
}
seata_status() {
  if systemctl is-active --quiet seata; then
    ok "Seata: 运行中"
    nc -z 127.0.0.1 8091 && echo "  端口 8091 可达"
    nc -z 127.0.0.1 7091 && echo "  端口 7091 (Console) 可达"
  else
    err "Seata: 未运行"
  fi
}

# =============================================================================
#  RocketMQ (NameServer + Broker)
# =============================================================================
rocketmq_start() {
  log "启动 RocketMQ NameServer..."
  sudo systemctl start rocketmq-namesrv
  sleep 3

  log "启动 RocketMQ Broker..."
  sudo systemctl start rocketmq-broker
  log "等待 Broker 就绪..."
  for i in {1..20}; do
    if nc -z 127.0.0.1 10911 2>/dev/null; then
      ok "RocketMQ 已启动"; return
    fi
    sleep 2
  done
  warn "RocketMQ 启动超时"
}
rocketmq_stop() {
  log "停止 RocketMQ Broker..."
  sudo systemctl stop rocketmq-broker
  sleep 2
  log "停止 RocketMQ NameServer..."
  sudo systemctl stop rocketmq-namesrv
  ok "RocketMQ 已停止"
}
rocketmq_status() {
  if systemctl is-active --quiet rocketmq-namesrv && systemctl is-active --quiet rocketmq-broker; then
    ok "RocketMQ: 运行中"
    nc -z 127.0.0.1 9876 && echo "  NameServer 端口 9876 可达"
    nc -z 127.0.0.1 10911 && echo "  Broker 端口 10911 可达"
  else
    err "RocketMQ: 未运行（NameServer=$(systemctl is-active rocketmq-namesrv), Broker=$(systemctl is-active rocketmq-broker)）"
  fi
}

# =============================================================================
#  XXL-Job
# =============================================================================
xxl_start() {
  if systemctl is-active --quiet xxl-job; then
    log "XXL-Job 已在运行"; return
  fi
  log "启动 XXL-Job..."
  sudo systemctl start xxl-job
  log "等待 XXL-Job 就绪..."
  for i in {1..20}; do
    if curl -sf http://127.0.0.1:9100/xxl-job-admin/actuator/health >/dev/null 2>&1; then
      ok "XXL-Job 已启动"; return
    fi
    sleep 2
  done
  warn "XXL-Job 启动超时"
}
xxl_stop() {
  log "停止 XXL-Job..."
  sudo systemctl stop xxl-job
  ok "XXL-Job 已停止"
}
xxl_status() {
  if systemctl is-active --quiet xxl-job; then
    ok "XXL-Job: 运行中"
    curl -s http://127.0.0.1:9100/xxl-job-admin/actuator/health 2>/dev/null
  else
    err "XXL-Job: 未运行"
  fi
}

# =============================================================================
#  注: Elasticsearch 已移除（P2-19 起改用 PostgreSQL tsvector 全文检索）
# =============================================================================

# =============================================================================
#  路由
# =============================================================================
case "$ACTION" in
  start)
    case "$TARGET" in
      all)
        pg_start; redis_start; nacos_start; minio_start
        seata_start; rocketmq_start; xxl_start
        ;;
      postgres)      pg_start ;;
      redis)         redis_start ;;
      nacos)         nacos_start ;;
      minio)         minio_start ;;
      seata)         seata_start ;;
      rocketmq)      rocketmq_start ;;
      xxl-job)       xxl_start ;;
      *) err "未知中间件: $TARGET"; exit 1 ;;
    esac
    ;;
  stop)
    case "$TARGET" in
      all)
        xxl_stop; rocketmq_stop; seata_stop
        minio_stop; nacos_stop; redis_stop; pg_stop
        ;;
      postgres)      pg_stop ;;
      redis)         redis_stop ;;
      nacos)         nacos_stop ;;
      minio)         minio_stop ;;
      seata)         seata_stop ;;
      rocketmq)      rocketmq_stop ;;
      xxl-job)       xxl_stop ;;
      *) err "未知中间件: $TARGET"; exit 1 ;;
    esac
    ;;
  status)
    echo "================== 中间件状态 =================="
    pg_status; redis_status; nacos_status; minio_status
    seata_status; rocketmq_status; xxl_status
    echo "================================================="
    ;;
  restart)
    $0 stop $TARGET
    sleep 2
    $0 start $TARGET
    ;;
  *)
    echo "用法: $0 {start|stop|status|restart} [middleware]"
    echo "      middleware: postgres|redis|nacos|minio|seata|rocketmq|xxl-job|all"
    exit 1
    ;;
esac
