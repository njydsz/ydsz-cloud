#!/usr/bin/env bash
# =============================================================================
#  PMIS PostgreSQL 每日备份健康检查脚本
#  ----------------------------------------------------------------------------
#  用途：每日 09:00 自动跑（crontab），校验昨日全量备份文件存在 + 大小 + 校验和
#  失败时告警邮件
# =============================================================================
set -euo pipefail

BACKUP_DIR="${PMIS_BACKUP_DIR:-/data/backup/pmis/daily}"
LOG_DIR="${PMIS_BACKUP_LOG_DIR:-/var/log/pmis/backup}"
ALERT_MAIL="${PMIS_ALERT_MAIL:-ops@ydsz-pmis.cn}"
MIN_SIZE_MB="${PMIS_MIN_BACKUP_SIZE_MB:-50}"
TODAY=$(date +%F)
YESTERDAY=$(date -d 'yesterday' +%F 2>/dev/null || date -v-1d +%F)

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠ $*${NC}"; }
err()  { echo -e "${RED}✗ $*${NC}"; }
fail() { err "$*"; ALERT_MAIL_SUBJECT="[ALERT] PMIS 备份检查失败 $(date +%F)"; echo -e "$*" | mailx -s "${ALERT_MAIL_SUBJECT}" "${ALERT_MAIL}" || true; exit 1; }

mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/verify_$(date +%Y%m).log"
exec >> "${LOG_FILE}" 2>&1

echo "==========================================="
echo "[PMIS Backup Verify] ${TODAY}"
echo "  yesterday: ${YESTERDAY}"
echo "  backup_dir: ${BACKUP_DIR}"
echo "==========================================="

# ---------- 1) 文件存在性 ----------
BACKUP_FILE="${BACKUP_DIR}/pmis_daily_${YESTERDAY}.sql.gz"
if [ ! -f "${BACKUP_FILE}" ]; then
  fail "昨日备份文件不存在：${BACKUP_FILE}"
fi
ok "备份文件存在：$(basename ${BACKUP_FILE})"

# ---------- 2) 文件大小 ----------
SIZE_MB=$(du -m "${BACKUP_FILE}" | cut -f1)
if [ "${SIZE_MB}" -lt "${MIN_SIZE_MB}" ]; then
  fail "备份文件过小：${SIZE_MB}MB < 最小 ${MIN_SIZE_MB}MB（可能不完整）"
fi
ok "备份大小：${SIZE_MB}MB"

# ---------- 3) 完整性校验（gzip test）----------
if ! gzip -t "${BACKUP_FILE}" 2>/dev/null; then
  fail "备份文件 gzip 校验失败：${BACKUP_FILE}"
fi
ok "gzip 完整性校验通过"

# ---------- 4) 备份状态文件 ----------
STATUS_FILE="${BACKUP_DIR}/.last_backup.json"
if [ -f "${STATUS_FILE}" ]; then
  STATUS=$(jq -r '.status' < "${STATUS_FILE}" 2>/dev/null || echo "unknown")
  if [ "${STATUS}" != "SUCCESS" ]; then
    fail "备份状态文件记录非成功：${STATUS}"
  fi
  ok "备份状态：${STATUS}"
fi

# ---------- 5) 记录日志 ----------
cat > "${LOG_DIR}/.last_verify.json" <<EOF
{
  "verify_date": "${TODAY}",
  "backup_file": "$(basename ${BACKUP_FILE})",
  "size_mb": ${SIZE_MB},
  "gzip_ok": true,
  "status": "PASS"
}
EOF
ok "备份健康检查通过"
exit 0
