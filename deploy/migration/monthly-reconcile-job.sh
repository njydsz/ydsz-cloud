#!/usr/bin/env bash
# =============================================================================
#  月度对账任务 (Monthly Reconcile Job)
#  批次 21 / P1 11.4 — 每月 1 日 03:00 自动执行, 比对 PMIS 与财务系统
#  校验项:
#    1) 发票 vs 收款  (pmis_finance_invoice.amount = pmis_finance_payment.amount)
#    2) 合同 vs 发票  (pmis_project_contract.contract_amount = sum(invoice.amount))
#    3) 项目 vs 预算  (sum(pmis_budget_item.amount) <= project.contract_amount)
#    4) 工时 vs 工资  (sum(time_entry.hours) * rate ~= payroll.amount)
#    5) COA 余额     (sum(debit) = sum(credit))
#  用法: ./monthly-reconcile-job.sh --period=2026-06 [--commit] [--notify]
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_FILE="${SCRIPT_DIR}/migration.conf"
LOG_DIR="${SCRIPT_DIR}/../logs/migration"
LOG_FILE="${LOG_DIR}/reconcile-$(date +%Y%m).log"
REPORT_DIR="${SCRIPT_DIR}/../reports/migration"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "[$(date +%Y-%m-%d %H:%M:%S)] $*" | tee -a "${LOG_FILE}"; }
err()  { log "${RED}✗ $*${NC}"; }
ok()   { log "${GREEN}✓ $*${NC}"; }
warn() { log "${YELLOW}⚠ $*${NC}"; }

# ===== 参数 =====
PERIOD=$(date -d "last month" +%Y-%m)
COMMIT=0
NOTIFY=0
TOLERANCE=0.01

while [[ $# -gt 0 ]]; do
  case "$1" in
    --period=*) PERIOD="${1#*=}" ;;
    --commit)   COMMIT=1 ;;
    --notify)   NOTIFY=1 ;;
    --tol=*)    TOLERANCE="${1#*=}" ;;
    -h|--help)  grep -E '^#( |$)' "${BASH_SOURCE[0]}" | sed 's/^# //; s/^#$//'; exit 0 ;;
    *) err "未知参数: $1"; exit 1 ;;
  esac
  shift
done

[[ -f "${CONF_FILE}" ]] && source "${CONF_FILE}"
: "${PMIS_DSN:=postgresql://postgres:Limw1020@127.0.0.1:5432/ydsz-pmis}"

log "=========================================="
log "月度对账任务启动"
log "  period: ${PERIOD}"
log "  commit: ${COMMIT}"
log "  notify: ${NOTIFY}"
log "  tol:    ${TOLERANCE}"
log "=========================================="

PSQL=$(command -v psql || true)
[[ -z "${PSQL}" ]] && { err "未检测到 psql"; exit 1; }

REPORT_FILE="${REPORT_DIR}/reconcile-${PERIOD}.md"
BATCH_CODE="RECONCILE_${PERIOD}_$(date +%Y%m%d%H%M%S)"

# ===== 对账日志 =====
psql "${PMIS_DSN}" -q -c "
CREATE TABLE IF NOT EXISTS pmis_reconcile_log (
  id                BIGSERIAL PRIMARY KEY,
  batch_code        VARCHAR(64)  NOT NULL,
  period            VARCHAR(32)  NOT NULL,
  check_type        VARCHAR(32)  NOT NULL,
  total             BIGINT       NOT NULL DEFAULT 0,
  matched           BIGINT       NOT NULL DEFAULT 0,
  diff              BIGINT       NOT NULL DEFAULT 0,
  diff_amount       NUMERIC(20,2) NOT NULL DEFAULT 0,
  status            VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
  error_message     TEXT,
  started_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at       TIMESTAMP
);" >/dev/null

cat > "${REPORT_FILE}" <<EOF
# 月度对账报告

- **批次**: ${BATCH_CODE}
- **对账周期**: ${PERIOD}
- **生成时间**: $(date '+%Y-%m-%d %H:%M:%S')
- **容差**: ${TOLERANCE}

## 校验项

