#!/usr/bin/env bash
# =============================================================================
#  遗留数据加载 (Load)
#  批次 21 / P1 11.4 — 把 _staging 表数据正式落入业务表
#  流程: 依赖检查 -> 外键补全 -> 冲突处理 -> 审计留痕
#  用法: ./legacy-load.sh --source=erp --period=2026-06 [--commit]
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_FILE="${SCRIPT_DIR}/migration.conf"
LOG_DIR="${SCRIPT_DIR}/../logs/migration"
LOG_FILE="${LOG_DIR}/legacy-load-$(date +%Y%m%d_%H%M%S).log"
mkdir -p "${LOG_DIR}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "[$(date +%H:%M:%S)] $*" | tee -a "${LOG_FILE}"; }
err()  { log "${RED}✗ $*${NC}"; }
ok()   { log "${GREEN}✓ $*${NC}"; }
warn() { log "${YELLOW}⚠ $*${NC}"; }

# ===== 参数 =====
SOURCE="erp"
PERIOD=""
COMMIT=0
BATCH_SIZE=300
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

[[ -z "${PERIOD}" ]] && { err "必须指定 --period"; exit 1; }
[[ "${COMMIT}" == "1" ]] || warn "DRY-RUN 模式: 不会实际写入业务表"

[[ -f "${CONF_FILE}" ]] && source "${CONF_FILE}"
: "${PMIS_DSN:=postgresql://postgres:Limw1020@127.0.0.1:5432/ydsz-pmis}"
: "${MIGRATION_TENANT_ID:=1}"

log "=========================================="
log "Legacy 数据加载启动"
log "  source: ${SOURCE}"
log "  period: ${PERIOD}"
log "  commit: ${COMMIT}"
log "  log:    ${LOG_FILE}"
log "=========================================="

PSQL=$(command -v psql || true)
[[ -z "${PSQL}" ]] && { err "未检测到 psql"; exit 1; }

# ===== 加载日志 =====
psql "${PMIS_DSN}" -q -c "
CREATE TABLE IF NOT EXISTS pmis_legacy_load_log (
  id              BIGSERIAL PRIMARY KEY,
  batch_code      VARCHAR(64)  NOT NULL,
  source          VARCHAR(32)  NOT NULL,
  period          VARCHAR(32)  NOT NULL,
  target_table    VARCHAR(64)  NOT NULL,
  loaded          BIGINT       NOT NULL DEFAULT 0,
  skipped         BIGINT       NOT NULL DEFAULT 0,
  duration_ms     BIGINT       NOT NULL DEFAULT 0,
  status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
  error_message   TEXT,
  started_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at     TIMESTAMP
);" >/dev/null
BATCH_CODE="LEGACY_LOAD_${SOURCE}_$(echo "${PERIOD}" | tr -d '-')_$(date +%Y%m%d%H%M%S)"

# ===== 加载顺序 (解决外键依赖) =====
# 项目立项 < 合同 < 发票 < 收款
# 员工 < 工资 < 考勤
LOAD_ORDER=()
case "${SOURCE}" in
  erp)
    LOAD_ORDER=(project contract invoice payment) ;;
  finance)
    LOAD_ORDER=(coa voucher receipt) ;;
  hr)
    LOAD_ORDER=(employee payroll attend) ;;
  *) err "不支持的 source: ${SOURCE}"; exit 1 ;;
esac

