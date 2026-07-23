#!/usr/bin/env bash
# ============================================================
# YDSZ PostgreSQL 物理全量备份脚本（pg_basebackup）
#
# 功能:
#   1. 使用 pg_basebackup 创建物理全量备份（含 WAL）
#   2. 自动清理过期物理备份（保留 4 周）
#   3. 备份结果 Webhook 通知
#
# 使用方式:
#   ./pg-basebackup.sh                  # 执行物理全量备份
#
# 定时调度（crontab）:
#   # 每周日凌晨 3:00 物理全量备份
#   0 3 * * 0 /opt/ydsz/scripts/pg-basebackup.sh >> /var/log/ydsz/basebackup.log 2>&1
#
# 退出码:
#   0 — 备份成功
#   1 — 备份失败
#   2 — 环境错误
#
# 依赖: pg_basebackup, curl
# 与 DISASTER_RECOVERY_RUNBOOK.md §3.2 配套使用
# ============================================================
set -euo pipefail

# ============================================================
# 配置（通过环境变量或默认值）
# ============================================================
PG_HOST="${PG_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-postgres}"
# PGPASSWORD 环境变量传入密码

BACKUP_DIR="${BACKUP_DIR:-/data/backups/postgres/base}"
RETENTION_WEEKS="${RETENTION_WEEKS:-4}"

# Webhook 通知（可选）
WEBHOOK_URL="${WEBHOOK_URL:-}"
WEBHOOK_TYPE="${WEBHOOK_TYPE:-feishu}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DATE=$(date +"%Y-%m-%d")
HOSTNAME=$(hostname)

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()   { echo -e "[$(date '+%F %T')] [INFO]  $*"; }
warn()  { echo -e "[$(date '+%F %T')] [WARN]  ${YELLOW}$*${NC}"; }
error() { echo -e "[$(date '+%F %T')] [ERROR] ${RED}$*${NC}"; }

# ============================================================
# Webhook 通知（复用 pg-backup.sh 的通知格式）
# ============================================================
notify() {
    local status=$1
    local message=$2
    if [ -z "$WEBHOOK_URL" ]; then
        return 0
    fi
    local payload
    case "$WEBHOOK_TYPE" in
        feishu)
            payload=$(cat <<EOF
{
  "msg_type": "text",
  "content": {
    "text": "[YDSZ Backup] pg_basebackup ${status}\nHost: ${HOSTNAME}\nTime: ${TIMESTAMP}\n${message}"
  }
}
EOF
)
            ;;
        dingtalk)
            payload=$(cat <<EOF
{
  "msgtype": "text",
  "text": {
    "content": "[YDSZ Backup] pg_basebackup ${status}\nHost: ${HOSTNAME}\nTime: ${TIMESTAMP}\n${message}"
  }
}
EOF
)
            ;;
        *)
            payload="{\"status\":\"${status}\",\"message\":\"${message}\",\"host\":\"${HOSTNAME}\",\"time\":\"${TIMESTAMP}\"}"
            ;;
    esac
    curl -sS -X POST -H 'Content-Type: application/json' -d "$payload" "$WEBHOOK_URL" >/dev/null 2>&1 || true
}

# ============================================================
# 前置检查
# ============================================================
check_env() {
    command -v pg_basebackup >/dev/null 2>&1 || { error "pg_basebackup 未安装"; exit 2; }
    command -v curl >/dev/null 2>&1 || { error "curl 未安装"; exit 2; }
    [ -d "$BACKUP_DIR" ] || mkdir -p "$BACKUP_DIR"
    log "环境检查通过: BACKUP_DIR=$BACKUP_DIR"
}

# ============================================================
# 物理全量备份
# ============================================================
do_basebackup() {
    local backup_path="${BACKUP_DIR}/base_${TIMESTAMP}"
    log "开始 pg_basebackup: $backup_path"

    # 使用 -X stream 流式传输 WAL，-z 启用 gzip 压缩 tar
    if PGPASSWORD="${PGPASSWORD:-}" pg_basebackup \
        -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" \
        -D "$backup_path" \
        -Ft -z -Xs -P \
        -c fast; then
        local size
        size=$(du -sh "$backup_path" | awk '{print $1}')
        log "物理备份完成: $backup_path ($size)"
        notify "SUCCESS" "备份路径: $backup_path, 大小: $size"
    else
        error "pg_basebackup 失败"
        rm -rf "$backup_path"
        notify "FAILED" "pg_basebackup 执行失败,请检查日志"
        exit 1
    fi

    # SHA256 校验
    local tar_file="${backup_path}/base.tar.gz"
    if [ -f "$tar_file" ]; then
        local sha256
        sha256=$(sha256sum "$tar_file" | awk '{print $1}')
        echo "$sha256  $tar_file" > "${backup_path}/base.tar.gz.sha256"
        log "SHA256 校验: $sha256"
    fi
}

# ============================================================
# 清理过期备份
# ============================================================
cleanup() {
    log "清理 ${RETENTION_WEEKS} 周前的物理备份..."
    find "$BACKUP_DIR" -maxdepth 1 -type d -name "base_*" -mtime "+$((RETENTION_WEEKS * 7))" -exec rm -rf {} \; 2>/dev/null || true
    log "清理完成"
}

# ============================================================
# 主流程
# ============================================================
main() {
    log "========== YDSZ pg_basebackup 开始 =========="
    log "Host: $HOSTNAME | Date: $DATE | Timestamp: $TIMESTAMP"
    check_env
    do_basebackup
    cleanup
    log "========== YDSZ pg_basebackup 结束 =========="
}

main "$@"