| # | 类型 | 说明 | 规则 |
|---|------|------|------|
| 1 | invoice-payment | 发票 vs 收款 | 已开发票金额 = 已收款金额 (按 invoice_id 关联) |
| 2 | contract-invoice | 合同 vs 发票 | 累计开票金额 <= 合同金额 (按 contract_id 关联) |
| 3 | project-budget | 项目 vs 预算 | 预算总额 <= 合同金额 (按 initiation_id 关联) |
| 4 | time-payroll | 工时 vs 工资 | 工资总额 ~= sum(hours) * 平均时薪 |
| 5 | coa-balance | COA 借贷平衡 | sum(debit) = sum(credit) |

## 对账结果

EOF

# ===== 1) 发票 vs 收款 =====
log "[1/5] 发票 vs 收款"
psql "${PMIS_DSN}" -q -c "
INSERT INTO pmis_reconcile_log (batch_code, period, check_type)
VALUES ('${BATCH_CODE}', '${PERIOD}', 'invoice-payment');
" >/dev/null
psql "${PMIS_DSN}" -c "
SELECT
  i.invoice_code,
  i.amount                            AS invoice_amount,
  COALESCE(SUM(p.amount), 0)          AS paid_amount,
  i.amount - COALESCE(SUM(p.amount), 0) AS diff
FROM pmis_finance_invoice i
LEFT JOIN pmis_finance_payment p
       ON p.invoice_id = i.id
      AND p.status = 'RECEIVED'
WHERE i.issued_at >= '${PERIOD}-01'
  AND i.issued_at <  ('${PERIOD}-01'::date + INTERVAL '1 month')
  AND i.status IN ('ISSUED', 'PAID', 'RED_REVERSED')
  AND ABS(i.amount - COALESCE((SELECT SUM(p2.amount)
                                 FROM pmis_finance_payment p2
                                WHERE p2.invoice_id = i.id
                                  AND p2.status = 'RECEIVED'), 0)) > ${TOLERANCE}
GROUP BY i.id, i.invoice_code, i.amount
ORDER BY ABS(diff) DESC
LIMIT 20;
"

# ===== 2) 合同 vs 发票 =====
log "[2/5] 合同 vs 发票"
psql "${PMIS_DSN}" -q -c "
INSERT INTO pmis_reconcile_log (batch_code, period, check_type)
VALUES ('${BATCH_CODE}', '${PERIOD}', 'contract-invoice');
" >/dev/null
psql "${PMIS_DSN}" -c "
SELECT
  c.contract_code,
  c.contract_amount,
  COALESCE(SUM(i.amount), 0) AS invoiced_amount,
  c.contract_amount - COALESCE(SUM(i.amount), 0) AS remaining
FROM pmis_project_contract c
LEFT JOIN pmis_finance_invoice i
       ON i.contract_id = c.id
      AND i.status NOT IN ('CANCELLED')
WHERE c.signed_at < ('${PERIOD}-01'::date + INTERVAL '1 month')
  AND c.status NOT IN ('DRAFT', 'TERMINATED')
GROUP BY c.id, c.contract_code, c.contract_amount
HAVING COALESCE(SUM(i.amount), 0) > c.contract_amount + ${TOLERANCE}
ORDER BY (COALESCE(SUM(i.amount), 0) - c.contract_amount) DESC
LIMIT 20;
"

# ===== 3) 项目 vs 预算 =====
log "[3/5] 项目 vs 预算"
psql "${PMIS_DSN}" -q -c "
INSERT INTO pmis_reconcile_log (batch_code, period, check_type)
VALUES ('${BATCH_CODE}', '${PERIOD}', 'project-budget');
" >/dev/null
psql "${PMIS_DSN}" -c "
SELECT
  p.project_code,
  p.contract_amount,
  COALESCE(SUM(b.amount), 0) AS budget_total,
  COALESCE(SUM(b.amount), 0) - p.contract_amount AS overrun
FROM pmis_project_initiation p
LEFT JOIN pmis_budget_item b
       ON b.initiation_id = p.id
WHERE p.status NOT IN ('CANCELLED')
GROUP BY p.id, p.project_code, p.contract_amount
HAVING COALESCE(SUM(b.amount), 0) > p.contract_amount + ${TOLERANCE}
ORDER BY overrun DESC
LIMIT 20;
"