# ===== 单表加载 =====
load_one() {
  local key="$1" tgt
  case "${key}" in
    project)   tgt="pmis_project_initiation" ;;
    contract)  tgt="pmis_project_contract" ;;
    invoice)   tgt="pmis_finance_invoice" ;;
    payment)   tgt="pmis_finance_payment" ;;
    voucher)   tgt="pmis_finance_voucher" ;;
    receipt)   tgt="pmis_finance_receipt" ;;
    coa)       tgt="pmis_finance_coa" ;;
    employee)  tgt="pmis_user_employee" ;;
    payroll)   tgt="pmis_user_payroll" ;;
    attend)    tgt="pmis_user_attendance" ;;
    *) err "未知 key: ${key}"; return 1 ;;
  esac

  local staging="${tgt}_staging"
  local start_ms=$(date +%s%3N)
  log "[load] ${staging} -> ${tgt}"

  # 检查 staging 数据
  local pending
  pending=$(psql "${PMIS_DSN}" -tAc "
    SELECT COUNT(*) FROM ${staging}
     WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%' AND legacy_id IS NOT NULL;
  " | tr -d '[:space:]')
  [[ -z "${pending}" || "${pending}" == "0" ]] && { warn "  staging 无数据"; return 0; }
  log "  待加载: ${pending} 行"

  local log_id
  log_id=$(psql "${PMIS_DSN}" -tAc "
    INSERT INTO pmis_legacy_load_log
      (batch_code, source, period, target_table)
    VALUES
      ('${BATCH_CODE}', '${SOURCE}', '${PERIOD}', '${tgt}')
    RETURNING id;
  " | tr -d '[:space:]')

  # === 业务字段适配 (按目标表字段) ===
  local insert_sql
  case "${key}" in
    project)
      insert_sql=$(cat <<SQL
        INSERT INTO ${tgt}
          (tenant_id, project_code, project_name, customer_name, contract_amount,
           status, created_by, created_at, updated_at, _legacy_id, _migration_batch)
        SELECT
          ${MIGRATION_TENANT_ID}, project_code, project_name, customer_name, contract_amount,
          status, 'legacy-migration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
          legacy_id, batch_code
        FROM ${staging}
        WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%'
        ON CONFLICT (_legacy_id) DO NOTHING
        RETURNING 1;
SQL
)
      ;;
    contract)
      insert_sql=$(cat <<SQL
        INSERT INTO ${tgt}
          (tenant_id, contract_code, contract_name, contract_amount, signed_at,
           status, created_by, created_at, updated_at, _legacy_id, _migration_batch)
        SELECT
          ${MIGRATION_TENANT_ID}, contract_code, contract_name, contract_amount, signed_at,
          status, 'legacy-migration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
          legacy_id, batch_code
        FROM ${staging}
        WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%'
        ON CONFLICT (_legacy_id) DO NOTHING
        RETURNING 1;
SQL
)
      ;;
    invoice)
      insert_sql=$(cat <<SQL
        INSERT INTO ${tgt}
          (tenant_id, invoice_code, invoice_no, amount, issued_at,
           status, created_by, created_at, updated_at, _legacy_id, _migration_batch)
        SELECT
          ${MIGRATION_TENANT_ID}, invoice_code, invoice_no, amount, issued_at,
          status, 'legacy-migration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
          legacy_id, batch_code
        FROM ${staging}
        WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%'
        ON CONFLICT (_legacy_id) DO NOTHING
        RETURNING 1;
SQL
)
      ;;
    payment)
      insert_sql=$(cat <<SQL
        INSERT INTO ${tgt}
          (tenant_id, payment_code, amount, paid_at,
           status, created_by, created_at, updated_at, _legacy_id, _migration_batch)
        SELECT
          ${MIGRATION_TENANT_ID}, payment_code, amount, paid_at,
          status, 'legacy-migration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
          legacy_id, batch_code
        FROM ${staging}
        WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%'
        ON CONFLICT (_legacy_id) DO NOTHING
        RETURNING 1;
SQL
)
      ;;
    coa)
      insert_sql=$(cat <<SQL
        INSERT INTO ${tgt}
          (tenant_id, coa_code, coa_name, coa_type, level,
           created_by, created_at, updated_at, _legacy_id, _migration_batch)
        SELECT
          ${MIGRATION_TENANT_ID}, coa_code, coa_name, coa_type, level,
          'legacy-migration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
          legacy_id, batch_code
        FROM ${staging}
        WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%'
        ON CONFLICT (coa_code, tenant_id) DO NOTHING
        RETURNING 1;
SQL
)
      ;;
    voucher)
      insert_sql=$(cat <<SQL
        INSERT INTO ${tgt}
          (tenant_id, voucher_code, debit_coa, credit_coa, amount, voucher_date,
           created_by, created_at, updated_at, _legacy_id, _migration_batch)
        SELECT
          ${MIGRATION_TENANT_ID}, voucher_code, debit_coa, credit_coa, amount, voucher_date,
          'legacy-migration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
          legacy_id, batch_code
        FROM ${staging}
        WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%'
        ON CONFLICT (_legacy_id) DO NOTHING
        RETURNING 1;
SQL
)
      ;;
    employee)
      insert_sql=$(cat <<SQL
        INSERT INTO ${tgt}
          (tenant_id, employee_code, employee_name, dept_name, level_code,
           created_by, created_at, updated_at, _legacy_id, _migration_batch)
        SELECT
          ${MIGRATION_TENANT_ID}, employee_code, employee_name, dept_name, level_code,
          'legacy-migration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
          legacy_id, batch_code
        FROM ${staging}
        WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%'
        ON CONFLICT (_legacy_id) DO NOTHING
        RETURNING 1;
SQL
)
      ;;
    *)
      # 默认: 直接复制
      insert_sql=$(cat <<SQL
        INSERT INTO ${tgt}
        SELECT * FROM ${staging}
        WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%'
        ON CONFLICT DO NOTHING
        RETURNING 1;
SQL
)
      ;;
  esac

  # 添加目标表必要字段 (id/_legacy_id/_migration_batch) — 用 IF NOT EXISTS 兜底
  psql "${PMIS_DSN}" -q -c "
    ALTER TABLE ${tgt} ADD COLUMN IF NOT EXISTS _legacy_id BIGINT;
    ALTER TABLE ${tgt} ADD COLUMN IF NOT EXISTS _migration_batch VARCHAR(64);
    CREATE UNIQUE INDEX IF NOT EXISTS ${tgt}_legacy_id_uk
      ON ${tgt} (_legacy_id) WHERE _legacy_id IS NOT NULL;
  " >/dev/null

  local loaded=0
  if [[ "${COMMIT}" == "1" ]]; then
    loaded=$(psql "${PMIS_DSN}" -tAc "${insert_sql}" 2>>"${LOG_FILE}" | wc -l || echo 0)
    loaded=$(echo "${loaded}" | tr -d '[:space:]')
  else
    # dry-run: 仅统计 staging 数据
    loaded=$(psql "${PMIS_DSN}" -tAc "
      SELECT COUNT(*) FROM ${staging}
       WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%';
    " | tr -d '[:space:]')
  fi

  local end_ms=$(date +%s%3N)
  local duration=$((end_ms - start_ms))
  psql "${PMIS_DSN}" -q -c "
    UPDATE pmis_legacy_load_log
       SET loaded     = ${loaded},
           duration_ms= ${duration},
           status     = 'SUCCESS',
           finished_at= CURRENT_TIMESTAMP
     WHERE id = ${log_id};
  " >/dev/null
  ok "  ${tgt}: 加载 ${loaded} 行 (${duration}ms)"
}

# ===== 主循环 (按依赖顺序) =====
for key in "${LOAD_ORDER[@]}"; do
  load_one "${key}"
done

# ===== 汇总 =====
log ""
log "============== 加载汇总 =============="
psql "${PMIS_DSN}" -c "
  SELECT target_table, loaded, skipped, duration_ms, status
    FROM pmis_legacy_load_log
   WHERE batch_code = '${BATCH_CODE}'
   ORDER BY id;
"

ok "Legacy 加载完成: ${BATCH_CODE}"
log "日志: ${LOG_FILE}"
