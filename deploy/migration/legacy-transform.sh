#!/usr/bin/env bash
# =============================================================================
#  遗留数据转换 (Transform)
#  批次 21 / P1 11.4 — 把暂存表 _raw_payload(JSONB) 转换为 PMIS 业务实体
#  用法: ./legacy-transform.sh --source=erp --period=2026-06 [--commit]
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_FILE="${SCRIPT_DIR}/migration.conf"
LOG_DIR="${SCRIPT_DIR}/../logs/migration"
LOG_FILE="${LOG_DIR}/legacy-transform-$(date +%Y%m%d_%H%M%S).log"
mkdir -p "${LOG_DIR}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "[$(date +%H:%M:%S)] $*" | tee -a "${LOG_FILE}"; }
err()  { log "${RED}✗ $*${NC}"; }
ok()   { log "${GREEN}✓ $*${NC}"; }
warn() { log "${YELLOW}⚠ $*${NC}"; }

# ===== 参数解析 =====
SOURCE="erp"
PERIOD=""
COMMIT=0         # 默认 dry-run, 写 _staging 表不写业务表
BATCH_SIZE=500
RESUME=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source=*)   SOURCE="${1#*=}" ;;
    --period=*)   PERIOD="${1#*=}" ;;
    --batchSize=*) BATCH_SIZE="${1#*=}" ;;
    --commit)     COMMIT=1 ;;
    --resume)     RESUME=1 ;;
    -h|--help)    grep -E '^#( |$)' "${BASH_SOURCE[0]}" | sed 's/^# //; s/^#$//'; exit 0 ;;
    *) err "未知参数: $1"; exit 1 ;;
  esac
  shift
done

if [[ -z "${PERIOD}" ]]; then err "必须指定 --period=YYYY-MM"; exit 1; fi
[[ "${COMMIT}" == "1" ]] || warn "DRY-RUN 模式: 数据仅写入 _staging, 不会修改业务表"

[[ -f "${CONF_FILE}" ]] && source "${CONF_FILE}"
: "${PMIS_DSN:=postgresql://postgres:Limw1020@127.0.0.1:5432/ydsz-pmis}"

log "=========================================="
log "Legacy 数据转换启动"
log "  source: ${SOURCE}"
log "  period: ${PERIOD}"
log "  commit: ${COMMIT}"
log "  log:    ${LOG_FILE}"
log "=========================================="

PSQL=$(command -v psql || true)
[[ -z "${PSQL}" ]] && { err "未检测到 psql"; exit 1; }

# ===== 转换日志 =====
psql "${PMIS_DSN}" -q -c "
CREATE TABLE IF NOT EXISTS pmis_legacy_transform_log (
  id              BIGSERIAL PRIMARY KEY,
  batch_code      VARCHAR(64)  NOT NULL,
  source          VARCHAR(32)  NOT NULL,
  period          VARCHAR(32)  NOT NULL,
  target_table    VARCHAR(64)  NOT NULL,
  transformed     BIGINT       NOT NULL DEFAULT 0,
  failed          BIGINT       NOT NULL DEFAULT 0,
  duration_ms     BIGINT       NOT NULL DEFAULT 0,
  status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
  error_message   TEXT,
  started_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at     TIMESTAMP
);" >/dev/null
BATCH_CODE="LEGACY_TRANSFORM_${SOURCE}_$(echo "${PERIOD}" | tr -d '-')_$(date +%Y%m%d%H%M%S)"

# ===== 转换规则 (字段映射 + 数据清洗) =====
# 通用规则:
#   1) 数字: ::numeric
#   2) 字符串: btrim / nullif
#   3) 日期: ::date
#   4) 枚举: CASE WHEN code IN (...) THEN 'NORMAL' ELSE 'PENDING'

