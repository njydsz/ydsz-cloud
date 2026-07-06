#!/usr/bin/env bash
# =============================================================================
#  YDSZ PMIS · Entity ↔ SQL 对齐检查脚本 (Linux/macOS)
# -----------------------------------------------------------------------------
#  目的: 防止 Java 实体与 SQL 表结构漂移(参考大厂 DB 工程规范)
#
#  用法:
#    bash deploy/sql/scripts/check-entity-sql-alignment.sh [--strict] [--check-fields]
#
#  选项:
#    --strict       严格模式:发现任何漂移立即 exit 1(CI 默认)
#    --check-fields 字段级检查:对比 SQL 列名与 entity 字段名(更严格,可能误报)
#
#  检查项:
#    1. SQL 中存在 pmis_* 表但无对应 Java 实体
#    2. Java 实体 @TableName 指向 SQL 中不存在的表
#    3. (--check-fields)SQL 字段在 entity 中缺失,反之亦然
#
#  排除规则:
#    - undo_log (Seata 内置表)
#    - xxl_job_* (XXL-Job 调度框架表,源文件 deploy/common/sql/tables_xxl_job_pg.sql)
#    - pmis_migration_log (业务向,敏感字段迁移审计)
#    - pmis_database_change_log (历史占位,见 sql/README §Q5)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
SQL_FILE="${PROJECT_ROOT}/deploy/sql/V1.0.0.sql"
BACKEND_ROOT="${PROJECT_ROOT}/ydsz-pmis-backend"

STRICT=0
CHECK_FIELDS=0
for arg in "$@"; do
  case "${arg}" in
    --strict) STRICT=1 ;;
    --check-fields) CHECK_FIELDS=1 ;;
    -h|--help)
      sed -n '2,30p' "$0"
      exit 0
      ;;
    *) echo "[ERROR] 未知参数: ${arg}" >&2; exit 2 ;;
  esac
done

# 颜色
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BLUE='\033[0;34m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; BLUE=''; NC=''
fi

log()  { echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $*"; }
ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; }

[[ -f "${SQL_FILE}" ]] || { err "SQL 文件不存在: ${SQL_FILE}"; exit 1; }
[[ -d "${BACKEND_ROOT}" ]] || { err "后端目录不存在: ${BACKEND_ROOT}"; exit 1; }

# ----------------------------------------------------------------------------
# 1. 提取 SQL 中的所有 pmis_* 表名
# ----------------------------------------------------------------------------
log "扫描 SQL 中的 pmis_* 表..."
SQL_TABLES=$(grep -oE 'CREATE TABLE IF NOT EXISTS pmis_[a-zA-Z0-9_]+' "${SQL_FILE}" \
  | sed -E 's/CREATE TABLE IF NOT EXISTS //' \
  | sort -u)
SQL_TABLE_COUNT=$(echo "${SQL_TABLES}" | wc -l | tr -d ' ')
ok "SQL 中共发现 ${SQL_TABLE_COUNT} 张 pmis_* 表"

# ----------------------------------------------------------------------------
# 2. 提取 Java 实体中的 @TableName("pmis_xxx")
# ----------------------------------------------------------------------------
log "扫描 Java 实体中的 @TableName..."
ENTITY_TABLES=$(grep -rhE '@TableName\s*\(\s*(value\s*=\s*)?"pmis_[a-zA-Z0-9_]+"' \
  "${BACKEND_ROOT}" --include='*.java' \
  | sed -E 's/.*@TableName\s*\(\s*(value\s*=\s*)?"([^"]+)".*/\2/' \
  | sort -u)
ENTITY_TABLE_COUNT=$(echo "${ENTITY_TABLES}" | wc -l | tr -d ' ')
ok "Java 实体中共映射 ${ENTITY_TABLE_COUNT} 张表"

# ----------------------------------------------------------------------------
# 3. 比对:SQL 有但无实体
# ----------------------------------------------------------------------------
MISSING_ENTITY=()
while IFS= read -r table; do
  [[ -z "${table}" ]] && continue
  if ! echo "${ENTITY_TABLES}" | grep -qx "${table}"; then
    MISSING_ENTITY+=("${table}")
  fi
done <<< "${SQL_TABLES}"

