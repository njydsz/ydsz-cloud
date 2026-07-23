#!/usr/bin/env bash
# ============================================================
# YDSZ Nacos 配置导出备份脚本
#
# 功能:
#   1. 调用 Nacos OpenAPI 导出全部配置为 zip
#   2. 自动清理过期备份（保留 7 天）
#   3. 备份结果 Webhook 通知
#
# 使用方式:
#   ./nacos-config-export.sh
#
# 定时调度（crontab）:
#   # 每日凌晨 0:30 Nacos 配置导出
#   30 0 * * * /opt/ydsz/scripts/nacos-config-export.sh >> /var/log/ydsz/nacos-export.log 2>&1
#
# 环境变量:
#   NACOS_HOST        Nacos 主机,默认 127.0.0.1
#   NACOS_PORT        Nacos 端口,默认 8848
#   NACOS_USERNAME    Nacos 用户名（可选,开启认证时必填）
#   NACOS_PASSWORD    Nacos 密码（可选,开启认证时必填）
#   BACKUP_DIR        备份目录,默认 /data/backups/nacos
#   RETENTION_DAYS    保留天数,默认 7
#
# 与 DISASTER_RECOVERY_RUNBOOK.md §3.2 配套使用
# ============================================================
set -euo pipefail

NACOS_HOST="${NACOS_HOST:-127.0.0.1}"
NACOS_PORT="${NACOS_PORT:-8848}"
NACOS_USERNAME="${NACOS_USERNAME:-}"
NACOS_PASSWORD="${NACOS_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-/data/backups/nacos}"
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
            payload="{\"msg_type\":\"text\",\"content\":{\"text\":\"[YDSZ Nacos Export] ${status}\\nHost: ${HOSTNAME}\\nTime: ${TIMESTAMP}\\n${message}\"}}"
            ;;
        dingtalk)
            payload="{\"msgtype\":\"text\",\"text\":{\"content\":\"[YDSZ Nacos Export] ${status}\\nHost: ${HOSTNAME}\\nTime: ${TIMESTAMP}\\n${message}\"}}"
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
    command -v curl >/dev/null 2>&1 || { error "curl 未安装"; exit 2; }
    [ -d "$BACKUP_DIR" ] || mkdir -p "$BACKUP_DIR"
    log "环境检查通过"
}

# ============================================================
# 获取 Nacos AccessToken（如果配置了认证）
# ============================================================
get_access_token() {
    [ -z "$NACOS_USERNAME" ] && return 0

    local response
    response=$(curl -sS -X POST \
        "http://${NACOS_HOST}:${NACOS_PORT}/nacos/v1/auth/login" \
        -d "username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}" 2>/dev/null) || {
        error "Nacos 登录失败"
        return 1
    }

    # 从响应中提取 accessToken
    local token
    token=$(echo "$response" | grep -oE '"accessToken":"[^"]+"' | head -1 | cut -d'"' -f4)
    [ -z "$token" ] && { error "无法获取 Nacos accessToken"; return 1; }
    echo "$token"
}

# ============================================================
# 导出配置
# ============================================================
do_export() {
    local backup_file="${BACKUP_DIR}/configs_${TIMESTAMP}.zip"
    local base_url="http://${NACOS_HOST}:${NACOS_PORT}/nacos/v1/cs/configs?export=true"

    # 如果配置了认证,附加 accessToken 参数
    local access_token
    access_token=$(get_access_token)
    if [ -n "$access_token" ]; then
        base_url="${base_url}&accessToken=${access_token}"
    fi

    log "导出 Nacos 配置: $base_url -> $backup_file"

    local http_code
    http_code=$(curl -sS -o "$backup_file" -w "%{http_code}" "$base_url" 2>/dev/null) || {
        error "curl 请求失败"
        notify "FAILED" "curl 请求失败"
        exit 1
    }

    if [ "$http_code" != "200" ]; then
        error "导出失败,HTTP $http_code"
        rm -f "$backup_file"
        notify "FAILED" "导出失败 HTTP $http_code"
        exit 1
    fi

    # 检查文件大小
    local size
    size=$(du -sh "$backup_file" | awk '{print $1}')
    log "导出完成: $backup_file ($size)"
    notify "SUCCESS" "导出路径: $backup_file, 大小: $size"
}

# ============================================================
# 清理过期备份
# ============================================================
cleanup() {
    log "清理 ${RETENTION_DAYS} 天前的 Nacos 备份..."
    find "$BACKUP_DIR" -maxdepth 1 -type f -name "configs_*.zip" -mtime "+$RETENTION_DAYS" -delete 2>/dev/null || true
    log "清理完成"
}

# ============================================================
# 主流程
# ============================================================
main() {
    log "========== YDSZ Nacos 配置导出开始 =========="
    log "Host: $HOSTNAME | Date: $DATE | Timestamp: $TIMESTAMP"
    check_env
    do_export
    cleanup
    log "========== YDSZ Nacos 配置导出结束 =========="
}

main "$@"
