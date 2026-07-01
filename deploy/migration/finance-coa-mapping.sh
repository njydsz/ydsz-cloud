#!/usr/bin/env bash
# =============================================================================
#  财务科目映射 (Finance COA Mapping)
#  批次 21 / P1 11.4 — 把遗留 COA 映射到 PMIS 标准 COA
#  策略: 4 段编码匹配 (段1.资产/负债/权益; 段2.类别; 段3.明细; 段4.项目)
#  用法: ./finance-coa-mapping.sh [--commit] [--report-only]
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_FILE="${SCRIPT_DIR}/migration.conf"
LOG_DIR="${SCRIPT_DIR}/../logs/migration"
LOG_FILE="${LOG_DIR}/coa-mapping-$(date +%Y%m%d_%H%M%S).log"
REPORT_DIR="${SCRIPT_DIR}/../reports/migration"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "[$(date +%H:%M:%S)] $*" | tee -a "${LOG_FILE}"; }
err()  { log "${RED}✗ $*${NC}"; }
ok()   { log "${GREEN}✓ $*${NC}"; }
warn() { log "${YELLOW}⚠ $*${NC}"; }

# ===== 参数 =====
COMMIT=0
REPORT_ONLY=0
LEGACY_DSN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --commit)       COMMIT=1 ;;
    --report-only)  REPORT_ONLY=1 ;;
    --dsn=*)        LEGACY_DSN="${1#*=}" ;;
    -h|--help)      grep -E '^#( |$)' "${BASH_SOURCE[0]}" | sed 's/^# //; s/^#$//'; exit 0 ;;
    *) err "未知参数: $1"; exit 1 ;;
  esac
  shift
done

[[ -f "${CONF_FILE}" ]] && source "${CONF_FILE}"
: "${PMIS_DSN:=postgresql://postgres:Limw1020@127.0.0.1:5432/ydsz-pmis}"
: "${MIGRATION_TENANT_ID:=1}"

log "=========================================="
log "财务科目 COA 映射启动"
log "  commit:      ${COMMIT}"
log "  reportOnly:  ${REPORT_ONLY}"
log "  legacyDsn:   ${LEGACY_DSN:-N/A}"
log "  log:         ${LOG_FILE}"
log "=========================================="

PSQL=$(command -v psql || true)
[[ -z "${PSQL}" ]] && { err "未检测到 psql"; exit 1; }

REPORT_FILE="${REPORT_DIR}/coa-mapping-$(date +%Y%m%d).md"
cat > "${REPORT_FILE}" <<EOF
# 财务科目映射报告

- **生成时间**: $(date '+%Y-%m-%d %H:%M:%S')
- **租户**: ${MIGRATION_TENANT_ID}

## 映射策略

| 段位 | 长度 | 描述 | PMIS 规范 |
|------|------|------|----------|
| 段1 | 1位 | 资产/负债/权益类别 | 1=资产, 2=负债, 3=权益, 4=成本, 5=损益 |
| 段2 | 2位 | 类别(应收/应付/费用/收入) | 01=现金, 02=银行, 03=应收, 04=存货, ... |
| 段3 | 3位 | 明细科目 | 用户自定义 |
| 段4 | 3位 | 项目/部门辅助核算 | 可空 |

> 格式: X-XX-XXX-XXX (例: 1-03-001-000 = 资产-应收账款-明细001-无项目)

## 映射规则表 (内置)

EOF

# ===== 创建 COA 映射表 =====
psql "${PMIS_DSN}" -q -c "
CREATE TABLE IF NOT EXISTS pmis_coa_mapping (
  id                BIGSERIAL PRIMARY KEY,
  legacy_coa_code   VARCHAR(64)  NOT NULL,
  pmis_coa_code     VARCHAR(64)  NOT NULL,
  mapping_type      VARCHAR(16)  NOT NULL,  -- EXACT/PATTERN/DEFAULT/MANUAL
  confidence        NUMERIC(4,3) NOT NULL DEFAULT 1.0,
  remark            TEXT,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (legacy_coa_code)
);
CREATE INDEX IF NOT EXISTS idx_pmis_coa_mapping_pmis
  ON pmis_coa_mapping (pmis_coa_code);" >/dev/null

