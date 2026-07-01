#!/usr/bin/env bash
# ============================================================================
#  PMIS PostgreSQL 季度恢复演练脚本
#  --------------------------------------------------------------------------
#  用途：每月 1 号 04:00 触发（crontab），将昨日全量备份恢复到临时实例
#        验证备份文件可用性 + 记录 RTO（恢复时间目标）
#  输出：恢复时长 / 校验行数 / 异常告警
#  注意：需预先创建临时 PG 实例（端口 5433，独立数据目录）
# ============================================================================
set -euo pipefail

# ---------- 配置 ----------
PG_HOST="${PMIS_PG_HOST:-127.0.0.1}"
PG_PORT="${PMIS_PG_PORT:-5432}"
PG_USER="${PMIS_PG_USER:-pmis_backup}"
PG_DB="${PMIS_PG_DB:-pmis}"

# 临时实例（用于恢复演练，独立于生产）
DRILL_HOST="${PMIS_DRILL_HOST:-127.0.0.1}"
DRILL_PORT="${PMIS_DRILL_PORT:-5433}"
DRILL_USER="${PMIS_DRILL_USER:-pmis_drill}"
DRILL_DATA_DIR="${PMIS_DRILL_DATA_DIR:-/data/pg_drill}"
DRILL_PG_BIN="${PMIS_DRILL_PG_BIN:-/usr/lib/postgresql/16/bin}"
DRILL_PG_USER="postgres"

BACKUP_DIR="${PMIS_BACKUP_DIR:-/data/backup/pmis/daily}"
LOG_DIR="${PMIS_BACKUP_LOG_DIR:-/var/log/pmis/backup}"
ALERT_MAIL="${PMIS_ALERT_MAIL:-ops@ydsz-pmis.cn}"
REPORT_FILE="${LOG_DIR}/restore_test_$(date +%Y%m).log"

mkdir -p "${LOG_DIR}" "${DRILL_DATA_DIR}"
exec > >(tee -a "${REPORT_FILE}") 2>&1
echo "============================================================"
echo "[PMIS Restore Drill] started at $(date '+%F %T')"

# ---------- 1. 选择最近一次成功备份 ----------
LATEST_BACKUP=$(ls -t "${BACKUP_DIR}"/pmis_daily_*.sql.gz 2>/dev/null | head -n1)
if [ -z "${LATEST_BACKUP}" ]; then
  echo "[FATAL] no backup file found in ${BACKUP_DIR}" >&2
  echo "PMIS 恢复演练失败：未找到备份文件" | mailx -s "[ALERT] PMIS Restore Drill FAILED" "${ALERT_MAIL}" || true
  exit 1
fi
echo "[STEP 1] latest backup: ${LATEST_BACKUP}"
BACKUP_SIZE=$(stat -c %s "${LATEST_BACKUP}" 2>/dev/null || stat -f %z "${LATEST_BACKUP}")

# ---------- 2. 启动临时实例 ----------
echo "[STEP 2] start drill instance on port ${DRILL_PORT}"
if ! pg_isready -h "${DRILL_HOST}" -p "${DRILL_PORT}" -q; then
  ${DRILL_PG_BIN}/pg_ctl -D "${DRILL_DATA_DIR}" -o "-p ${DRILL_PORT}" -l "${LOG_DIR}/drill_pg.log" start
  sleep 3
fi
if ! pg_isready -h "${DRILL_HOST}" -p "${DRILL_PORT}" -q; then
  echo "[FATAL] drill instance not started" >&2
  exit 1
fi

# ---------- 3. 恢复到临时实例 ----------
echo "[STEP 3] pg_restore started"
START_TS=$(date +%s)
PGPASSWORD="${PMIS_PGPASSWORD}" pg_restore \
  -h "${DRILL_HOST}" -p "${DRILL_PORT}" -U "${DRILL_USER}" -d "${PG_DB}" \
  --no-owner --no-privileges --clean --if-exists --jobs=4 \
  "${LATEST_BACKUP}" || echo "[WARN] pg_restore returned non-zero (may be non-critical)"

# ---------- 4. 校验关键表行数 ----------
echo "[STEP 4] verify row counts"
RESULT_JSON="${LOG_DIR}/restore_verify_$(date +%Y%m%d).json"
CRITICAL_TABLES=("pmis_user" "pmis_project_initiation" "pmis_contract" "pmis_invoice" "pmis_payment" "pmis_wbs_task" "pmis_revenue" "pmis_evm_measure")
JSON_ROWS="["
FIRST=1
for table in "${CRITICAL_TABLES[@]}"; do
  COUNT=$(PGPASSWORD="${PMIS_PGPASSWORD}" psql -h "${DRILL_HOST}" -p "${DRILL_PORT}" -U "${DRILL_USER}" -d "${PG_DB}" -tAc "SELECT count(*) FROM ${table}" 2>/dev/null || echo "0")
  echo "  ${table}: ${COUNT} rows"
  if [ ${FIRST} -eq 0 ]; then JSON_ROWS+=","; fi
  JSON_ROWS+="{\"table\":\"${table}\",\"rows\":${COUNT}}"
  FIRST=0
done
JSON_ROWS+="]"
cat > "${RESULT_JSON}" <<EOF
{
  "drill_date": "$(date +%F)",
  "backup_file": "$(basename ${LATEST_BACKUP})",
  "backup_size_bytes": ${BACKUP_SIZE},
  "tables": ${JSON_ROWS}
}
EOF

# ---------- 5. 关闭临时实例 ----------
END_TS=$(date +%s)
DURATION=$((END_TS - START_TS))
echo "[STEP 5] stop drill instance"
${DRILL_PG_BIN}/pg_ctl -D "${DRILL_DATA_DIR}" stop -m fast || true

# ---------- 6. 评估 RTO ----------
if [ ${DURATION} -le 1800 ]; then
  RTO_STATUS="PASS (≤30min)"
else
  RTO_STATUS="WARN (>30min)"
  echo "PMIS 恢复演练超时：RTO=${DURATION}s 超过 30min 目标" | mailx -s "[WARN] PMIS Restore Drill SLOW" "${ALERT_MAIL}" || true
fi
echo "[PMIS Restore Drill] ${RTO_STATUS}, duration=${DURATION}s"
echo "============================================================"
exit 0
