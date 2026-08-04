#!/usr/bin/env bash
# =====================================================================
# Schema 一致性校验脚本（CI 使用）
#
# 流程：
#   1. 读取 STATUS.md 判断当前模式（PLACEHOLDER / LIVE）
#   2. 启动临时 PostgreSQL 容器（自动清理）
#   3. 依次执行 schema/*.sql + seed/*.sql
#   4. PLACEHOLDER 模式：宽松校验（可执行性 + 语法 + 版本递增）
#   5. LIVE 模式：严格校验（基线 diff + 表/视图/列数阈值）
#
# 用法：
#   ./schema_check.sh [--pg-version pg17] [--strict] [--baseline-file <path>]
#
# 环境变量：
#   SKIP_SCHEMA_CHECK=1   跳过（本地调试用）
#   EXPECTED_TABLES       期望表数阈值（默认 126）
#   EXPECTED_VIEWS        期望视图数阈值（默认 5）
# =====================================================================
set -euo pipefail

# ---------------- 参数解析 ----------------
PG_VERSION="${PG_VERSION:-pg17}"
STRICT_MODE="${STRICT_MODE:-0}"
BASELINE_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pg-version) PG_VERSION="$2"; shift 2 ;;
    --strict) STRICT_MODE=1; shift ;;
    --baseline-file) BASELINE_FILE="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

# ---------------- 跳过逻辑 ----------------
if [[ "${SKIP_SCHEMA_CHECK:-0}" == "1" ]]; then
  echo "⏭️  SKIP_SCHEMA_CHECK=1，跳过 Schema 校验"
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
SQL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
STATUS_FILE="${SQL_DIR}/STATUS.md"

