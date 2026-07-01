#!/usr/bin/env bash
# =============================================================================
#  遗留系统数据抽取 (Legacy Extract)
#  批次 21 / P1 11.4 — 从老系统(ERP/财务/HR)抽取数据到 PMIS 暂存表
#  用法: ./legacy-extract.sh --source=erp --period=2026-06
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_FILE="${SCRIPT_DIR}/migration.conf"
LOG_DIR="${SCRIPT_DIR}/../logs/migration"
LOG_FILE="${LOG_DIR}/legacy-extract-$(date +%Y%m%d_%H%M%S).log"

mkdir -p "${LOG_DIR}"

# 颜色输出
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "[$(date +%H:%M:%S)] $*" | tee -a "${LOG_FILE}"; }
err()  { log "${RED}✗ $*${NC}"; }
ok()   { log "${GREEN}✓ $*${NC}"; }
warn() { log "${YELLOW}⚠ $*${NC}"; }

# ===== 参数解析 =====
SOURCE="erp"        # erp | finance | hr
PERIOD=""           # 2026-06 / 2026-Q2 / 2026-H1
BATCH_SIZE=2000
DRY_RUN=0
RESUME=0
LEGACY_DSN=""
LEGACY_SCHEMA="legacy"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source=*)   SOURCE="${1#*=}" ;;
    --period=*)   PERIOD="${1#*=}" ;;
    --batchSize=*) BATCH_SIZE="${1#*=}" ;;
    --dry-run)    DRY_RUN=1 ;;
    --resume)     RESUME=1 ;;
    --dsn=*)      LEGACY_DSN="${1#*=}" ;;
    --schema=*)   LEGACY_SCHEMA="${1#*=}" ;;
    -h|--help)
      grep -E '^#( |$)' "${BASH_SOURCE[0]}" | sed 's/^# //; s/^#$//'
      exit 0
      ;;
    *) err "未知参数: $1"; exit 1 ;;
  esac
  shift
done

if [[ -z "${PERIOD}" ]]; then
  err "必须指定 --period=YYYY-MM | YYYY-Qn | YYYY-Hn"
  exit 1
fi

# ===== 加载配置 =====
if [[ -f "${CONF_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${CONF_FILE}"
fi
: "${PMIS_DSN:=postgresql://postgres:Limw1020@127.0.0.1:5432/ydsz-pmis}"

log "=========================================="
log "Legacy 数据抽取启动"
log "  source:   ${SOURCE}"
log "  period:   ${PERIOD}"
log "  batch:    ${BATCH_SIZE}"
log "  dryRun:   ${DRY_RUN}"
log "  resume:   ${RESUME}"
log "  log:      ${LOG_FILE}"
log "=========================================="

# ===== 工具检测 =====
PSQL=$(command -v psql || true)
if [[ -z "${PSQL}" ]]; then
  err "未检测到 psql 命令"
  exit 1
fi

# ===== 周期转日期范围 =====
period_to_dates() {
  local p="$1" from to
  case "${p}" in
    [0-9][0-9][0-9][0-9]-[0-9][0-9])
      from="${p}-01"
      to="$(date -d "${from} +1 month -1 day" +%Y-%m-%d)"
      ;;
    [0-9][0-9][0-9][0-9]-Q[1-4])
      local q="${p##*Q}"
      local y="${p%-Q*}"
      local m1 m2
      case "${q}" in
        1) m1=01; m2=03 ;;
        2) m1=04; m2=06 ;;
        3) m1=07; m2=09 ;;
        4) m1=10; m2=12 ;;
      esac
      from="${y}-${m1}-01"
      to="${y}-${m2}-$(date -d "${y}-${m2}-01 +1 month -1 day" +%d)"
      ;;
    [0-9][0-9][0-9][0-9]-H[1-2])
      local h="${p##*H}"
      local y="${p%-H*}"
      if [[ "${h}" == "1" ]]; then from="${y}-01-01"; to="${y}-06-30"
      else from="${y}-07-01"; to="${y}-12-31"; fi
      ;;
    *) err "周期格式非法: ${p}"; return 1 ;;
  esac
  echo "${from} ${to}"
}

read -r DATE_FROM DATE_TO < <(period_to_dates "${PERIOD}")
log "数据周期: ${DATE_FROM} ~ ${DATE_TO}"

