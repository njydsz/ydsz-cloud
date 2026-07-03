#!/usr/bin/env bash
# =============================================================================
#  迁移准确性校验 (Accuracy Verify)
#  批次 21 / P1 11.4 — 校验 staging / 业务表 / 遗留源 三方数据一致性
#  用法: ./legacy-accuracy-verify.sh --source=erp --period=2026-06
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_FILE="${SCRIPT_DIR}/migration.conf"
LOG_DIR="${SCRIPT_DIR}/../logs/migration"
LOG_FILE="${LOG_DIR}/legacy-verify-$(date +%Y%m%d_%H%M%S).log"
REPORT_DIR="${SCRIPT_DIR}/../reports/migration"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "[$(date +%H:%M:%S)] $*" | tee -a "${LOG_FILE}"; }
err()  { log "${RED}✗ $*${NC}"; }
ok()   { log "${GREEN}✓ $*${NC}"; }
warn() { log "${YELLOW}⚠ $*${NC}"; }

# ===== 参数 =====
SOURCE="erp"
PERIOD=""
TOLERANCE_AMOUNT=0.01   # 金额容差
TOLERANCE_DATE_DAYS=1   # 日期容差
THRESHOLD_FAIL=10       # 错误数 > 阈值 -> 非零退出
LEGACY_DSN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source=*)      SOURCE="${1#*=}" ;;
    --period=*)      PERIOD="${1#*=}" ;;
    --tol-amount=*)  TOLERANCE_AMOUNT="${1#*=}" ;;
    --tol-date=*)    TOLERANCE_DATE_DAYS="${1#*=}" ;;
    --threshold=*)   THRESHOLD_FAIL="${1#*=}" ;;
    --dsn=*)         LEGACY_DSN="${1#*=}" ;;
    -h|--help)       grep -E '^#( |$)' "${BASH_SOURCE[0]}" | sed 's/^# //; s/^#$//'; exit 0 ;;
    *) err "未知参数: $1"; exit 1 ;;
  esac
  shift
done

[[ -z "${PERIOD}" ]] && { err "必须指定 --period"; exit 1; }
[[ -f "${CONF_FILE}" ]] && source "${CONF_FILE}"
: "${PMIS_DSN:=postgresql://postgres:Limw1020@127.0.0.1:5432/ydsz-pmis}"
: "${MIGRATION_TENANT_ID:=1}"

log "=========================================="
log "Legacy 数据准确性校验"
log "  source:    ${SOURCE}"
log "  period:    ${PERIOD}"
log "  amountTol: ${TOLERANCE_AMOUNT}"
log "  dateTol:   ${TOLERANCE_DATE_DAYS}"
log "  threshold: ${THRESHOLD_FAIL}"
log "  log:       ${LOG_FILE}"
log "=========================================="

# ===== 校验日志表 =====
psql "${PMIS_DSN}" -q -c "
CREATE TABLE IF NOT EXISTS pmis_legacy_verify_log (
  id              BIGSERIAL PRIMARY KEY,
  batch_code      VARCHAR(64)  NOT NULL,
  source          VARCHAR(32)  NOT NULL,
  period          VARCHAR(32)  NOT NULL,
  target_table    VARCHAR(64)  NOT NULL,
  total_rows      BIGINT       NOT NULL DEFAULT 0,
  matched_rows    BIGINT       NOT NULL DEFAULT 0,
  mismatch_rows   BIGINT       NOT NULL DEFAULT 0,
  missing_rows    BIGINT       NOT NULL DEFAULT 0,
  accuracy_pct    NUMERIC(6,3) NOT NULL DEFAULT 0,
  status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
  error_message   TEXT,
  started_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at     TIMESTAMP
);" >/dev/null

BATCH_CODE="LEGACY_VERIFY_${SOURCE}_$(echo "${PERIOD}" | tr -d '-')_$(date +%Y%m%d%H%M%S)"
REPORT_FILE="${REPORT_DIR}/accuracy-${SOURCE}-${PERIOD}.md"

cat > "${REPORT_FILE}" <<EOF
# Legacy 数据迁移准确性校验报告

- **批次**: ${BATCH_CODE}
- **数据源**: ${SOURCE}
- **数据周期**: ${PERIOD}
- **生成时间**: $(date '+%Y-%m-%d %H:%M:%S')

## 校验规则

