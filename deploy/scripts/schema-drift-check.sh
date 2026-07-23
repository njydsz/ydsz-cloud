#!/usr/bin/env bash
# ============================================================
# YDSZ Schema 漂移检测脚本
#
# 功能:
#   1. 在临时 PostgreSQL 容器中执行 V1.0.0.sql
#   2. pg_dump --schema-only 导出实际 schema
#   3. 与上次基线快照对比（git diff）
#   4. 如果有变化，生成 diff 报告并标记需要审查的变更
#
# 使用方式:
#   ./deploy/scripts/schema-drift-check.sh              # 检测漂移
#   ./deploy/scripts/schema-drift-check.sh --update      # 更新基线快照
#
# 退出码:
#   0 — 无漂移或基线已更新
#   1 — 检测到漂移（需要人工审查）
#   2 — 环境错误（Docker/PG 不可用）
#
# 依赖: docker, pg_dump, git
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SQL_FILE="$PROJECT_ROOT/deploy/sql/V1.0.0.sql"
BASELINE_FILE="$PROJECT_ROOT/deploy/sql/.schema-baseline.sql"
DIFF_OUTPUT="$PROJECT_ROOT/deploy/sql/.schema-drift.diff"

PG_CONTAINER="ydsz-schema-check"
PG_IMAGE="postgres:18-alpine"
PG_USER="postgres"
PG_PASS="schema-check-pass"
PG_DB="ydsz_schema_check"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ============================================================
# 前置检查
# ============================================================
check_prerequisites() {
    if ! command -v docker &>/dev/null; then
        log_error "Docker 不可用，请先安装 Docker"
        exit 2
    fi
    if ! docker info &>/dev/null; then
        log_error "Docker daemon 未运行"
        exit 2
    fi
    if [[ ! -f "$SQL_FILE" ]]; then
        log_error "SQL 文件不存在: $SQL_FILE"
        exit 2
    fi
}

# ============================================================
# 启动临时 PG 容器并初始化 schema
# ============================================================
start_pg_and_init() {
    log_info "启动临时 PostgreSQL 容器..."
    # 清理可能残留的容器
    docker rm -f "$PG_CONTAINER" 2>/dev/null || true

    docker run -d \
        --name "$PG_CONTAINER" \
        -e POSTGRES_PASSWORD="$PG_PASS" \
        -e POSTGRES_DB="$PG_DB" \
        -p 0:5432 \
        "$PG_IMAGE" >/dev/null

    # 等待 PG 就绪
    log_info "等待 PostgreSQL 就绪..."
    local max_wait=30
    local waited=0
    while ! docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" -d "$PG_DB" &>/dev/null; do
        sleep 1
        waited=$((waited + 1))
        if [[ $waited -ge $max_wait ]]; then
            log_error "PostgreSQL 启动超时（${max_wait}s）"
            docker rm -f "$PG_CONTAINER" >/dev/null
            exit 2
        fi
    done
    log_info "PostgreSQL 已就绪（等待 ${waited}s）"

    # 执行 V1.0.0.sql
    log_info "执行 V1.0.0.sql 初始化 schema..."
    docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -v ON_ERROR_STOP=1 < "$SQL_FILE"
    if [[ $? -ne 0 ]]; then
        log_error "V1.0.0.sql 执行失败"
        docker rm -f "$PG_CONTAINER" >/dev/null
        exit 1
    fi
    log_info "Schema 初始化成功"
}

# ============================================================
# 导出 schema 快照
# ============================================================
dump_schema() {
    log_info "导出 schema 快照..."
    docker exec "$PG_CONTAINER" pg_dump \
        -U "$PG_USER" \
        -d "$PG_DB" \
        --schema-only \
        --no-owner \
        --no-privileges \
        --no-comments \
        | sed 's/^--.*$/--/' \
        | grep -v '^$' \
        > /tmp/ydsz-schema-current.sql
    log_info "Schema 快照已导出（$(wc -l < /tmp/ydsz-schema-current.sql) 行）"
}

# ============================================================
# 对比基线
# ============================================================
compare_baseline() {
    if [[ ! -f "$BASELINE_FILE" ]]; then
        log_warn "基线快照不存在，首次运行将创建基线"
        cp /tmp/ydsz-schema-current.sql "$BASELINE_FILE"
        log_info "基线快照已创建: $BASELINE_FILE"
        return 0
    fi

    log_info "与基线快照对比..."
    diff -u "$BASELINE_FILE" /tmp/ydsz-schema-current.sql > "$DIFF_OUTPUT" 2>&1 || true

    if [[ -s "$DIFF_OUTPUT" ]]; then
        log_warn "检测到 Schema 漂移！Diff 报告: $DIFF_OUTPUT"
        echo ""
        echo "===== Schema Drift Diff (前 50 行) ====="
        head -50 "$DIFF_OUTPUT"
        echo "..."
        echo ""
        log_warn "请审查以上变更，确认后运行: ./deploy/scripts/schema-drift-check.sh --update"
        return 1
    else
        log_info "无 Schema 漂移，基线与当前一致"
        rm -f "$DIFF_OUTPUT"
        return 0
    fi
}

# ============================================================
# 更新基线
# ============================================================
update_baseline() {
    cp /tmp/ydsz-schema-current.sql "$BASELINE_FILE"
    log_info "基线快照已更新: $BASELINE_FILE"
}

# ============================================================
# 清理
# ============================================================
cleanup() {
    log_info "清理临时容器..."
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
    rm -f /tmp/ydsz-schema-current.sql
}

# ============================================================
# 主流程
# ============================================================
main() {
    trap cleanup EXIT

    check_prerequisites
    start_pg_and_init
    dump_schema

    local update_mode=false
    if [[ "${1:-}" == "--update" ]]; then
        update_mode=true
    fi

    if $update_mode; then
        update_baseline
        exit 0
    else
        if compare_baseline; then
            exit 0
        else
            exit 1
        fi
    fi
}

main "$@"
