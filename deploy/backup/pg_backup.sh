#!/usr/bin/env bash
# ============================================================================
#  PMIS PostgreSQL 每日全量备份脚本
#  --------------------------------------------------------------------------
#  用途：每日凌晨 02:00 触发，pg_dump 全库导出 + gzip 压缩 + 7 天轮转
#  调用：deploy/backup/cron.d/pmis-backup (crontab)
#  依赖：PG_CLIENT (pg_dump / psql)、gzip、find、mailx
#  退出码：0=成功 1=备份失败 2=清理旧备份失败 3=校验失败
# ============================================================================
set -euo pipefail

# ---------- 配置（可通过环境变量覆盖） ----------
PG_HOST="${PMIS_PG_HOST:-127.0.0.1}"
PG_PORT="${PMIS_PG_PORT:-5432}"
PG_USER="${PMIS_PG_USER:-pmis_backup}"
PG_DB="${PMIS_PG_DB:-pmis}"
BACKUP_DIR="${PMIS_BACKUP_DIR:-/data/backup/pmis/daily}"
# H7.4 修复：等保 2.0 三级要求"重要数据备份至少保存 90 天"
#   本地保留 7 天（快速恢复近期数据）
#   OSS 保留 90 天（生命周期规则：30 天后转 IA，90 天后转 Archive）
#   异地 1 年（运维另行配置 OSS 跨区域复制）
RETENTION_DAYS="${PMIS_RETENTION_DAYS:-7}"
OSS_RETENTION_DAYS="${PMIS_OSS_RETENTION_DAYS:-90}"
LOG_DIR="${PMIS_BACKUP_LOG_DIR:-/var/log/pmis/backup}"
ALERT_MAIL="${PMIS_ALERT_MAIL:-ops@ydsz-pmis.cn}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/pmis_daily_${TIMESTAMP}.sql.gz"
LOG_FILE="${LOG_DIR}/pg_backup_${TIMESTAMP}.log"

# ---------- 初始化 ----------
mkdir -p "${BACKUP_DIR}" "${LOG_DIR}"
exec > >(tee -a "${LOG_FILE}") 2>&1
echo "============================================================"
echo "[PMIS Daily Backup] started at $(date '+%F %T')"
echo "  host=${PG_HOST} port=${PG_PORT} db=${PG_DB} user=${PG_USER}"
echo "  backup_file=${BACKUP_FILE}"

