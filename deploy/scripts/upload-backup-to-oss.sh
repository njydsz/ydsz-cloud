#!/usr/bin/env bash
# ============================================================
# YDSZ 备份文件上传 OSS 脚本
#
# 功能:
#   1. 将本地备份文件（pg dump / pg_basebackup / redis rdb / nacos zip）上传到阿里云 OSS
#   2. 自动清理 OSS 上过期备份（保留 30 天）
#   3. 上传结果 Webhook 通知
#
# 使用方式:
#   ./upload-backup-to-oss.sh                          # 上传今日全部备份
#   ./upload-backup-to-oss.sh --file /path/to/file     # 上传指定文件
#   ./upload-backup-to-oss.sh --cleanup                # 仅清理 OSS 过期对象
#
# 定时调度（crontab）:
#   # 每日凌晨 2:30 上传备份到 OSS
#   30 2 * * * /opt/ydsz/scripts/upload-backup-to-oss.sh >> /var/log/ydsz/oss-upload.log 2>&1
#
# 环境变量（必须）:
#   OSS_ENDPOINT        OSS 端点,如 https://oss-cn-hangzhou.aliyuncs.com
#   OSS_BUCKET          Bucket 名称
#   OSS_ACCESS_KEY_ID   AccessKey ID
#   OSS_ACCESS_KEY_SECRET  AccessKey Secret
#   OSS_PREFIX          OSS 对象前缀,默认 ydsz-backup/
#
# 与 DISASTER_RECOVERY_RUNBOOK.md §3.2 配套使用
# ============================================================
set -euo pipefail

# ============================================================
# 配置
# ============================================================
OSS_ENDPOINT="${OSS_ENDPOINT:-https://oss-cn-hangzhou.aliyuncs.com}"
OSS_BUCKET="${OSS_BUCKET:-ydsz-backup}"
# OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET 通过环境变量注入
OSS_PREFIX="${OSS_PREFIX:-ydsz-backup}"
BACKUP_DIR="${BACKUP_DIR:-/data/backups}"
OSS_RETENTION_DAYS="${OSS_RETENTION_DAYS:-30}"

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
            payload="{\"msg_type\":\"text\",\"content\":{\"text\":\"[YDSZ OSS Upload] ${status}\\nHost: ${HOSTNAME}\\nTime: ${TIMESTAMP}\\n${message}\"}}"
            ;;
        dingtalk)
            payload="{\"msgtype\":\"text\",\"text\":{\"content\":\"[YDSZ OSS Upload] ${status}\\nHost: ${HOSTNAME}\\nTime: ${TIMESTAMP}\\n${message}\"}}"
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
    command -v ossutil >/dev/null 2>&1 || command -v aws >/dev/null 2>&1 || {
        error "ossutil 或 aws cli 未安装,请先安装"
        exit 2
    }
    [ -z "${OSS_ACCESS_KEY_ID:-}" ] && { error "OSS_ACCESS_KEY_ID 未设置"; exit 2; }
    [ -z "${OSS_ACCESS_KEY_SECRET:-}" ] && { error "OSS_ACCESS_KEY_SECRET 未设置"; exit 2; }
    [ -d "$BACKUP_DIR" ] || { error "备份目录不存在: $BACKUP_DIR"; exit 2; }

    # 配置 ossutil（如果使用 ossutil）
    if command -v ossutil >/dev/null 2>&1; then
        ossutil config -e "$OSS_ENDPOINT" -i "$OSS_ACCESS_KEY_ID" -k "$OSS_ACCESS_KEY_SECRET" >/dev/null 2>&1 || true
    fi
    log "环境检查通过"
}