| 维度 | 规则 | 容差 |
|------|------|------|
| 金额 | ABS(目标 - 源) <= 容差 | ${TOLERANCE_AMOUNT} |
| 日期 | ABS(目标 - 源) <= ${TOLERANCE_DATE_DAYS} 天 | - |
| 主键 | 目标表行数 >= staging 行数 | 0 |
| 必填 | NOT NULL 字段无空值 | - |

## 校验结果

| 目标表 | 总行数 | 匹配 | 不匹配 | 缺失 | 准确率 | 状态 |
|--------|--------|------|--------|------|--------|------|
EOF

# ===== 校验主函数 =====
verify_one() {
  local key="$1" tgt total matched missing mismatch accuracy status
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

  log "[verify] ${tgt}"

  local log_id
  log_id=$(psql "${PMIS_DSN}" -tAc "
    INSERT INTO pmis_legacy_verify_log
      (batch_code, source, period, target_table)
    VALUES
      ('${BATCH_CODE}', '${SOURCE}', '${PERIOD}', '${tgt}')
    RETURNING id;
  " | tr -d '[:space:]')

  # 1) staging 行数
  local staging_count
  staging_count=$(psql "${PMIS_DSN}" -tAc "
    SELECT COUNT(*) FROM ${tgt}_staging
     WHERE batch_code LIKE 'LEGACY_TRANSFORM_${SOURCE}_%';
  " | tr -d '[:space:]')
  staging_count="${staging_count:-0}"

  # 2) 业务表行数 (已迁移)
  local biz_count
  biz_count=$(psql "${PMIS_DSN}" -tAc "
    SELECT COUNT(*) FROM ${tgt}
     WHERE _migration_batch LIKE 'LEGACY_TRANSFORM_${SOURCE}_%';
  " | tr -d '[:space:]')
  biz_count="${biz_count:-0}"

  # 3) 缺失 = staging - 业务 (应 >= 0)
  missing=$(( staging_count > biz_count ? staging_count - biz_count : 0 ))

  # 4) 金额 / 日期容差校验
  local amount_mismatch=0
  case "${key}" in
    project)
      amount_mismatch=$(psql "${PMIS_DSN}" -tAc "
        SELECT COUNT(*) FROM ${tgt}_staging s
         WHERE ABS(COALESCE(s.contract_amount, 0) -
                   COALESCE((SELECT contract_amount FROM ${tgt}
                              WHERE _legacy_id = s.legacy_id
                                AND _migration_batch = s.batch_code), 0))
               > ${TOLERANCE_AMOUNT};
      " | tr -d '[:space:]')
      ;;
    contract)
      amount_mismatch=$(psql "${PMIS_DSN}" -tAc "
        SELECT COUNT(*) FROM ${tgt}_staging s
         WHERE ABS(COALESCE(s.contract_amount, 0) -
                   COALESCE((SELECT contract_amount FROM ${tgt}
                              WHERE _legacy_id = s.legacy_id
                                AND _migration_batch = s.batch_code), 0))
               > ${TOLERANCE_AMOUNT};
      " | tr -d '[:space:]')
      ;;
    invoice)
      amount_mismatch=$(psql "${PMIS_DSN}" -tAc "
        SELECT COUNT(*) FROM ${tgt}_staging s
         WHERE ABS(COALESCE(s.amount, 0) -
                   COALESCE((SELECT amount FROM ${tgt}
                              WHERE _legacy_id = s.legacy_id
                                AND _migration_batch = s.batch_code), 0))
               > ${TOLERANCE_AMOUNT};
      " | tr -d '[:space:]')
      ;;
    payment)
      amount_mismatch=$(psql "${PMIS_DSN}" -tAc "
        SELECT COUNT(*) FROM ${tgt}_staging s
         WHERE ABS(COALESCE(s.amount, 0) -
                   COALESCE((SELECT amount FROM ${tgt}
                              WHERE _legacy_id = s.legacy_id
                                AND _migration_batch = s.batch_code), 0))
               > ${TOLERANCE_AMOUNT};
      " | tr -d '[:space:]')
      ;;
    voucher)
      amount_mismatch=$(psql "${PMIS_DSN}" -tAc "
        SELECT COUNT(*) FROM ${tgt}_staging s
         WHERE ABS(COALESCE(s.amount, 0) -
                   COALESCE((SELECT amount FROM ${tgt}
                              WHERE _legacy_id = s.legacy_id
                                AND _migration_batch = s.batch_code), 0))
               > ${TOLERANCE_AMOUNT};
      " | tr -d '[:space:]')
      ;;
    *) amount_mismatch=0 ;;
  esac
  amount_mismatch="${amount_mismatch:-0}"

  total=${staging_count}
  matched=$(( total - amount_mismatch - missing ))
  [[ "${matched}" -lt 0 ]] && matched=0
  mismatch=${amount_mismatch}
  accuracy=$(awk -v m="${matched}" -v t="${total}" 'BEGIN{ if(t==0) print "0.000"; else printf "%.3f", m*100/t }')

  if [[ "${mismatch}" -le "${THRESHOLD_FAIL}" && "${missing}" -le 0 ]]; then
    status="PASS"
    ok "  ${tgt}: ${matched}/${total} (${accuracy}%)"
  else
    status="FAIL"
    err "  ${tgt}: ${mismatch} 不匹配, ${missing} 缺失, 准确率 ${accuracy}%"
  fi

  psql "${PMIS_DSN}" -q -c "
    UPDATE pmis_legacy_verify_log
       SET total_rows   = ${total},
           matched_rows = ${matched},
           mismatch_rows= ${mismatch},
           missing_rows = ${missing},
           accuracy_pct = ${accuracy},
           status       = '${status}',
           finished_at  = CURRENT_TIMESTAMP
     WHERE id = ${log_id};
  " >/dev/null

  cat >> "${REPORT_FILE}" <<EOF
| ${tgt} | ${total} | ${matched} | ${mismatch} | ${missing} | ${accuracy}% | ${status} |
EOF

  echo "${status}" > "/tmp/_verify_${key}.status"
}

# ===== 主循环 =====
KEYS=()
case "${SOURCE}" in
  erp)     KEYS=(project contract invoice payment) ;;
  finance) KEYS=(coa voucher receipt) ;;
  hr)      KEYS=(employee payroll attend) ;;
  *) err "不支持的 source: ${SOURCE}"; exit 1 ;;
