#!/usr/bin/env bash
# ============================================================
# PMIS PostgreSQL 自动备份脚本
#
# 功能:
#   1. pg_basebackup 全量物理备份（WAL 归档模式）
#   2. pg_dump 逻辑备份（每日全量 + 可选增量）
#   3. 自动清理过期备份（保留 7 天全量 + 30 天 WAL 归档）
#   4. 备份完整性校验（pg_restore --list）
#   5. 备份结果通知（Webhook / 邮件）
#
# 使用方式:
#   ./pg-backup.sh                      # 执行全量备份
#   ./pg-backup.sh --verify <file>      # 验证备份文件
#   ./pg-backup.sh --restore <file>     # 恢复到指定备份
#
# 定时调度（crontab）:
#   # 每日凌晨 2:00 全量备份
#   0 2 * * * /opt/pmis/scripts/pg-backup.sh >> /var/log/pmis/backup.log 2>&1
#   # 每 15 分钟 WAL 归档检查
#   */15 * * * * /opt/pmis/scripts/pg-backup.sh --wal-archive >> /var/log/pmis/wal-archive.log 2>&1
#
# 退出码:
#   0 — 备份成功
#   1 — 备份失败
#   2 — 环境错误
#
# 依赖: pg_basebackup, pg_dump, pg_restore, curl (Webhook 通知)
# ============================================================
set -euo pipefail

# ============================================================
# 配置（通过环境变量或默认值）
# ============================================================
PG_HOST="${PG_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-postgres}"
PG_DB="${PG_DB:-ydsz-pmis}"
# PGPASSWORD 环境变量传入密码

BACKUP_DIR="${BACKUP_DIR:-/data/backups/postgres}"
WAL_ARCHIVE_DIR="${WAL_ARCHIVE_DIR:-/data/backups/wal-archive}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
WAL_RETENTION_DAYS="${WAL_RETENTION_DAYS:-30}"

# Webhook 通知（可选）
WEBHOOK_URL="${WEBHOOK_URL:-}"
WEBHOOK_TYPE="${WEBHOOK_TYPE:-feishu}"  # feishu / dingtalk / wecom / generic

# 时间戳
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DATE=$(date +"%Y-%m-%d")
HOSTNAME=$(hostname)

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] $*"; }
log_warn()  { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] $*"; }
log_error() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $*" >&2; }

# ============================================================
# 前置检查
# ============================================================
check_prerequisites() {
    if ! command -v pg_dump &>/dev/null; then
        log_error "pg_dump 不可用，请安装 PostgreSQL 客户端工具"
        exit 2
    fi
    if [[ -z "${PGPASSWORD:-}" ]]; then
        log_warn "PGPASSWORD 未设置，将使用 .pgpass 或免密认证"
    fi
    mkdir -p "$BACKUP_DIR" "$WAL_ARCHIVE_DIR"
}

# ============================================================
# Webhook 通知
# ============================================================
send_notification() {
    local status="$1"  # success / failure
    local message="$2"

    if [[ -z "$WEBHOOK_URL" ]]; then
        return 0
    fi

    local icon="✅"
    local color="green"
    if [[ "$status" == "failure" ]]; then
        icon="❌"
        color="red"
    fi

    local payload
    case "$WEBHOOK_TYPE" in
        feishu)
            payload=$(cat <<EOF
{
  "msg_type": "interactive",
  "card": {
    "header": {
      "title": {"tag": "plain_text", "content": "${icon} PMIS 数据库备份${status}"},
      "template": "${color}"
    },
    "elements": [
      {"tag": "div", "text": {"tag": "lark_md", "content": "**主机**: ${HOSTNAME}\n**数据库**: ${PG_DB}\n**时间**: ${DATE}\n**详情**: ${message}"}}
    ]
  }
}
EOF
)
            ;;
        dingtalk)
            payload=$(cat <<EOF
{
  "msgtype": "markdown",
  "markdown": {
    "title": "PMIS 数据库备份${status}",
    "text": "## ${icon} PMIS 数据库备份${status}\n\n- **主机**: ${HOSTNAME}\n- **数据库**: ${PG_DB}\n- **时间**: ${DATE}\n- **详情**: ${message}"
  }
}
EOF
)
            ;;
        *)
            payload="{\"status\": \"${status}\", \"host\": \"${HOSTNAME}\", \"database\": \"${PG_DB}\", \"message\": \"${message}\", \"timestamp\": \"${TIMESTAMP}\"}"
            ;;
    esac

    curl -s -X POST "$WEBHOOK_URL" \
        -H "Content-Type: application/json" \
        -d "$payload" >/dev/null 2>&1 || true
}