declare -A SOURCE_TABLES
declare -A TARGET_TABLES
case "${SOURCE}" in
  erp)
    SOURCE_TABLES=(
      [project] ="pmis_stage_legacy_project"
      [contract]="pmis_stage_legacy_contract"
      [invoice] ="pmis_stage_legacy_invoice"
      [payment] ="pmis_stage_legacy_payment"
    )
    TARGET_TABLES=(
      [project] ="pmis_project_initiation"
      [contract]="pmis_project_contract"
      [invoice] ="pmis_finance_invoice"
      [payment] ="pmis_finance_payment"
    )
    ;;
  finance)
    SOURCE_TABLES=(
      [voucher]="pmis_stage_legacy_voucher"
      [receipt]="pmis_stage_legacy_receipt"
      [coa]    ="pmis_stage_legacy_coa"
    )
    TARGET_TABLES=(
      [voucher]="pmis_finance_voucher"
      [receipt]="pmis_finance_receipt"
      [coa]    ="pmis_finance_coa"
    )
    ;;
  hr)
    SOURCE_TABLES=(
      [employee]="pmis_stage_legacy_employee"
      [payroll] ="pmis_stage_legacy_payroll"
      [attend]  ="pmis_stage_legacy_attendance"
    )
    TARGET_TABLES=(
      [employee]="pmis_user_employee"
      [payroll] ="pmis_user_payroll"
      [attend]  ="pmis_user_attendance"
    )
    ;;
  *)
    err "不支持的 source: ${SOURCE}"; exit 1 ;;
esac