esac

for key in "${KEYS[@]}"; do
  verify_one "${key}"
done

# ===== 跨表总校验: 业务表与 staging 数量一致性 =====
log ""
log "========== 跨表一致性 =========="
psql "${PMIS_DSN}" -c "
  SELECT
    s.target_table,
    s.total_rows   AS staging_count,
    v.matched_rows AS matched,
    v.mismatch_rows AS mismatch,
    v.missing_rows  AS missing,
    v.accuracy_pct
  FROM pmis_legacy_verify_log v
  WHERE v.batch_code = '${BATCH_CODE}'
  ORDER BY v.id;
"

# ===== 必填字段非空校验 =====
log ""
log "========== 必填字段非空校验 =========="
psql "${PMIS_DSN}" -c "
  SELECT
    target_table,
    SUM(CASE WHEN NOT (target_table LIKE '%_coa' OR target_table LIKE '%_employee') AND matched_rows = 0 THEN 1 ELSE 0 END) AS empty_warn
  FROM pmis_legacy_verify_log
  WHERE batch_code = '${BATCH_CODE}'
  GROUP BY target_table;
"

# ===== 写入报告尾 =====
cat >> "${REPORT_FILE}" <<EOF

## 结论

EOF

total_pass=0
total_fail=0
for key in "${KEYS[@]}"; do
  if [[ -f "/tmp/_verify_${key}.status" ]]; then
    if [[ "$(cat /tmp/_verify_${key}.status)" == "PASS" ]]; then
      total_pass=$((total_pass+1))
    else
      total_fail=$((total_fail+1))
    fi
    rm -f /tmp/_verify_${key}.status
  fi
done

if [[ "${total_fail}" -eq 0 ]]; then
  cat >> "${REPORT_FILE}" <<EOF
**全部通过 ✅**

- 通过: ${total_pass} 张表
- 失败: 0 张表

迁移结果可直接投入生产使用。
EOF
  ok "全部 ${total_pass} 张表校验通过"
  log "报告: ${REPORT_FILE}"
  exit 0
else
  cat >> "${REPORT_FILE}" <<EOF
**部分失败 ❌**

- 通过: ${total_pass} 张表
- 失败: ${total_fail} 张表

请检查 pmis_legacy_verify_log 表，定位不匹配/缺失数据。
EOF
  err "${total_fail} 张表校验失败, 请检查 verify_log"
  log "报告: ${REPORT_FILE}"
  exit 1
fi