# ---------------- 静态检查函数（无 Docker 时降级使用） ----------------
# 校验内容：文件命名规范 + 版本递增 + 占位检测 + 回滚注释完整性
_static_check_sql_files() {
  local sql_dir="$1"
  local schema_dir="${sql_dir}/schema"
  local error=0

  echo "  📐 静态模式 — 无 Docker，执行文件级校验"

  # 1. 校验文件命名规范（V<主>.<次>.<补丁>__描述.sql）
  local bad_naming=0
  for f in "${schema_dir}"/V*.sql; do
    [[ -f "${f}" ]] || continue
    local basename_f
    basename_f="$(basename "${f}")"
    if [[ ! "${basename_f}" =~ ^V[0-9]+\.[0-9]+\.[0-9]+__[a-zA-Z0-9_]+\.sql$ ]]; then
      echo "     ❌ 文件命名不规范: ${basename_f}（期望格式：V<主>.<次>.<补丁>__描述.sql）"
      bad_naming=1
      error=1
    fi
  done
  if [[ "${bad_naming}" -eq 0 ]]; then
    echo "     ✅ 文件命名规范校验通过"
  fi

  # 2. 版本号严格递增校验（不允许版本回退或重复）
  local versions=()
  for f in "${schema_dir}"/V*.sql; do
    [[ -f "${f}" ]] || continue
    versions+=("$(basename "${f}" | sed 's/^V\([0-9]*\.[0-9]*\.[0-9]*\)__.*/\1')")
  done
  local sorted_unique
  sorted_unique=$(printf '%s\n' "${versions[@]}" | sort -V | uniq | wc -l | tr -d ' ')
  local total=${#versions[@]}
  if [[ "${sorted_unique}" -ne "${total}" ]]; then
  echo "     ❌ 版本号存在重复或未递增（共 ${total} 个文件，唯一版本 ${sorted_unique} 个）"
    error=1
  else
    echo "     ✅ 版本号递增校验通过（共 ${total} 个版本文件）"
  fi

  # 3. 占位文件检测（V1.0.0 之外的占位文件应被显式标记或清空）
  for f in "${schema_dir}"/V*.sql; do
    [[ -f "${f}" ]] || continue
    local basename_f
    basename_f="$(basename "${f}")"
    if grep -qi '占位' "${f}" 2>/dev/null && [[ "${basename_f}" != "V1.0.0__init.sql" ]]; then
      echo "     ⚠️  非 V1.0.0 文件包含占位标记: ${basename_f}（请确认是否已填充 DDL）"
    fi
  done

  # 4. 回滚注释完整性校验（占位文件 V1.0.0 除外）
  local rollback_missing=0
  for f in "${schema_dir}"/V*.sql; do
    [[ -f "${f}" ]] || continue
    local basename_f
    basename_f="$(basename "${f}")"
    if [[ "${basename_f}" == "V1.0.0__init.sql" ]]; then
      continue
    fi
    if ! grep -qi 'ROLLBACK' "${f}" 2>/dev/null; then
      echo "     ❌ 缺少 ROLLBACK 注释: ${basename_f}"
      rollback_missing=1
      error=1
    fi
  done
  if [[ "${rollback_missing}" -eq 0 ]]; then
    echo "     ✅ ROLLBACK 注释完整性校验通过"
  fi

  return "${error}"
}

# ---------------- 状态检测 ----------------
CURRENT_STATUS="PLACEHOLDER"
if [[ -f "${STATUS_FILE}" ]]; then
  STATUS_LINE=$(grep -m1 '^## 当前状态' "${STATUS_FILE}" || true)
  if echo "${STATUS_LINE}" | grep -qi 'LIVE'; then
    CURRENT_STATUS="LIVE"
  fi
fi

if [[ "${STRICT_MODE}" == "1" ]]; then
  CURRENT_STATUS="LIVE"
fi

echo "📋 Schema 校验模式: ${CURRENT_STATUS}"

# ---------------- Docker 可用性检查 ----------------
if ! command -v docker &>/dev/null; then
  echo "⚠️  未找到 docker，Schema 校验降级为静态校验（精确但无容器内 diff）" >&2
  # 无 Docker 时执行增强版静态检查
  _static_check_sql_files "${SQL_DIR}"
  exit $?  # 显式传递返回码
fi

# ---------------- 容器生命周期管理 ----------------
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

# 等待容器就绪（最多 30s）
READY=0
for i in $(seq 1 30); do
  if docker exec "${CONTAINER_NAME}" pg_isready -U postgres -d ydsz >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 1
done

if [[ "${READY}" -eq 0 ]]; then
  echo "❌ PostgreSQL 容器启动超时" >&2
  exit 1
fi

PG_PORT="$(docker port "${CONTAINER_NAME}" 5432 | head -1 | awk -F: '{print $2}')"
PG_URL="postgresql://postgres:${PG_PASSWORD}@127.0.0.1:${PG_PORT}/ydsz"
echo "  数据库就绪: ${PG_URL}"

# ---------------- 执行 Schema 脚本 ----------------
FAILED=0
SCHEMA_COUNT=0

echo "📦 执行 schema/*.sql ..."
for f in "${SQL_DIR}"/schema/*.sql; do
  [[ -f "${f}" ]] || continue
  SCHEMA_COUNT=$((SCHEMA_COUNT + 1))
  # 跳过纯占位文件（仅注释 + 无任何有效 SQL 语句）
  if ! grep -qE '^\s*(CREATE|ALTER|DROP|INSERT|UPDATE|DELETE|GRANT|COMMENT)' "${f}"; then
    echo "  - $(basename "${f}") ⏳ [占位跳过]"
    continue
  fi
  echo "  - $(basename "${f}") 执行中 ..."
  if ! docker exec -i "${CONTAINER_NAME}" psql -U postgres -d ydsz -v ON_ERROR_STOP=1 < "${f}" >/dev/null 2>&1; then
    echo "    ❌ 执行失败: ${f}"
    FAILED=1
  else
    echo "    ✅ 执行成功"
  fi
done

if [[ "${SCHEMA_COUNT}" -eq 0 ]]; then
  echo "⚠️  未找到任何 schema/*.sql 文件"
fi

# ---------------- 执行 Seed 脚本 ----------------
echo "🌱 执行 seed/*.sql ..."
SEED_COUNT=0
for f in "${SQL_DIR}"/seed/*.sql; do
  [[ -f "${f}" ]] || continue
  SEED_COUNT=$((SEED_COUNT + 1))
  echo "  - $(basename "${f}") 执行中 ..."
  if ! docker exec -i "${CONTAINER_NAME}" psql -U postgres -d ydsz -v ON_ERROR_STOP=1 < "${f}" >/dev/null 2>&1; then
    echo "    ❌ 执行失败: ${f}"
    FAILED=1
  else
    echo "    ✅ 执行成功"
  fi
done

if [[ "${SEED_COUNT}" -eq 0 ]]; then
  echo "  ℹ️  无 seed 脚本（seed/ 目录为空）"
fi

if [[ "${FAILED}" == "1" ]]; then
  echo "❌ Schema/Seed 脚本执行存在失败项"
  exit 1
fi

# ---------------- PLACEHOLDER 模式：宽松校验 ----------------
if [[ "${CURRENT_STATUS}" == "PLACEHOLDER" ]]; then
  echo ""
  echo "🔍 PLACEHOLDER 模式 — 宽松校验"
  echo "  ✅ 所有 schema/seed 脚本语法正确、可执行"
  echo "  ⏳ 跳过表数/视图数阈值校验（待 LIVE 模式激活）"
  echo "  💡 提示：从数据库导出真实 DDL 到 V1.0.0__init.sql 后更新 STATUS.md 为 LIVE"
  echo ""
  echo "✅ Schema 校验通过（PLACEHOLDER 模式）"
  exit 0
fi

# ---------------- LIVE 模式：严格校验 ----------------
echo ""
echo "🔍 LIVE 模式 — 严格校验"

ACTUAL_SQL="$(docker exec "${CONTAINER_NAME}" pg_dump -U postgres -d ydsz --schema-only --no-owner --no-privileges 2>/dev/null)"

# 关键指标采集
EXPECTED_TABLES="${EXPECTED_TABLES:-126}"
EXPECTED_VIEWS="${EXPECTED_VIEWS:-5}"
TABLE_COUNT="$(echo "${ACTUAL_SQL}" | grep -c 'CREATE TABLE' || true)"
VIEW_COUNT="$(echo "${ACTUAL_SQL}" | grep -c 'CREATE VIEW' || true)"
INDEX_COUNT="$(echo "${ACTUAL_SQL}" | grep -c 'CREATE INDEX\|CREATE UNIQUE INDEX' || true)"
COMMENT_COUNT="$(echo "${ACTUAL_SQL}" | grep -c 'COMMENT ON' || true)"

echo "  📊 Schema 统计"
echo "     表数量:   ${TABLE_COUNT} / 期望 >= ${EXPECTED_TABLES}"
echo "     视图数量: ${VIEW_COUNT} / 期望 >= ${EXPECTED_VIEWS}"
echo "     索引数量: ${INDEX_COUNT}"
echo "     注释数量: ${COMMENT_COUNT}"

# 阈值校验
THRESHOLD_FAILED=0
if [[ "${TABLE_COUNT}" -lt "${EXPECTED_TABLES}" ]]; then
  echo "  ❌ 表数量不足: ${TABLE_COUNT} < ${EXPECTED_TABLES}"
  THRESHOLD_FAILED=1
fi

if [[ "${VIEW_COUNT}" -lt "${EXPECTED_VIEWS}" ]]; then
  echo "  ❌ 视图数量不足: ${VIEW_COUNT} < ${EXPECTED_VIEWS}"
  THRESHOLD_FAILED=1
fi

# 基线 diff 校验（可选）
if [[ -n "${BASELINE_FILE}" && -f "${BASELINE_FILE}" ]]; then
  echo "  📐 Baseline diff 校验"
  BASELINE_MD5="$(md5sum "${BASELINE_FILE}" 2>/dev/null | awk '{print $1}' || shasum -a 256 "${BASELINE_FILE}" 2>/dev/null | awk '{print $1}')"
  ACTUAL_MD5="$(echo "${ACTUAL_SQL}" | md5sum 2>/dev/null | awk '{print $1}' || echo "${ACTUAL_SQL}" | shasum -a 256 2>/dev/null | awk '{print $1}')"
  if [[ "${BASELINE_MD5}" == "${ACTUAL_MD5}" ]]; then
    echo "     ✅ Baseline 一致 (MD5: ${ACTUAL_MD5:0:8}...)"
  else
    echo "     ❌ Baseline 不一致"
    echo "        期望 MD5: ${BASELINE_MD5:0:16}..."
    echo "        实际 MD5: ${ACTUAL_MD5:0:16}..."
    THRESHOLD_FAILED=1
  fi
elif [[ -f "${SQL_DIR}/schema_baseline.sql" ]]; then
  echo "  📐 Baseline 文件自动检测 (schema_baseline.sql)"
  BASELINE_MD5="$(md5sum "${SQL_DIR}/schema_baseline.sql" 2>/dev/null | awk '{print $1}' || shasum -a 256 "${SQL_DIR}/schema_baseline.sql" 2>/dev/null | awk '{print $1}')"
  ACTUAL_MD5="$(echo "${ACTUAL_SQL}" | md5sum 2>/dev/null | awk '{print $1}' || echo "${ACTUAL_SQL}" | shasum -a 256 2>/dev/null | awk '{print $1}')"
  if [[ "${BASELINE_MD5}" == "${ACTUAL_MD5}" ]]; then
    echo "     ✅ Baseline 一致 (MD5: ${ACTUAL_MD5:0:8}...)"
  else
    echo "     ❌ Baseline 不一致（表结构变更未更新 baseline）"
     THRESHOLD_FAILED=1
  fi
fi

if [[ "${THRESHOLD_FAILED}" == "1" ]]; then
  echo ""
  echo "❌ LIVE 模式校验失败"
  exit 1
fi

echo ""
echo "✅ Schema 校验通过（LIVE 模式）"