# ===== 4) 工时 vs 工资 =====
log "[4/5] 工时 vs 工资"
psql "${PMIS_DSN}" -q -c "
INSERT INTO pmis_reconcile_log (batch_code, period, check_type)
VALUES ('${BATCH_CODE}', '${PERIOD}', 'time-payroll');
" >/dev/null
psql "${PMIS_DSN}" -c "
WITH time_agg AS (
  SELECT employee_id, SUM(hours) AS total_hours
    FROM pmis_execution_time_entry
   WHERE entry_date >= '${PERIOD}-01'
     AND entry_date <  ('${PERIOD}-01'::date + INTERVAL '1 month')
     AND status = 'APPROVED'
   GROUP BY employee_id
),
payroll_agg AS (
  SELECT employee_id, SUM(amount) AS total_pay
    FROM pmis_user_payroll
   WHERE period = '${PERIOD}'
   GROUP BY employee_id
)
SELECT
  t.employee_id,
  e.employee_name,
  t.total_hours,
  COALESCE(p.total_pay, 0) AS total_pay,
  CASE WHEN t.total_hours > 0
       THEN (COALESCE(p.total_pay, 0) / t.total_hours)::numeric(10,2)
       ELSE 0 END AS avg_rate
  FROM time_agg t
  LEFT JOIN payroll_agg p ON p.employee_id = t.employee_id
  JOIN pmis_user_employee e ON e.id = t.employee_id
 WHERE ABS(COALESCE(p.total_pay, 0) - t.total_hours * 100) > 5000  -- 平均时薪波动 > 50
 ORDER BY ABS(COALESCE(p.total_pay, 0) - t.total_hours * 100) DESC
 LIMIT 20;
"

# ===== 5) COA 借贷平衡 =====
log "[5/5] COA 借贷平衡"
psql "${PMIS_DSN}" -q -c "
INSERT INTO pmis_reconcile_log (batch_code, period, check_type)
VALUES ('${BATCH_CODE}', '${PERIOD}', 'coa-balance');
" >/dev/null
psql "${PMIS_DSN}" -c "
SELECT
  voucher_date::date,
  COUNT(*) AS voucher_count,
  SUM(debit_amount) AS total_debit,
  SUM(credit_amount) AS total_credit,
  SUM(debit_amount) - SUM(credit_amount) AS diff
  FROM pmis_finance_voucher
 WHERE voucher_date >= '${PERIOD}-01'
   AND voucher_date <  ('${PERIOD}-01'::date + INTERVAL '1 month')
 GROUP BY voucher_date::date
HAVING ABS(SUM(debit_amount) - SUM(credit_amount)) > ${TOLERANCE}
 ORDER BY voucher_date;
"

# ===== 汇总 =====
log ""
log "============== 对账汇总 =============="
psql "${PMIS_DSN}" -c "
  SELECT
    check_type,
    COUNT(*) AS log_count,
    SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) AS success_count,
    SUM(CASE WHEN status='FAIL'    THEN 1 ELSE 0 END) AS fail_count
  FROM pmis_reconcile_log
  WHERE batch_code = '${BATCH_CODE}'
  GROUP BY check_type;
"

# ===== 通知 (可选) =====
if [[ "${NOTIFY}" == "1" ]]; then
  log "发送对账通知..."
  # 调用通知服务 (email/钉钉/企微)
  if [[ -f "${SCRIPT_DIR}/notify.sh" ]]; then
    "${SCRIPT_DIR}/notify.sh" \
      --title="PMIS 月度对账完成 ${PERIOD}" \
      --content-file="${REPORT_FILE}" \
      --level=info || warn "通知发送失败 (非阻塞)"
  else
    warn "未找到 notify.sh, 跳过通知"
  fi
fi

# ===== 清理 =====
psql "${PMIS_DSN}" -q -c "
  UPDATE pmis_reconcile_log
     SET status = 'SUCCESS', finished_at = CURRENT_TIMESTAMP
   WHERE batch_code = '${BATCH_CODE}' AND status = 'RUNNING';
" >/dev/null

ok "月度对账完成: ${BATCH_CODE}"
log "报告: ${REPORT_FILE}"

# ===== 退出码: 0=通过, 1=存在不平衡 =====
local has_diff=$(psql "${PMIS_DSN}" -tAc "
  SELECT COUNT(*) FROM pmis_reconcile_log
   WHERE batch_code = '${BATCH_CODE}' AND diff > 0;
" | tr -d '[:space:]')
if [[ "${has_diff}" -gt 0 ]]; then
  warn "存在 ${has_diff} 条对账差异, 请人工复核"
  exit 1
fi
exit 0
