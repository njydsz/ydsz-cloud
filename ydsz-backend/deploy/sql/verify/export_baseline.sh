#!/usr/bin/env bash
# =====================================================================
# Schema Baseline 导出脚本
#
# 用途：从当前数据库导出 Schema 作为未来校验的 baseline
#
# 用法：
#   ./export_baseline.sh [--output <path>] [--pg-version pg17]
#
# 前置：STATUS.md 状态必须为 LIVE（即 schema/*.sql 已全部为真实 DDL）
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT="${SQL_DIR}/schema_baseline.sql"
PG_VERSION="${PG_VERSION:-pg17}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) OUTPUT="$2"; shift 2 ;;
    --pg-version) PG_VERSION="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

# 状态检查
STATUS_FILE="${SQL_DIR}/STATUS.md"
if [[ -f "${STATUS_FILE}" ]] && ! grep -qi '## 当前状态.*LIVE' "${STATUS_FILE}"; then
  echo "❌ 当前状态不是 LIVE，baseline 导出无意义" >&2
  echo "   请先完成 V1.0.0__init.sql 的真实 DDL 导入，再更新 STATUS.md" >&2
  exit 1
fi

# Docker 检查
if ! command -v docker &>/dev/null; then
  echo "❌ 未找到 docker，baseline 导出失败" >&2
  exit 1
fi

CONTAINER_NAME="ydsz-baseline-export-$RANDOM"
PG_PASSWORD="ydsz_baseline"

cleanup() {
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "🚀 启动临时 PostgreSQL 容器 ..."
docker run -d --name "${CONTAINER_NAME}" \
  -e POSTGRES_PASSWORD="${PG_PASSWORD}" \
  -e POSTGRES_DB=ydsz \
  -p 0:5432 \
  "${PG_VERSION}" >/dev/null

for i in $(seq 1 30); do
  if docker exec "${CONTAINER_NAME}" pg_isready -U postgres -d ydsz >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "📦 执行 schema/*.sql ..."
for f in "${SQL_DIR}"/schema/*.sql; do
  [[ -f "${f}" ]] || continue
  if ! grep -qE '^\s*(CREATE|ALTER|DROP|INSERT|UPDATE|DELETE)' "${f}"; then
    continue
  fi
  docker exec -i "${CONTAINER_NAME}" psql -U postgres -d ydsz -v ON_ERROR_STOP=1 < "${f}" >/dev/null 2>&1 || true
done

echo "🌱 执行 seed/*.sql ..."
for f in "${SQL_DIR}"/seed/*.sql; do
  [[ -f "${f}" ]] || continue
  docker exec -i "${CONTAINER_NAME}" psql -U postgres -d ydsz -v ON_ERROR_STOP=1 < "${f}" >/dev/null 2>&1 || true
done

echo "📐 导出 baseline 到 ${OUTPUT} ..."
docker exec "${CONTAINER_NAME}" pg_dump -U postgres -d ydsz --schema-only --no-owner --no-privileges > "${OUTPUT}"

echo "✅ Baseline 导出完成"
echo "   文件: ${OUTPUT}"
echo "   大小: $(wc -c < "${OUTPUT}") bytes"