# ============================================================
# 上传单个文件
# ============================================================
upload_file() {
    local local_file="$1"
    local oss_key

    if [ ! -f "$local_file" ]; then
        warn "文件不存在,跳过: $local_file"
        return 0
    fi

    # OSS 对象 key: prefix/date/type/filename
    local file_type
    file_type=$(basename "$(dirname "$local_file")")
    oss_key="${OSS_PREFIX}/${DATE}/${file_type}/$(basename "$local_file")"

    log "上传: $local_file -> oss://${OSS_BUCKET}/${oss_key}"

    if command -v ossutil >/dev/null 2>&1; then
        if ossutil cp -f "$local_file" "oss://${OSS_BUCKET}/${oss_key}" >/dev/null 2>&1; then
            log "上传成功 (ossutil)"
        else
            error "上传失败 (ossutil): $local_file"
            return 1
        fi
    else
        # AWS CLI 兼容（S3 协议）
        if AWS_ACCESS_KEY_ID="$OSS_ACCESS_KEY_ID" \
           AWS_SECRET_ACCESS_KEY="$OSS_ACCESS_KEY_SECRET" \
           aws s3 cp "$local_file" "s3://${OSS_BUCKET}/${oss_key}" \
           --endpoint-url "$OSS_ENDPOINT" >/dev/null 2>&1; then
            log "上传成功 (aws cli)"
        else
            error "上传失败 (aws cli): $local_file"
            return 1
        fi
    fi
}

# ============================================================
# 上传今日全部备份
# ============================================================
upload_all() {
    local failed=0

    # PG 逻辑备份
    for f in "${BACKUP_DIR}/postgres/ydsz_full_${DATE}"*.dump; do
        [ -f "$f" ] || continue
        upload_file "$f" || failed=$((failed + 1))
    done

    # PG 物理备份
    for f in "${BACKUP_DIR}/postgres/base/base_${DATE}"*/base.tar.gz; do
        [ -f "$f" ] || continue
        upload_file "$f" || failed=$((failed + 1))
    done

    # Redis RDB
    for f in "${BACKUP_DIR}/redis/dump_${DATE}"*.rdb; do
        [ -f "$f" ] || continue
        upload_file "$f" || failed=$((failed + 1))
    done

    # Nacos 配置
    for f in "${BACKUP_DIR}/nacos/configs_${DATE}"*.zip; do
        [ -f "$f" ] || continue
        upload_file "$f" || failed=$((failed + 1))
    done

    if [ "$failed" -gt 0 ]; then
        error "${failed} 个文件上传失败"
        notify "FAILED" "上传失败 ${failed} 个文件,请检查日志"
        return 1
    fi
    log "全部上传成功"
    notify "SUCCESS" "今日备份已全部上传到 OSS"
}

# ============================================================
# 清理 OSS 过期对象
# ============================================================
cleanup_oss() {
    local cutoff_date
    cutoff_date=$(date -d "-${OSS_RETENTION_DAYS} days" +"%Y-%m-%d" 2>/dev/null || date -v-${OSS_RETENTION_DAYS}d +"%Y-%m-%d")
    log "清理 OSS 上 ${cutoff_date} 之前的对象..."

    if command -v ossutil >/dev/null 2>&1; then
        # 列出 OSS 对象并删除过期对象
        ossutil ls "oss://${OSS_BUCKET}/${OSS_PREFIX}/" --recursive 2>/dev/null | \
            grep -E "^oss://" | awk '{print $1}' | while read -r obj; do
                # 从对象 key 提取日期段(prefix/YYYY-MM-DD/...)
                local obj_date
                obj_date=$(echo "$obj" | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2}' | head -1)
                if [ -n "$obj_date" ] && [ "$obj_date" \< "$cutoff_date" ]; then
                    ossutil rm "$obj" --force >/dev/null 2>&1 && log "已删除: $obj"
                fi
            done
    fi
    log "OSS 清理完成"
}

# ============================================================
# 主流程
# ============================================================
main() {
    log "========== YDSZ 备份上传 OSS 开始 =========="
    log "Host: $HOSTNAME | Date: $DATE | Timestamp: $TIMESTAMP"
    check_env

    case "${1:-}" in
        --file)
            shift
            upload_file "$1"
            ;;
        --cleanup)
            cleanup_oss
            ;;
        "")
            upload_all
            cleanup_oss
            ;;
        *)
            error "未知参数: $1"
            echo "用法: $0 [--file <path> | --cleanup]"
            exit 2
            ;;
    esac

    log "========== YDSZ 备份上传 OSS 结束 =========="
}

main "$@"