# ---------- 1. 预检：磁盘空间（至少预留 2 倍 DB 体积） ----------
DB_SIZE_BYTES=$(PGPASSWORD="${PMIS_PGPASSWORD}" psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" -tAc "SELECT pg_database_size('${PG_DB}')")
REQUIRED_BYTES=$((DB_SIZE_BYTES * 2))
AVAIL_BYTES=$(df -B1 "${BACKUP_DIR}" | tail -n1 | awk '{print $4}')
echo "  db_size=${DB_SIZE_BYTES}B required=${REQUIRED_BYTES}B avail=${AVAIL_BYTES}B"
if [ "${AVAIL_BYTES}" -lt "${REQUIRED_BYTES}" ]; then
  echo "[FATAL] disk space insufficient, abort" >&2
  echo "PMIS 备份失败：磁盘空间不足（需要 ${REQUIRED_BYTES}，可用 ${AVAIL_BYTES}）" | mailx -s "[ALERT] PMIS Daily Backup FAILED" "${ALERT_MAIL}" || true
  exit 1
fi

# ---------- 2. 执行 pg_dump（全库 + 并行 + 压缩） ----------
echo "[STEP 2] pg_dump started"
START_TS=$(date +%s)
if ! PGPASSWORD="${PMIS_PGPASSWORD}" pg_dump \
    -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" \
    --format=custom \
    --compress=9 \
    --no-owner \
    --no-privileges \
    --serializable-deferrable \
    --jobs=4 \
    --file="${BACKUP_FILE}"; then
  echo "[FATAL] pg_dump failed" >&2
  echo "PMIS 备份失败：pg_dump 错误，请查看 ${LOG_FILE}" | mailx -s "[ALERT] PMIS Daily Backup FAILED" "${ALERT_MAIL}" || true
  rm -f "${BACKUP_FILE}"
  exit 1
fi
END_TS=$(date +%s)
DURATION=$((END_TS - START_TS))
BACKUP_SIZE=$(stat -c %s "${BACKUP_FILE}" 2>/dev/null || stat -f %z "${BACKUP_FILE}")
echo "[STEP 2] pg_dump done, duration=${DURATION}s size=${BACKUP_SIZE}B"

# ---------- 3. 校验备份完整性（pg_restore --list） ----------
echo "[STEP 3] verify backup integrity"
if ! pg_restore --list "${BACKUP_FILE}" > /dev/null 2>&1; then
  echo "[FATAL] backup file corrupted" >&2
  echo "PMIS 备份失败：备份文件 ${BACKUP_FILE} 校验失败" | mailx -s "[ALERT] PMIS Daily Backup CORRUPTED" "${ALERT_MAIL}" || true
  exit 3
fi
echo "[STEP 3] verify ok"

# ---------- 4. 上传 OSS / 异地存储（可选，H7.3 修复：传输加密） ----------
# H7.3 修复：等保 2.0 三级要求"传输与存储加密"，原脚本明文上传 OSS
#   方案 A：OSS 服务端加密（推荐，零侵入）
#   方案 B：客户端 GPG 加密后上传（密钥由运维独立托管）
# 优先采用方案 A，配置 PMIS_BACKUP_GPG_PASSPHRASE 时同时启用方案 B 双重保险
if [ -n "${PMIS_BACKUP_OSS_BUCKET:-}" ] && command -v ossutil > /dev/null 2>&1; then
  echo "[STEP 4] upload to oss://${PMIS_BACKUP_OSS_BUCKET} (with server-side encryption)"
  # OSS 服务端加密：x-oss-server-side-encryption=KMS（阿里云 KMS 托管主密钥）
  ossutil cp "${BACKUP_FILE}" "oss://${PMIS_BACKUP_OSS_BUCKET}/daily/$(basename ${BACKUP_FILE})" \
      --meta x-oss-storage-class:IA \
      --meta x-oss-server-side-encryption:KMS \
      || echo "[WARN] oss upload failed, continue"
  # 可选：客户端 GPG 加密二次保护
  if [ -n "${PMIS_BACKUP_GPG_PASSPHRASE:-}" ] && command -v gpg > /dev/null 2>&1; then
    echo "  [STEP 4.1] GPG 加密后上传 .gpg 副本"
    gpg --batch --yes --passphrase "${PMIS_BACKUP_GPG_PASSPHRASE}" \
        -c "${BACKUP_FILE}" 2>/dev/null || echo "[WARN] gpg encrypt failed"
    if [ -f "${BACKUP_FILE}.gpg" ]; then
      ossutil cp "${BACKUP_FILE}.gpg" "oss://${PMIS_BACKUP_OSS_BUCKET}/daily/$(basename ${BACKUP_FILE}).gpg" \
          --meta x-oss-storage-class:Archive \
          || echo "[WARN] gpg oss upload failed"
      shred -u "${BACKUP_FILE}.gpg" 2>/dev/null || rm -f "${BACKUP_FILE}.gpg"
    fi
  fi
fi

# ---------- 5. 清理 7 天前的旧备份 ----------
echo "[STEP 5] cleanup backups older than ${RETENTION_DAYS} days"
DELETED=$(find "${BACKUP_DIR}" -name "pmis_daily_*.sql.gz" -mtime +${RETENTION_DAYS} -print -delete | wc -l)
echo "[STEP 5] deleted ${DELETED} old backup files"

# ---------- 6. 写入备份元数据（供监控巡检） ----------
echo "[STEP 6] write metadata"
cat > "${BACKUP_DIR}/.last_backup.json" <<EOF
{
  "type": "daily",
  "timestamp": "${TIMESTAMP}",
  "db": "${PG_DB}",
  "file": "$(basename ${BACKUP_FILE})",
  "size_bytes": ${BACKUP_SIZE},
  "duration_seconds": ${DURATION},
  "status": "SUCCESS"
}
EOF

echo "[PMIS Daily Backup] SUCCESS at $(date '+%F %T'), size=${BACKUP_SIZE}B duration=${DURATION}s"
echo "============================================================"
exit 0