# ===== 抽取任务表 =====
ensure_log_table() {
  local sql=$(cat <<'SQL'
CREATE TABLE IF NOT EXISTS pmis_legacy_extract_log (
  id              BIGSERIAL PRIMARY KEY,
  batch_code      VARCHAR(64)  NOT NULL,
  source          VARCHAR(32)  NOT NULL,
  period          VARCHAR(32)  NOT NULL,
  table_name      VARCHAR(64)  NOT NULL,
  extracted_rows  BIGINT       NOT NULL DEFAULT 0,
  duration_ms     BIGINT       NOT NULL DEFAULT 0,
  status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
  error_message   TEXT,
  started_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pmis_legacy_extract_log_batch
  ON pmis_legacy_extract_log (batch_code);
CREATE INDEX IF NOT EXISTS idx_pmis_legacy_extract_log_status
  ON pmis_legacy_extract_log (status);
SQL
)
  psql "${PMIS_DSN}" -q -c "${sql}" >/dev/null
}

ensure_log_table
BATCH_CODE="LEGACY_EXTRACT_${SOURCE}_$(echo "${PERIOD}" | tr -d '-')_$(date +%Y%m%d%H%M%S)"
ok "日志表已就绪, batch_code=${BATCH_CODE}"

# ===== 检查遗留 DSN =====
if [[ -z "${LEGACY_DSN}" ]]; then
  warn "未提供 --dsn, 模拟抽取模式: 生成 fixture 数据供 transform/load 联调"
  SIMULATE=1
else
  SIMULATE=0
  log "连接遗留库: ${LEGACY_DSN%%@*}@***"
fi

# ===== 抽取主流程 =====
extract_table() {
  local legacy_table="$1"
  local target_table="$2"
  local extract_sql="$3"

  local start_ms=$(date +%s%3N)
  local extract_id
  extract_id=$(psql "${PMIS_DSN}" -tAc "
    INSERT INTO pmis_legacy_extract_log
      (batch_code, source, period, table_name)
    VALUES
      ('${BATCH_CODE}', '${SOURCE}', '${PERIOD}', '${target_table}')
    RETURNING id;
  " 2>>"${LOG_FILE}" || echo "0")
  extract_id=$(echo "${extract_id}" | tr -d '[:space:]')

  log "[extract] ${legacy_table} -> ${target_table}"

  # 幂等: 已抽取批次跳过
  if [[ "${RESUME}" == "1" ]]; then
    local existed
    existed=$(psql "${PMIS_DSN}" -tAc "
      SELECT COUNT(*) FROM pmis_legacy_extract_log
       WHERE source='${SOURCE}' AND period='${PERIOD}'
         AND table_name='${target_table}' AND status='SUCCESS';
    ")
    if [[ "${existed:-0}" -gt 0 ]]; then
      warn "已存在成功批次, 跳过 (resume mode)"
      return 0
    fi
  fi

  # 创建目标暂存表
  psql "${PMIS_DSN}" -q -c "
    CREATE TABLE IF NOT EXISTS ${target_table} (
      _legacy_id     BIGINT,
      _batch_code    VARCHAR(64),
      _extracted_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      _raw_payload   JSONB,
      _transformed   BOOLEAN DEFAULT FALSE
    );
  " >/dev/null

  if [[ "${DRY_RUN}" == "1" ]]; then
    warn "DRY-RUN: 跳过实际写入"
    return 0
  fi

  local row_count=0
  if [[ "${SIMULATE}" == "1" ]]; then
    # 模拟数据: 按月生成 100~200 条
    row_count=$((RANDOM % 100 + 100))
    psql "${PMIS_DSN}" -q -c "
      INSERT INTO ${target_table} (_legacy_id, _batch_code, _raw_payload)
      SELECT
        g,
        '${BATCH_CODE}',
        jsonb_build_object(
          'id', g,
          'period', '${PERIOD}',
          'amount', (random() * 1000000)::numeric(18,2),
          'createdAt', (timestamp '${DATE_FROM}' +
                        (random() * ('${DATE_TO}'::timestamp - '${DATE_FROM}'::timestamp))),
          'note', 'simulated row ' || g
        )
      FROM generate_series(1, ${row_count}) g;
    " >/dev/null
  else
    # 真抽: 使用 dblink_fdw 或外部表
    row_count=$(psql "${PMIS_DSN}" -tAc "
      INSERT INTO ${target_table} (_legacy_id, _batch_code, _raw_payload)
      SELECT id, '${BATCH_CODE}', to_jsonb(t)
        FROM dblink('${LEGACY_DSN}',
                   'SELECT * FROM ${LEGACY_SCHEMA}.${legacy_table}
                     WHERE created_at >= ''${DATE_FROM}''
                       AND created_at <  ''${DATE_TO}''::date + 1')
            AS t(id BIGINT, payload JSONB)
      RETURNING 1;
    " 2>>"${LOG_FILE}" | wc -l || echo 0)
    row_count=$(echo "${row_count}" | tr -d '[:space:]')
  fi

  local end_ms=$(date +%s%3N)
  local duration=$((end_ms - start_ms))

  psql "${PMIS_DSN}" -q -c "
    UPDATE pmis_legacy_extract_log
       SET extracted_rows = ${row_count},
           duration_ms     = ${duration},
           status          = 'SUCCESS',
           finished_at     = CURRENT_TIMESTAMP
     WHERE id = ${extract_id};
  " >/dev/null
  ok "  ${target_table}: ${row_count} 行 (${duration}ms)"
}

# ===== 按 source 分发 =====
case "${SOURCE}" in
  erp)
    extract_table "erp_project"  "pmis_stage_legacy_project"
    extract_table "erp_contract" "pmis_stage_legacy_contract"
    extract_table "erp_invoice"  "pmis_stage_legacy_invoice"
    extract_table "erp_payment"  "pmis_stage_legacy_payment"
    ;;
  finance)
    extract_table "fin_voucher"  "pmis_stage_legacy_voucher"
    extract_table "fin_receipt"  "pmis_stage_legacy_receipt"
    extract_table "fin_coa"      "pmis_stage_legacy_coa"
    ;;
  hr)
    extract_table "hr_employee"  "pmis_stage_legacy_employee"
    extract_table "hr_payroll"   "pmis_stage_legacy_payroll"
    extract_table "hr_attend"    "pmis_stage_legacy_attendance"
    ;;
  *)
    err "不支持的 source: ${SOURCE}, 可选 erp | finance | hr"
    exit 1
    ;;
esac

# ===== 汇总 =====
log ""
log "============== 抽取汇总 =============="
psql "${PMIS_DSN}" -c "
  SELECT table_name, extracted_rows, duration_ms, status, started_at
    FROM pmis_legacy_extract_log
   WHERE batch_code = '${BATCH_CODE}'
   ORDER BY id;
"

ok "Legacy 抽取完成: ${BATCH_CODE}"
log "日志: ${LOG_FILE}"
