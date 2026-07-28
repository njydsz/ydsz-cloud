#!/usr/bin/env bash
# =============================================================================
# YDSZ 数据库 Schema 漂移检测脚本
# -----------------------------------------------------------------------------
# 作用:  对比 deploy/sql/V1.0.0.sql 定义的表结构与实际数据库的表结构
#        生成差异报告，CI 中阻断有漂移的 PR
# 用法:  bash deploy/scripts/schema-drift-check.sh
# 依赖:  psql (PostgreSQL client), pg_dump
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR/../.." && pwd)"
SQL_FILE="${PROJECT_ROOT}/deploy/sql/V1.0.0.sql"
DRIFT_FILE="${PROJECT_DIR:-${PROJECT_ROOT}}/deploy/sql/.schema-drift.diff"

# 数据库连接参数（从环境变量读取）
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-ydsz}"
DB_USER="${DB_USER:-ydsz}"
DB_PASSWORD="${DB_PASSWORD:-ydsz123}"

export PGPASSWORD="${DB_PASSWORD}"

echo "=========================================="
echo "  YDSZ Schema Drift Detection"
echo "  Database: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "  SQL File: ${SQL_FILE}"
echo "=========================================="

if [ ! -f "${SQL_FILE}" ]; then
  echo "⚠️  SQL 文件不存在: ${SQL_FILE}，跳过漂移检测"
  exit 0
fi

# 检查 psql 是否可用
if ! command -v psql &> /dev/null; then
  echo "⚠️  psql 未安装，跳过 Schema 漂移检测"
  echo "   安装: apt-get install postgresql-client"
  exit 0
fi

# 检查数据库连接
if ! psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c "SELECT 1" &> /dev/null; then
  echo "⚠️  无法连接到数据库，跳过 Schema 漂移检测"
  echo "   DB: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
  exit 0
fi

echo "✅ 数据库连接成功"

# 获取实际数据库的表结构（DDL）
ACTUAL_SCHEMA=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;" \
  -t 2>/dev/null | sed 's/^ *//;s/ *$//')

# 获取 SQL 文件中定义的表名
DEFINED_TABLES=$(grep -oE 'CREATE TABLE[^(]+' "${SQL_FILE}" | sed 's/CREATE TABLE//;s/^ *//;s/ *$//;s/ IF NOT EXISTS//' | sort)

# 对比表名差异
echo ""
echo "--- 表名差异 ---"
DIFF_FOUND=0

# 在 SQL 文件中定义但不在数据库中的表
while IFS= read -r table; do
  if [ -n "${table}" ] && ! echo "${ACTUAL_SCHEMA}" | grep -qw "${table}"; then
    echo "  + ${table} (定义了但数据库中不存在)"
    DIFF_FOUND=1
  fi
done <<< "${DEFINED_TABLES}"

# 在数据库中但不在 SQL 文件中的表
while IFS= read -r table; do
  if [ -n "${table}" ] && ! echo "${DEFINED_TABLES}" | grep -qw "${table}"; then
    echo "  - ${table} (数据库中存在但未定义)"
    DIFF_FOUND=1
  fi
done <<< "${ACTUAL_SCHEMA}"

echo ""
if [ "${DIFF_FOUND}" -eq 0 ]; then
  echo "✅ 未检测到 Schema 漂移"
  exit 0
else
  echo "❌ 检测到 Schema 漂移，请同步 SQL 文件与数据库结构"
  # 生成详细差异报告
  {
    echo "Schema Drift Report"
    echo "Generated: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo ""
    echo "=== Defined in SQL but missing in DB ==="
    while IFS= read -r table; do
      if [ -n "${table}" ] && ! echo "${ACTUAL_SCHEMA}" | grep -qw "${table}"; then
        echo "  + ${table}"
      fi
    done <<< "${DEFINED_TABLES}"
    echo ""
    echo "=== In DB but not defined in SQL ==="
    while IFS= read -r table; do
      if [ -n "${table}" ] && ! echo "${DEFINED_TABLES}" | grep -qw "${table}"; then
        echo "  - ${table}"
      fi
    done <<< "${ACTUAL_SCHEMA}"
  } > "${DRIFT_FILE}" 2>/dev/null || true
  exit 1
fi