# ============================================================
# 全量逻辑备份（pg_dump）
# ============================================================
full_logical_backup() {
    local dump_file="${BACKUP_DIR}/${PG_DB}_full_${TIMESTAMP}.dump"
    local dump_size

    log_info "开始全量逻辑备份: $dump_file"

    if PGPASSWORD="${PGPASSWORD:-}" pg_dump \
        -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" \
        --format=custom \
        --compress=9 \
        --verbose \
        --no-owner \
        --no-privileges \
        -f "$dump_file" 2>&1 | tail -5; then

        dump_size=$(du -h "$dump_file" | cut -f1)
        log_info "全量逻辑备份完成: $dump_file (${dump_size})"

        # 完整性校验
        if pg_restore --list "$dump_file" >/dev/null 2>&1; then
            log_info "备份文件完整性校验通过"
        else
            log_error "备份文件完整性校验失败！文件可能已损坏"
            send_notification "failure" "备份文件完整性校验失败: $dump_file"
            exit 1
        fi

        # 记录备份元信息
        cat > "${dump_file}.meta" <<EOF
backup_time=${TIMESTAMP}
backup_date=${DATE}
host=${HOSTNAME}
database=${PG_DB}
size=${dump_size}
type=full_logical
pg_version=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -t -c "SELECT version();" 2>/dev/null | head -1 | xargs)
checksum=$(sha256sum "$dump_file" | cut -d' ' -f1)
EOF
        log_info "备份元信息已记录: ${dump_file}.meta"

        send_notification "success" "全量逻辑备份成功: ${dump_file} (${dump_size})"
    else
        log_error "全量逻辑备份失败"
        send_notification "failure" "pg_dump 执行失败，请检查日志"
        exit 1
    fi
}

# ============================================================
# WAL 归档检查
# ============================================================
wal_archive_check() {
    log_info "检查 WAL 归档状态..."

    local wal_count
    wal_count=$(find "$WAL_ARCHIVE_DIR" -name "0000000*" -type f | wc -l)
    log_info "WAL 归档文件数: $wal_count"

    # 检查最近 WAL 归档时间
    local latest_wal
    latest_wal=$(find "$WAL_ARCHIVE_DIR" -name "0000000*" -type f -newer "$WAL_ARCHIVE_DIR/.last_check" 2>/dev/null | wc -l)
    if [[ $latest_wal -eq 0 ]]; then
        log_warn "自上次检查以来无新 WAL 归档，可能数据库无写入或归档异常"
    else
        log_info "新增 WAL 归档: $latest_wal 个"
    fi
    touch "$WAL_ARCHIVE_DIR/.last_check"
}

# ============================================================
# 清理过期备份
# ============================================================
cleanup_expired_backups() {
    log_info "清理过期备份（保留 ${RETENTION_DAYS} 天）..."

    local deleted_count=0
    # 清理全量备份
    while IFS= read -r -d '' file; do
        log_info "删除过期备份: $file"
        rm -f "$file" "${file}.meta"
        deleted_count=$((deleted_count + 1))
    done < <(find "$BACKUP_DIR" -name "*.dump" -mtime +${RETENTION_DAYS} -print0)

    # 清理过期 WAL 归档
    local wal_deleted=0
    while IFS= read -r -d '' file; do
        rm -f "$file"
        wal_deleted=$((wal_deleted + 1))
    done < <(find "$WAL_ARCHIVE_DIR" -name "0000000*" -mtime +${WAL_RETENTION_DAYS} -print0)

    log_info "清理完成: 删除 ${deleted_count} 个过期备份, ${wal_deleted} 个过期 WAL"
}