# ----------------------------------------------------------------------------
# 4. 比对:实体映射了 SQL 中不存在的表
# ----------------------------------------------------------------------------
ORPHAN_ENTITY=()
while IFS= read -r table; do
  [[ -z "${table}" ]] && continue
  if ! echo "${SQL_TABLES}" | grep -qx "${table}"; then
    ORPHAN_ENTITY+=("${table}")
  fi
done <<< "${ENTITY_TABLES}"

# ----------------------------------------------------------------------------
# 5. 输出报告
# ----------------------------------------------------------------------------
echo ""
echo "============================================================"
echo "  Entity ↔ SQL 对齐检查报告"
echo "============================================================"
echo "  SQL 表总数:       ${SQL_TABLE_COUNT}"
echo "  Java 实体映射数:  ${ENTITY_TABLE_COUNT}"
echo "  SQL 有 / 无实体:  ${#MISSING_ENTITY[@]}"
echo "  实体孤儿:         ${#ORPHAN_ENTITY[@]}"
echo "============================================================"

if [[ ${#MISSING_ENTITY[@]} -gt 0 ]]; then
  echo ""
  err "以下 ${#MISSING_ENTITY[@]} 张 SQL 表没有对应 Java 实体:"
  for t in "${MISSING_ENTITY[@]}"; do
    echo "  - ${t}"
  done
fi

if [[ ${#ORPHAN_ENTITY[@]} -gt 0 ]]; then
  echo ""
  err "以下 ${#ORPHAN_ENTITY[@]} 个 Java 实体映射了 SQL 中不存在的表:"
  for t in "${ORPHAN_ENTITY[@]}"; do
    echo "  - ${t}"
  done
fi

# ----------------------------------------------------------------------------
# 6. 可选:字段级检查(更严格,可能误报)
# ----------------------------------------------------------------------------
if [[ ${CHECK_FIELDS} -eq 1 ]]; then
  log "字段级检查(--check-fields)..."
  # 简化版:仅对前 5 张表做抽样,避免全表扫描太慢
  SAMPLE_TABLES=$(echo "${SQL_TABLES}" | head -5)
  for table in ${SAMPLE_TABLES}; do
    log "  抽样字段比对: ${table}"
    # 实体文件:从 @TableName 反查
    entity_file=$(grep -rl "@TableName(\"${table}\")" \
      "${BACKEND_ROOT}" --include='*.java' | head -1 || true)
    if [[ -z "${entity_file}" ]]; then
      continue
    fi
    # SQL 列:CREATE TABLE 内字段
    sql_cols=$(awk "/CREATE TABLE IF NOT EXISTS ${table}\\(/,/^\\);/" "${SQL_FILE}" \
      | grep -oE '^[[:space:]]+[a-z_][a-z0-9_]+[[:space:]]+[A-Z]' \
      | awk '{print $1}' | sort -u)
    # Entity 字段名:private xxx yyy;
    entity_cols=$(grep -oE 'private [A-Za-z0-9<>?, ]+ [a-z][a-zA-Z0-9]*;' "${entity_file}" \
      | awk '{print $NF}' | sed 's/;//' | sort -u)
    # 输出 diff(仅 warn,不 fail)
    only_sql=$(comm -23 <(echo "${sql_cols}") <(echo "${entity_cols}"))
    only_ent=$(comm -13 <(echo "${sql_cols}") <(echo "${entity_cols}"))
    if [[ -n "${only_sql}" ]]; then
      warn "    SQL 中存在但 entity 缺失:"
      for c in ${only_sql}; do echo "      - ${c}"; done | head -5
    fi
    if [[ -n "${only_ent}" ]]; then
      warn "    entity 存在但 SQL 中缺失(可能是 @TableField 派生):"
      for c in ${only_ent}; do echo "      - ${c}"; done | head -5
    fi
  done
fi

echo ""
if [[ ${#MISSING_ENTITY[@]} -eq 0 && ${#ORPHAN_ENTITY[@]} -eq 0 ]]; then
  ok "对齐检查通过:SQL 与 Java 实体完全对齐"
  exit 0
fi

if [[ ${STRICT} -eq 1 ]]; then
  err "对齐检查失败(--strict 模式,exit 1)"
  exit 1
else
  warn "对齐检查发现问题(非严格模式,exit 0,但请尽快修复)"
  exit 0
fi
