#!/usr/bin/env bash
# =============================================================================
# YDSZ 数据库 Schema 变更执行脚本
# -----------------------------------------------------------------------------
# 作用:  在不引入 Flyway/Liquibase 的前提下，提供结构化的 DDL 变更管理流程
#        - 变更前自动备份当前 schema
#        - 变更后自动验证 + 漂移检测
#        - 每次变更记录到 changelog
#
# 用法:  bash deploy/scripts/pg-apply-schema.sh <sql_file> [--env=sit|prod]
#        bash deploy/scripts/pg-apply-schema.sh deploy/sql/changelog/2026-07-29_add_user_avatar.sql
#
# 流程:  1. 解析 SQL 文件，提取 DDL 语句
#        2. 连接数据库，执行 BEGIN → DDL → COMMIT（事务安全）
#        3. 记录变更日志到 deploy/sql/changelog/
#        4. 自动执行 schema 漂移检测
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR/../.." && pwd)"
CHANGELOG_DIR="${PROJECT_ROOT}/deploy/sql/changelog"
ROLLBACK_DIR="${PROJECT_ROOT}/deploy/sql/rollback"

# 解析参数
SQL_FILE=""
ENV="sit"
for arg in "$@"; do
  case "${arg}" in
    --env=*) ENV="${arg#--env=}" ;;
    --env) shift; ENV="$1" ;;
    *) SQL_FILE="${arg}" ;;
  esac
done

if [ -z "${SQL_FILE}" ]; then
  echo "用法: bash deploy/scripts/pg-apply-schema.sh <sql_file> [--env=sit|prod]"
  echo "示例: bash deploy/scripts/pg-apply-schema.sh deploy/sql/changelog/2026-07-29_add_user_avatar.sql --env=sit"
  exit 1
fi

if [ ! -f "${SQL_FILE}" ]; then
  echo "❌ SQL 文件不存在: ${SQL_FILE}"
  exit 1
fi

# 根据环境选择数据库连接参数
case "${ENV}" in
  sit)
    DB_HOST="${DB_HOST_SIT:-sit-postgresql}"
    DB_PORT="${DB_PORT_SIT:-5432}"
    DB_NAME="${DB_NAME_SIT:-ydsz}"
    DB_USER="${DB_USER_SIT:-ydsz}"
    DB_PASSWORD="${DB_PASSWORD_SIT:-ydsz123}"
    ;;
  prod)
    DB_HOST="${DB_HOST_PROD}"
    DB_PORT="${DB_PORT_PROD:-5432}"
    DB_NAME="${DB_NAME_PROD:-ydsz}"
    DB_USER="${DB_USER_PROD:-ydsz}"
    DB_PASSWORD="${DB_PASSWORD_PROD}"
    if [ -z "${DB_PASSWORD}" ]; then
      echo "❌ 生产环境数据库密码未配置: DB_PASSWORD_PROD"
      exit 1
    fi
    ;;
  *)
    echo "❌ 未知环境: ${ENV} (支持: sit, prod)"
    exit 1
    ;;
esac

export PGPASSWORD="${DB_PASSWORD}"
TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
SQL_BASENAME=$(basename "${SQL_FILE}" .sql)
CHANGELOG_FILE="${CHANGELOG_DIR}/${TIMESTAMP}_${SQL_BASENAME}.json"

mkdir -p "${CHANGELOG_DIR}" "${ROLLBACK_DIR}"

echo "=========================================="
echo "  YDSZ Schema Change Apply"
echo "  Environment: ${ENV}"
echo "  Database: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "  SQL File: ${SQL_FILE}"
echo "  Timestamp: ${TIMESTAMP}"
echo "=========================================="

# 1. 备份当前 schema
BACKUP_FILE="/tmp/schema_backup_${TIMESTAMP}.sql"
echo ">>> Step 1: 备份当前 schema..."
pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  --schema-only --no-owner --no-privileges > "${BACKUP_FILE}" 2>/dev/null || {
  echo "⚠️  schema 备份失败，继续执行（生产环境建议中止）"
  BACKUP_FILE=""
}

# 2. 执行 DDL 变更（事务安全）
echo ">>> Step 2: 执行 DDL 变更..."
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  -v ON_ERROR_STOP=1 <<EOF
BEGIN;
\i ${SQL_FILE}
COMMIT;
EOF

if [ $? -ne 0 ]; then
  echo "❌ DDL 变更执行失败！"
  if [ -n "${BACKUP_FILE}" ]; then
    echo ">>> 自动回滚: 恢复 schema 备份..."
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
      -v ON_ERROR_STOP=1 -f "${BACKUP_FILE}" 2>/dev/null || true
    echo "⚠️  schema 已尝试恢复，请手动验证"
  fi
  exit 1
fi

echo "✅ DDL 变更执行成功"

# 3. 记录变更日志
echo ">>> Step 3: 记录变更日志..."
cat > "${CHANGELOG_FILE}" <<EOF
{
  "timestamp": "${TIMESTAMP}",
  "environment": "${ENV}",
  "sqlFile": "${SQL_FILE}",
  "database": "${DB_HOST}:${DB_PORT}/${DB_NAME}",
  "appliedBy": "$(whoami)",
  "status": "SUCCESS",
  "backupFile": "${BACKUP_FILE}"
}
EOF
echo "✅ 变更日志: ${CHANGELOG_FILE}"

# 4. 漂移检测
echo ">>> Step 4: Schema 漂移检测..."
if [ -f "${SCRIPT_DIR}/schema-drift-check.sh" ]; then
  bash "${SCRIPT_DIR}/schema-drift-check.sh" || true
fi

echo "=========================================="
echo "  ✅ Schema 变更完成"
echo "  Changelog: ${CHANGELOG_FILE}"
echo "=========================================="
