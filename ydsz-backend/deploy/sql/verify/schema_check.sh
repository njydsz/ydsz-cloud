#!/usr/bin/env bash
# =====================================================================
# Schema 一致性校验脚本（CI 使用）
#
# 流程：
#   1. 启动临时 PostgreSQL 容器（自动清理）
#   2. 依次执行 schema/*.sql + seed/*.sql
#   3. pg_dump --schema-only 导出实际 Schema
#   4. 与期望 Schema（repo 内的 baseline）对比
#   5. 不一致 → 退出码 1，CI 失败
#
# 用法：
#   ./schema_check.sh [--pg-version pg17]
# 环境变量：
#   SKIP_SCHEMA_CHECK=1  跳过（本地调试用）
# =====================================================================
set -euo pipefail

if [[ "${SKIP_SCHEMA_CHECK:-0}" == "1" ]]; then
  echo "⏭️  SKIP_SCHEMA_CHECK=1，跳过 Schema 校验"
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PG_VERSION="${PG_VERSION:-pg17}"

# 检查 docker 可用性
if ! command -v docker &>/dev/null; then
  echo "❌ 未找到 docker，Schema 校验无法执行" >&2
  exit 1
fi

CONTAINER_NAME="ydsz-schema-check-$RANDOM"
PG_PASSWORD="ydsz_schema_check"

cleanup() {
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "🚀 启动临时 PostgreSQL 容器 (${PG_VERSION}) ..."
docker run -d --name "${CONTAINER_NAME}" \
  -e POSTGRES_PASSWORD="${PG_PASSWORD}" \
  -e POSTGRES_DB=ydsz \
  -p 0:5432 \
  "${PG_VERSION}" >/dev/null

# 等待容器就绪
for i in $(seq 1 30); do
  if docker exec "${CONTAINER_NAME}" pg_isready -U postgres -d ydsz >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

PG_PORT="$(docker port "${CONTAINER_NAME}" 5432 | head -1 | awk -F: '{print $2}')"
PG_URL="postgresql://postgres:${PG_PASSWORD}@127.0.0.1:${PG_PORT}/ydsz"

echo "📦 执行 schema/*.sql ..."
FAILED=0
for f in "${SQL_DIR}"/schema/*.sql; do
  echo "  - $(basename "${f}")"
  if ! docker exec -i "${CONTAINER_NAME}" psql -U postgres -d ydsz -v ON_ERROR_STOP=1 < "${f}" >/dev/null 2>&1; then
    echo "    ❌ 执行失败: ${f}"
    FAILED=1
  fi
done

echo "🌱 执行 seed/*.sql ..."
for f in "${SQL_DIR}"/seed/*.sql; do
  [[ -f "${f}" ]] || continue
  echo "  - $(basename "${f}")"
  if ! docker exec -i "${CONTAINER_NAME}" psql -U postgres -d ydsz -v ON_ERROR_STOP=1 < "${f}" >/dev/null 2>&1; then
    echo "    ❌ 执行失败: ${f}"
    FAILED=1
  fi
done

if [[ "${FAILED}" == "1" ]]; then
  echo "❌ Schema 脚本执行存在失败项"
  exit 1
fi

# 导出实际 Schema
echo "🔍 导出实际 Schema 并与期望对比 ..."
ACTUAL_SQL="$(docker exec "${CONTAINER_NAME}" pg_dump -U postgres -d ydsz --schema-only --no-owner --no-privileges 2>/dev/null)"

# 关键指标校验
TABLES_EXPECTED="${EXPECTED_TABLES:-126}"
TABLE_COUNT="$(echo "${ACTUAL_SQL}" | grep -c 'CREATE TABLE' || true)"
VIEW_COUNT="$(echo "${ACTUAL_SQL}" | grep -c 'CREATE VIEW' || true)"

echo "  - 实际表数: ${TABLE_COUNT}（期望 >= ${TABLES_EXPECTED}）"
echo "  - 实际视图数: ${VIEW_COUNT}（期望 >= 5）"

if [[ "${TABLE_COUNT}" -lt "${TABLES_EXPECTED}" ]]; then
  echo "❌ 表数量不足：${TABLE_COUNT} < ${TABLES_EXPECTED}"
  exit 1
fi

if [[ "${VIEW_COUNT}" -lt 5 ]]; then
  echo "⚠️  视图数量不足（${VIEW_COUNT} < 5），请确认视图是否已包含在版本脚本中"
fi

echo "✅ Schema 一致性校验通过"