# ===== 加载内置映射规则 =====
# 1=资产, 2=负债, 3=权益, 4=成本, 5=损益
psql "${PMIS_DSN}" -q <<'SQL' >/dev/null
-- 资产类
INSERT INTO pmis_coa_mapping (legacy_coa_code, pmis_coa_code, mapping_type, confidence, remark) VALUES
  ('1001', '1-01-001-000', 'EXACT', 1.000, '现金-库存现金'),
  ('1002', '1-02-001-000', 'EXACT', 1.000, '银行存款'),
  ('1002.01', '1-02-002-000', 'EXACT', 1.000, '工行账户'),
  ('1002.02', '1-02-003-000', 'EXACT', 1.000, '建行账户'),
  ('1122', '1-03-001-000', 'EXACT', 1.000, '应收账款-主'),
  ('1122.01', '1-03-002-000', 'EXACT', 1.000, '应收账款-客户A'),
  ('1122.02', '1-03-003-000', 'EXACT', 1.000, '应收账款-客户B'),
  ('1221', '1-04-001-000', 'EXACT', 1.000, '其他应收款'),
  ('1403', '1-05-001-000', 'EXACT', 1.000, '原材料'),
  ('1405', '1-05-002-000', 'EXACT', 1.000, '库存商品'),
  ('1601', '1-06-001-000', 'EXACT', 1.000, '固定资产-房屋'),
  ('1602', '1-06-002-000', 'EXACT', 1.000, '固定资产-设备'),
  ('1701', '1-07-001-000', 'EXACT', 1.000, '无形资产-软件'),
  ('1801', '1-08-001-000', 'EXACT', 1.000, '长期待摊费用'),
  -- 负债类
  ('2001', '2-01-001-000', 'EXACT', 1.000, '短期借款'),
  ('2202', '2-02-001-000', 'EXACT', 1.000, '应付账款-主'),
  ('2211', '2-03-001-000', 'EXACT', 1.000, '应付职工薪酬'),
  ('2221', '2-04-001-000', 'EXACT', 1.000, '应交税费-增值税'),
  ('2221.01', '2-04-002-000', 'EXACT', 1.000, '应交税费-所得税'),
  ('2241', '2-05-001-000', 'EXACT', 1.000, '其他应付款'),
  ('2501', '2-06-001-000', 'EXACT', 1.000, '长期借款'),
  -- 权益类
  ('3001', '3-01-001-000', 'EXACT', 1.000, '实收资本'),
  ('3002', '3-02-001-000', 'EXACT', 1.000, '资本公积'),
  ('3101', '3-03-001-000', 'EXACT', 1.000, '盈余公积'),
  ('3103', '3-04-001-000', 'EXACT', 1.000, '本年利润'),
  ('3104', '3-05-001-000', 'EXACT', 1.000, '利润分配'),
  -- 成本类
  ('4001', '4-01-001-000', 'EXACT', 1.000, '生产成本-人工'),
  ('4002', '4-01-002-000', 'EXACT', 1.000, '生产成本-材料'),
  ('4101', '4-02-001-000', 'EXACT', 1.000, '制造费用'),
  ('4301', '4-03-001-000', 'EXACT', 1.000, '研发支出'),
  -- 损益类
  ('5001', '5-01-001-000', 'EXACT', 1.000, '主营业务收入'),
  ('5051', '5-01-002-000', 'EXACT', 1.000, '其他业务收入'),
  ('5301', '5-02-001-000', 'EXACT', 1.000, '投资收益'),
  ('5401', '5-03-001-000', 'EXACT', 1.000, '主营业务成本'),
  ('5402', '5-03-002-000', 'EXACT', 1.000, '其他业务成本'),
  ('5501', '5-04-001-000', 'EXACT', 1.000, '营业税金及附加'),
  ('5502', '5-04-002-000', 'EXACT', 1.000, '销售费用'),
  ('5503', '5-04-003-000', 'EXACT', 1.000, '管理费用'),
  ('5504', '5-04-004-000', 'EXACT', 1.000, '财务费用'),
  ('5601', '5-05-001-000', 'EXACT', 1.000, '营业外收入'),
  ('5602', '5-05-002-000', 'EXACT', 1.000, '营业外支出')
ON CONFLICT (legacy_coa_code) DO UPDATE
  SET pmis_coa_code = EXCLUDED.pmis_coa_code,
      mapping_type  = EXCLUDED.mapping_type,
      confidence    = EXCLUDED.confidence,
      remark        = EXCLUDED.remark;
SQL