# ===== 转换单个表 =====
transform_one() {
  local key="$1" src="${SOURCE_TABLES[$1]}" tgt="${TARGET_TABLES[$1]}"
  local start_ms=$(date +%s%3N)
  log "[transform] ${src} -> ${tgt}"

  # 检查源表数据
  local pending
  pending=$(psql "${PMIS_DSN}" -tAc "
    SELECT COUNT(*) FROM ${src}
     WHERE _transformed = FALSE OR _transformed IS NULL;
  " | tr -d '[:space:]')
  [[ "${pending}" == "0" || -z "${pending}" ]] && { warn "  无待转换数据"; return 0; }
  log "  待转换: ${pending} 行"

  local log_id
  log_id=$(psql "${PMIS_DSN}" -tAc "
    INSERT INTO pmis_legacy_transform_log
      (batch_code, source, period, target_table)
    VALUES
      ('${BATCH_CODE}', '${SOURCE}', '${PERIOD}', '${tgt}')
    RETURNING id;
  " | tr -d '[:space:]')

  # 转换 SQL (按 key 分发)
  local sql
  case "${key}" in
    project)
      sql=$(cat <<SQL
        INSERT INTO ${tgt}_staging (
          legacy_id, project_code, project_name, customer_name,
          contract_amount, status, period, batch_code
        )
        SELECT
          (_raw_payload->>'id')::bigint,
          UPPER(btrim(_raw_payload->>'code')),
          btrim(_raw_payload->>'name'),
          btrim(_raw_payload->>'customer_name'),
          COALESCE(NULLIF(_raw_payload->>'amount', ''), '0')::numeric(18,2),
          CASE
            WHEN _raw_payload->>'status' IN ('ACTIVE','CLOSED','PENDING')
              THEN _raw_payload->>'status'
            ELSE 'PENDING'
          END,
          '${PERIOD}',
          '${BATCH_CODE}'
        FROM ${src}
        WHERE _transformed = FALSE OR _transformed IS NULL
        LIMIT ${BATCH_SIZE};
SQL
)
      ;;
    contract)
      sql=$(cat <<SQL
        INSERT INTO ${tgt}_staging (
          legacy_id, contract_code, contract_name, contract_amount,
          signed_at, status, period, batch_code
        )
        SELECT
          (_raw_payload->>'id')::bigint,
          UPPER(btrim(_raw_payload->>'code')),
          btrim(_raw_payload->>'name'),
          COALESCE(NULLIF(_raw_payload->>'amount', ''), '0')::numeric(18,2),
          COALESCE(NULLIF(_raw_payload->>'signed_at', ''), CURRENT_DATE)::date,
          CASE
            WHEN _raw_payload->>'status' IN ('SIGNED','TERMINATED','DRAFT')
              THEN _raw_payload->>'status'
            ELSE 'DRAFT'
          END,
          '${PERIOD}',
          '${BATCH_CODE}'
        FROM ${src}
        WHERE _transformed = FALSE OR _transformed IS NULL
        LIMIT ${BATCH_SIZE};
SQL
)
      ;;
    invoice)
      sql=$(cat <<SQL
        INSERT INTO ${tgt}_staging (
          legacy_id, invoice_code, invoice_no, amount, issued_at, status, period, batch_code
        )
        SELECT
          (_raw_payload->>'id')::bigint,
          UPPER(btrim(_raw_payload->>'code')),
          btrim(_raw_payload->>'invoice_no'),
          COALESCE(NULLIF(_raw_payload->>'amount', ''), '0')::numeric(18,2),
          COALESCE(NULLIF(_raw_payload->>'issued_at', ''), CURRENT_DATE)::date,
          CASE
            WHEN _raw_payload->>'status' IN ('ISSUED','CANCELLED','PAID')
              THEN _raw_payload->>'status'
            ELSE 'PENDING'
          END,
          '${PERIOD}',
          '${BATCH_CODE}'
        FROM ${src}
        WHERE _transformed = FALSE OR _transformed IS NULL
        LIMIT ${BATCH_SIZE};
SQL
)
      ;;
    payment)
      sql=$(cat <<SQL
        INSERT INTO ${tgt}_staging (
          legacy_id, payment_code, amount, paid_at, status, period, batch_code
        )
        SELECT
          (_raw_payload->>'id')::bigint,
          UPPER(btrim(_raw_payload->>'code')),
          COALESCE(NULLIF(_raw_payload->>'amount', ''), '0')::numeric(18,2),
          COALESCE(NULLIF(_raw_payload->>'paid_at', ''), CURRENT_DATE)::date,
          CASE
            WHEN _raw_payload->>'status' IN ('RECEIVED','PENDING','FAILED')
              THEN _raw_payload->>'status'
            ELSE 'PENDING'
          END,
          '${PERIOD}',
          '${BATCH_CODE}'
        FROM ${src}
        WHERE _transformed = FALSE OR _transformed IS NULL
        LIMIT ${BATCH_SIZE};
SQL
)
      ;;
    voucher)
      sql=$(cat <<SQL
        INSERT INTO ${tgt}_staging (
          legacy_id, voucher_code, debit_coa, credit_coa, amount, voucher_date, period, batch_code
        )
        SELECT
          (_raw_payload->>'id')::bigint,
          UPPER(btrim(_raw_payload->>'code')),
          btrim(_raw_payload->>'debit_coa'),
          btrim(_raw_payload->>'credit_coa'),
          COALESCE(NULLIF(_raw_payload->>'amount', ''), '0')::numeric(18,2),
          COALESCE(NULLIF(_raw_payload->>'voucher_date', ''), CURRENT_DATE)::date,
          '${PERIOD}',
          '${BATCH_CODE}'
        FROM ${src}
        WHERE _transformed = FALSE OR _transformed IS NULL
        LIMIT ${BATCH_SIZE};
SQL
)
      ;;
    coa)
      sql=$(cat <<SQL
        INSERT INTO ${tgt}_staging (
          legacy_id, coa_code, coa_name, coa_type, level, period, batch_code
        )
        SELECT
          (_raw_payload->>'id')::bigint,
          btrim(_raw_payload->>'code'),
          btrim(_raw_payload->>'name'),
          UPPER(btrim(_raw_payload->>'type')),
          COALESCE(NULLIF(_raw_payload->>'level', ''), '1')::int,
          '${PERIOD}',
          '${BATCH_CODE}'
        FROM ${src}
        WHERE _transformed = FALSE OR _transformed IS NULL
        LIMIT ${BATCH_SIZE};
SQL
)
      ;;
    employee)
      sql=$(cat <<SQL
        INSERT INTO ${tgt}_staging (
          legacy_id, employee_code, employee_name, dept_name, level_code, period, batch_code
        )
        SELECT
          (_raw_payload->>'id')::bigint,
          UPPER(btrim(_raw_payload->>'code')),
          btrim(_raw_payload->>'name'),
          btrim(_raw_payload->>'dept_name'),
          UPPER(btrim(_raw_payload->>'level')),
          '${PERIOD}',
          '${BATCH_CODE}'
        FROM ${src}
        WHERE _transformed = FALSE OR _transformed IS NULL
        LIMIT ${BATCH_SIZE};
SQL
)
      ;;
    *)
      warn "  无内置转换规则, 透传 _raw_payload"
      sql=$(cat <<SQL
        INSERT INTO ${tgt}_staging (legacy_id, raw_payload, period, batch_code)
        SELECT (_raw_payload->>'id')::bigint, _raw_payload, '${PERIOD}', '${BATCH_CODE}'
        FROM ${src}
        WHERE _transformed = FALSE OR _transformed IS NULL
        LIMIT ${BATCH_SIZE};
SQL
)
      ;;
  esac

  # 创建 _staging 表 (若不存在, 沿用目标表结构但追加 staging 字段)
  psql "${PMIS_DSN}" -q -c "
    CREATE TABLE IF NOT EXISTS ${tgt}_staging (LIKE ${tgt} INCLUDING ALL);
    ALTER TABLE ${tgt}_staging
      ADD COLUMN IF NOT EXISTS legacy_id BIGINT,
      ADD COLUMN IF NOT EXISTS period VARCHAR(32),
      ADD COLUMN IF NOT EXISTS batch_code VARCHAR(64);
  " >/dev/null

  local inserted
  inserted=$(psql "${PMIS_DSN}" -tAc "${sql}" 2>>"${LOG_FILE}" | wc -l || echo 0)
  inserted=$(echo "${inserted}" | tr -d '[:space:]')

  if [[ "${COMMIT}" == "1" ]]; then
    # 真提交: 拷贝 staging 到目标表 (用 ON CONFLICT DO NOTHING 幂等)
    psql "${PMIS_DSN}" -q -c "
      INSERT INTO ${tgt}
      SELECT * FROM ${tgt}_staging
      WHERE batch_code = '${BATCH_CODE}'
      ON CONFLICT DO NOTHING;
    " >/dev/null
  fi

  # 标记已转换
  psql "${PMIS_DSN}" -q -c "
    UPDATE ${src}
       SET _transformed = TRUE
     WHERE _raw_payload->>'id' IN (
       SELECT legacy_id::text FROM ${tgt}_staging WHERE batch_code = '${BATCH_CODE}'
     );
  " >/dev/null

  local end_ms=$(date +%s%3N)
  local duration=$((end_ms - start_ms))
  psql "${PMIS_DSN}" -q -c "
    UPDATE pmis_legacy_transform_log
       SET transformed  = ${inserted},
           duration_ms  = ${duration},
           status       = 'SUCCESS',
           finished_at  = CURRENT_TIMESTAMP
     WHERE id = ${log_id};
  " >/dev/null
  ok "  ${tgt}: 转换 ${inserted} 行 (${duration}ms)"
}

# ===== 主循环 =====
for key in "${!SOURCE_TABLES[@]}"; do
  transform_one "${key}"
done

# ===== 汇总 =====
log ""
log "============== 转换汇总 =============="
psql "${PMIS_DSN}" -c "
  SELECT target_table, transformed, failed, duration_ms, status
    FROM pmis_legacy_transform_log
   WHERE batch_code = '${BATCH_CODE}'
   ORDER BY id;
"

ok "Legacy 转换完成: ${BATCH_CODE}"
log "日志: ${LOG_FILE}"
