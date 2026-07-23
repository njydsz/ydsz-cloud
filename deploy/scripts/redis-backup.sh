#!/usr/bin/env bash
# ============================================================
# YDSZ Redis 备份脚本
#
# 功能:
#   1. 触发 Redis BGSAVE 并等待完成
#   2. 拷贝 dump.rdb 到备份目录（按日期命名）
#   3. 自动清理过期备份（保留 7 天）
#   4. 备份结果 Webhook 通知
#
# 使用方式:
#   ./redis-backup.sh
#
# 定时调度（crontab）:
#   # 每日凌晨 1:00 Redis 备份
#   0 1 * * * /opt/ydsz/scripts/redis-backup.sh >> /var/log/ydsz/redis-backup.log 2>&1
#
# 环境变量:
#   REDIS_HOST        Redis 主机,默认 127.0.0.1
#   REDIS_PORT        Redis 端口,默认 6379
#   REDIS_PASS        Redis 密码（可选）
#   REDIS_RDB_PATH    dump.rdb 路径,默认 /var/lib/redis/dump.rdb
#   BACKUP_DIR        备份目录,默认 /data/backups/redis
#   RETENTION_DAYS    保留天数,默认 7
#
# 与 DISASTER_RECOVERY_RUNBOOK.md §3.2 配套使用
# ============================================================
set -euo pipefail

REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASS="${REDIS_PASS:-}"
REDIS_RDB_PATH="${REDIS_RDB_PATH:-/var/lib/redis/dump.rdb}"
BACKUP_DIR="${BACKUP_DIR:-/data/backups/redis}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

WEBHOOK_URL="${WEBHOOK_URL:-}"
WEBHOOK_TYPE="${WEBHOOK_TYPE:-feishu}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DATE=$(date +"%Y-%m-%d")
HOSTNAME=$(hostname)

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()   { echo -e "[$(date '+%F %T')] [INFO]  $*"; }
warn()  { echo -e "[$(date '+%F %T')] [WARN]  ${YELLOW}$*${NC}"; }
error() { echo -e "[$(date '+%F %T')] [ERROR] ${RED}$*${NC}"; }

notify() {
    local status=$1
    local message=$2
    [ -z "$WEBHOOK_URL" ] && return 0
    local payload
    case "$WEBHOOK_TYPE" in
        feishu)
            payload="{\"msg_type\":\"text\",\"content\":{\"text\":\"[YDSZ Redis Backup] ${status}\\nHost: ${HOSTNAME}\\nTime: ${TIMESTAMP}\\n${message}\"}}"
            ;;
        dingtalk)
            payload="{\"msgtype\":\"text\",\"text\":{\"content\":\"[YDSZ Redis Backup] ${status}\\nHost: ${HOSTNAME}\\nTime: ${TIMESTAMP}\\n${message}\"}}"
            ;;
        *)
            payload="{\"status\":\"${status}\",\"message\":\"${message}\",\"host\":\"${HOSTNAME}\",\"time\":\"${TIMESTAMP}\"}"
            ;;
    esac
    curl -sS -X POST -H 'Content-Type: application/json' -d "$payload" "$WEBHOOK_URL" >/dev/null 2>&1 || true
}

# ============================================================
# Redis CLI 包装器
# ============================================================
redis_cli() {
    if [ -n "$REDIS_PASS" ]; then
        redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -a "$REDIS_PASS" --no-auth-warning "$@"
    else
        redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" "$@"
    fi
}

# ============================================================
# 前置检查
# ============================================================
check_env() {
    command -v redis-cli >/dev/null 2>&1 || { error "redis-cli 未安装"; exit 2; }
    [ -d "$BACKUP_DIR" ] || mkdir -p "$BACKUP_DIR"
    log "环境检查通过"
}

# ============================================================
# 触发 BGSAVE 并等待完成
# ============================================================
do_bgsave() {
    log "触发 BGSAVE..."
    redis_cli BGSAVE >/dev/null 2>&1 || { error "BGSAVE 触发失败"; notify "FAILED" "BGSAVE 触发失败"; exit 1; }

    # 等待 BGSAVE 完成
    local elapsed=0
    local max_wait=300
    while [ "$elapsed" -lt "$max_wait" ]; do
        local last_save
        last_save=$(redis_cli LASTSAVE 2>/dev/null | tr -d '[:space:]')
        local current_time
        current_time=$(date +%s)
        if [ -n "$last_save" ] && [ $((current_time - last_save)) -lt 10 ]; then
            log "BGSAVE 完成 (LASTSAVE: $last_save)"
            break
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    if [ "$elapsed" -ge "$max_wait" ]; then
        error "BGSAVE 超时 ${max_wait}s"
        notify "FAILED" "BGSAVE 超时"
        exit 1
    fi
}

# ============================================================
# 拷贝 RDB 文件
# ============================================================
copy_rdb() {
    local backup_file="${BACKUP_DIR}/dump_${TIMESTAMP}.rdb"

    if [ ! -f "$REDIS_RDB_PATH" ]; then
        error "RDB 文件不存在: $REDIS_RDB_PATH"
        notify "FAILED" "RDB 文件不存在: $REDIS_RDB_PATH"
        exit 1
    fi

    log "拷贝 RDB: $REDIS_RDB_PATH -> $backup_file"
    cp "$REDIS_RDB_PATH" "$backup_file" || { error "拷贝失败"; notify "FAILED" "拷贝 RDB 失败"; exit 1; }

    # SHA256 校验
    local sha256
    sha256=$(sha256sum "$backup_file" | awk '{print $1}')
    echo "$sha256  $backup_file" > "${backup_file}.sha256"
    log "SHA256: $sha256"

    local size
    size=$(du -sh "$backup_file" | awk '{print $1}')
    log "备份完成: $backup_file ($size)"
    notify "SUCCESS" "备份路径: $backup_file, 大小: $size"
}

# ============================================================
# 清理过期备份
# ============================================================
cleanup() {
    log "清理 ${RETENTION_DAYS} 天前的 Redis 备份..."
    find "$BACKUP_DIR" -maxdepth 1 -type f -name "dump_*.rdb" -mtime "+$RETENTION_DAYS" -delete 2>/dev/null || true
    find "$BACKUP_DIR" -maxdepth 1 -type f -name "dump_*.rdb.sha256" -mtime "+$RETENTION_DAYS" -delete 2>/dev/null || true
    log "清理完成"
}

# ============================================================
# 主流程
# ============================================================
main() {
    log "========== YDSZ Redis 备份开始 =========="
    log "Host: $HOSTNAME | Date: $DATE | Timestamp: $TIMESTAMP"
    check_env
    do_bgsave
    copy_rdb
    cleanup
    log "========== YDSZ Redis 备份结束 =========="
}

main "$@"