# ===== 模式匹配 (未精确匹配的 legacy COA 用前 N 位推断) =====
# 1xxx -> 1-资产; 2xxx -> 2-负债; 3xxx -> 3-权益; 4xxx -> 4-成本; 5xxx -> 5-损益
psql "${PMIS_DSN}" -q <<'SQL' >/dev/null
-- 自动模式匹配: 1000-1999 -> 1-xx-xxx-000, 2000-2999 -> 2-xx-xxx-000 ...
INSERT INTO pmis_coa_mapping (legacy_coa_code, pmis_coa_code, mapping_type, confidence, remark)
SELECT
  lc.coa_code,
  CASE
    WHEN lc.coa_code LIKE '1%' THEN '1-' || substr(lc.coa_code, 2, 2) || '-' || substr(lc.coa_code, 4, 3) || '-000'
    WHEN lc.coa_code LIKE '2%' THEN '2-' || substr(lc.coa_code, 2, 2) || '-' || substr(lc.coa_code, 4, 3) || '-000'
    WHEN lc.coa_code LIKE '3%' THEN '3-' || substr(lc.coa_code, 2, 2) || '-' || substr(lc.coa_code, 4, 3) || '-000'
    WHEN lc.coa_code LIKE '4%' THEN '4-' || substr(lc.coa_code, 2, 2) || '-' || substr(lc.coa_code, 4, 3) << '-000'
    WHEN lc.coa_code LIKE '5%' THEN '5-' || substr(lc.coa_code, 2, 2) || '-' || substr(lc.coa_code, 4, 3) || '-000'
    ELSE '9-99-999-000'
  END,
  'PATTERN',
  0.700,
  '模式自动匹配, 需人工复核'
FROM pmis_finance_coa lc
WHERE NOT EXISTS (
  SELECT 1 FROM pmis_coa_mapping m WHERE m.legacy_coa_code = lc.coa_code
)
  AND lc.coa_code ~ '^[1-5][0-9]{3}'
ON CONFLICT (legacy_coa_code) DO NOTHING;
SQL

# ===== 统计 =====
local total_legacy=$(psql "${PMIS_DSN}" -tAc "SELECT COUNT(*) FROM pmis_finance_coa;" | tr -d '[:space:]')
local total_mapped=$(psql "${PMIS_DSN}" -tAc "SELECT COUNT(*) FROM pmis_coa_mapping;" | tr -d '[:space:]')
local exact_count=$(psql "${PMIS_DSN}" -tAc "SELECT COUNT(*) FROM pmis_coa_mapping WHERE mapping_type='EXACT';" | tr -d '[:space:]')
local pattern_count=$(psql "${PMIS_DSN}" -tAc "SELECT COUNT(*) FROM pmis_coa_mapping WHERE mapping_type='PATTERN';" | tr -d '[:space:]')

cat >> "${REPORT_FILE}" <<EOF

## 映射统计

| 指标 | 数量 |
|------|------|
| 遗留 COA 总数 | ${total_legacy} |
| 已映射 | ${total_mapped} |
| 精确匹配 | ${exact_count} |
| 模式匹配 | ${pattern_count} |
| 未映射 | $(( total_legacy - total_mapped )) |

EOF

log "  遗留 COA: ${total_legacy}, 已映射: ${total_mapped} (精确 ${exact_count}, 模式 ${pattern_count})"

# ===== 标记 PMIS 标准 COA 中的目标记录 =====
if [[ "${COMMIT}" == "1" ]]; then
  log "  提交映射结果到 pmis_finance_coa..."
  psql "${PMIS_DSN}" -q -c "
    UPDATE pmis_finance_coa c
       SET pmis_coa_code = m.pmis_coa_code,
           _mapping_type = m.mapping_type,
           _mapping_confidence = m.confidence
      FROM pmis_coa_mapping m
     WHERE c.coa_code = m.legacy_coa_code;
  " >/dev/null
  ok "  COA 映射已落地"
else
  warn "  DRY-RUN 模式, 未修改 pmis_finance_coa"
fi

# ===== 报告: 待人工确认 (模式匹配或缺失) =====
cat >> "${REPORT_FILE}" <<EOF

## 待人工确认列表 (confidence < 0.9)

| 遗留编码 | 映射到 | 类型 | 置信度 | 备注 |
|----------|--------|------|--------|------|
EOF
psql "${PMIS_DSN}" -c "
  SELECT legacy_coa_code, pmis_coa_code, mapping_type, confidence, remark
    FROM pmis_coa_mapping
   WHERE confidence < 0.9
   ORDER BY confidence, legacy_coa_code
   LIMIT 100;
" >> "${REPORT_FILE}"

ok "COA 映射完成"
log "报告: ${REPORT_FILE}"