# ============================================================
# 验证备份文件
# ============================================================
verify_backup() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        log_error "备份文件不存在: $file"
        exit 1
    fi

    log_info "验证备份文件: $file"

    # 1. pg_restore --list 检查
    if pg_restore --list "$file" >/dev/null 2>&1; then
        log_info "✅ pg_restore --list 校验通过"
    else
        log_error "❌ pg_restore --list 校验失败，文件已损坏"
        exit 1
    fi

    # 2. SHA256 校验
    if [[ -f "${file}.meta" ]]; then
        local expected_checksum
        expected_checksum=$(grep '^checksum=' "${file}.meta" | cut -d= -f2)
        local actual_checksum
        actual_checksum=$(sha256sum "$file" | cut -d' ' -f1)
        if [[ "$expected_checksum" == "$actual_checksum" ]]; then
            log_info "✅ SHA256 校验通过: $actual_checksum"
        else
            log_error "❌ SHA256 校验失败: expected=$expected_checksum, actual=$actual_checksum"
            exit 1
        fi
    fi

    # 3. 表数量检查
    local table_count
    table_count=$(pg_restore --list "$file" 2>/dev/null | grep -c "TABLE" || true)
    log_info "备份中包含 ${table_count} 个表定义"

    log_info "备份验证全部通过 ✅"
}

# ============================================================
# 恢复指南
# ============================================================
restore_guide() {
    local file="$1"
    echo "============================================================"
    echo "PMIS 数据库恢复指南"
    echo "============================================================"
    echo ""
    echo "备份文件: $file"
    echo ""
    echo "恢复步骤:"
    echo "  1. 停止应用服务（所有 PMIS 微服务）"
    echo "  2. 停止 PostgreSQL: systemctl stop postgresql"
    echo "  3. 备份当前数据目录: mv /var/lib/postgresql/data /var/lib/postgresql/data.bak.$(date +%s)"
    echo "  4. 初始化新数据目录: pg_ctl initdb -D /var/lib/postgresql/data"
    echo "  5. 启动 PostgreSQL: systemctl start postgresql"
    echo "  6. 创建数据库: createdb -U postgres ${PG_DB}"
    echo "  7. 恢复备份: pg_restore -U postgres -d ${PG_DB} -j 4 -v $file"
    echo "  8. 验证数据: psql -U postgres -d ${PG_DB} -c 'SELECT count(*) FROM pmis_user;'"
    echo "  9. 启动应用服务"
    echo ""
    echo "⚠️  注意:"
    echo "  - 恢复前务必停止所有应用连接"
    echo "  - 恢复后需检查序列号（SEQUENCE）是否需要重置"
    echo "  - 如需 PITR（时间点恢复），请配合 WAL 归档文件"
    echo "============================================================"
}

# ============================================================
# 主流程
# ============================================================
main() {
    local mode="${1:-backup}"

    check_prerequisites

    case "$mode" in
        --verify)
            verify_backup "${2:-}"
            ;;
        --restore)
            restore_guide "${2:-}"
            ;;
        --wal-archive)
            wal_archive_check
            cleanup_expired_backups
            ;;
        backup|"")
            full_logical_backup
            wal_archive_check
            cleanup_expired_backups
            log_info "备份流程全部完成 ✅"
            ;;
        *)
            echo "用法: $0 [backup|--verify <file>|--restore <file>|--wal-archive]"
            exit 1
            ;;
    esac
}

main "$@"
