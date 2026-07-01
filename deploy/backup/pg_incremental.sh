#!/usr/bin/env bash
# ============================================================================
#  PMIS PostgreSQL 增量备份（WAL 归档）脚本
#  --------------------------------------------------------------------------
#  用途：每小时触发一次，强制 archive_command 归档当前 WAL 段
#  原理：基于 PostgreSQL continuous archiving，依赖 archive_mode=on
#        + archive_command 在 postgresql.conf 中配置指向此脚本
#  调用：由 postgresql.conf 的 archive_command 调用，而非 crontab
#  退出码：0=成功 1=归档失败
# ============================================================================
set -euo pipefail

# ---------- 入参：archive_command 传 %f (filename) %p (full path) ----------
WAL_FILE_NAME="${1:-}"
WAL_FILE_PATH="${2:-}"

# ---------- 配置 ----------
WAL_ARCHIVE_DIR="${PMIS_WAL_ARCHIVE_DIR:-/data/backup/pmis/wal}"
RETENTION_HOURS="${PMIS_WAL_RETENTION_HOURS:-168}"  # 7 天
LOG_DIR="${PMIS_BACKUP_LOG_DIR:-/var/log/pmis/backup}"
ALERT_MAIL="${PMIS_ALERT_MAIL:-ops@ydsz-pmis.cn}"

# ---------- 初始化 ----------
mkdir -p "${WAL_ARCHIVE_DIR}" "${LOG_DIR}"
WAL_TARGET="${WAL_ARCHIVE_DIR}/${WAL_FILE_NAME}"
LOG_FILE="${LOG_DIR}/pg_wal_archive.log"

echo "[$(date '+%F %T')] archive wal=${WAL_FILE_NAME} from=${WAL_FILE_PATH}" >> "${LOG_FILE}"

# ---------- 1. 拷贝 WAL 段到归档目录（gzip 压缩） ----------
if [ -z "${WAL_FILE_PATH}" ] || [ ! -f "${WAL_FILE_PATH}" ]; then
  echo "[FATAL] wal source not found: ${WAL_FILE_PATH}" >> "${LOG_FILE}"
  echo "PMIS WAL 归档失败：源文件 ${WAL_FILE_PATH} 不存在" | mailx -s "[ALERT] PMIS WAL Archive FAILED" "${ALERT_MAIL}" || true
  exit 1
fi

if ! gzip -c "${WAL_FILE_PATH}" > "${WAL_TARGET}.gz"; then
  echo "[FATAL] gzip failed for ${WAL_FILE_PATH}" >> "${LOG_FILE}"
  exit 1
fi
chmod 600 "${WAL_TARGET}.gz"

# ---------- 2. 上传 OSS / 异地 ----------
if [ -n "${PMIS_BACKUP_OSS_BUCKET:-}" ] && command -v ossutil > /dev/null 2>&1; then
  ossutil cp "${WAL_TARGET}.gz" "oss://${PMIS_BACKUP_OSS_BUCKET}/wal/${WAL_FILE_NAME}.gz" --meta x-oss-storage-class:IA >> "${LOG_FILE}" 2>&1 || echo "[WARN] oss upload failed for ${WAL_FILE_NAME}" >> "${LOG_FILE}"
fi

# ---------- 3. 清理超过保留时间的 WAL ----------
find "${WAL_ARCHIVE_DIR}" -name "*.gz" -mmin +$((RETENTION_HOURS * 60)) -delete 2>/dev/null || true

echo "[$(date '+%F %T')] archived ${WAL_FILE_NAME} ok" >> "${LOG_FILE}"
exit 0
