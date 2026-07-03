#!/usr/bin/env bash
# ============================================================================
#  PMIS PostgreSQL 备份恢复演练脚本
#  --------------------------------------------------------------------------
#  用途：每月 1 号 04:00 自动触发，验证昨日全量备份可恢复
#  调用：deploy/backup/cron.d/pmis-backup (crontab)
#  依赖：docker、gunzip、psql（容器内）
#  退出码：0=成功 1=备份文件未找到 2=容器启动失败 3=恢复失败 4=数据校验失败
# ============================================================================
set -euo pipefail

# ---------- 配置（可通过环境变量覆盖） ----------
BACKUP_DIR="${PMIS_BACKUP_DIR:-/data/backup/pmis/daily}"
LOG_DIR="${PMIS_BACKUP_LOG_DIR:-/var/log/pmis/backup}"
CONTAINER_NAME="pmis-restore-test-$$"
PG_IMAGE="${PMIS_RESTORE_PG_IMAGE:-postgres:16}"
PG_PASSWORD="${PMIS_RESTORE_PG_PASSWORD:-pmis_restore_test_2026}"
TEST_DB="pmis"
ALERT_MAIL="${PMIS_ALERT_MAIL:-ops@ydsz-pmis.cn}"

# 关键表行数下限校验（值需根据生产实际调整，下限 = 至少要有数据）
declare -A TABLE_MIN_ROWS=(
    ["pmis_user_account"]=1
    ["pmis_role"]=1
    ["pmis_permission"]=1
    ["pmis_dict_type"]=1
    ["pmis_department"]=1
)

LOG_FILE="${LOG_DIR}/restore_test_$(date +%Y%m%d_%H%M%S).log"
mkdir -p "${LOG_DIR}"
exec > >(tee -a "${LOG_FILE}") 2>&1

echo "============================================================"
echo "[PMIS Restore Test] started at $(date '+%F %T')"
echo "  backup_dir=${BACKUP_DIR} pg_image=${PG_IMAGE}"
echo "============================================================"

# ---------- 1. 取最近一份全量备份 ----------
YESTERDAY=$(date -d 'yesterday' +%Y%m%d)
BACKUP_FILE=$(ls -t "${BACKUP_DIR}/pmis_daily_${YESTERDAY}_"*.sql.gz 2>/dev/null | head -1 || true)
if [ -z "${BACKUP_FILE}" ]; then
    # 兜底：取最近一份任意日期的备份
    BACKUP_FILE=$(ls -t "${BACKUP_DIR}/pmis_daily_"*.sql.gz 2>/dev/null | head -1 || true)
fi
if [ -z "${BACKUP_FILE}" ] || [ ! -f "${BACKUP_FILE}" ]; then
    echo "[FATAL] 未找到可用的全量备份文件，目录=${BACKUP_DIR}" >&2
    echo "Restore test FAILED: no backup file found" | mailx -s "[PMIS] 备份恢复演练失败" "${ALERT_MAIL}" || true
    exit 1
fi
echo "[STEP 1] 备份文件：${BACKUP_FILE}"

# ---------- 2. 启动临时 PG 容器 ----------
echo "[STEP 2] 启动临时容器 ${CONTAINER_NAME} ..."
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
if ! docker run -d --name "${CONTAINER_NAME}" \
        -e POSTGRES_PASSWORD="${PG_PASSWORD}" \
        -e POSTGRES_DB="${TEST_DB}" \
        "${PG_IMAGE}" >/dev/null; then
    echo "[FATAL] 容器启动失败" >&2
    exit 2
fi

# 等 PG 就绪
echo "  等待 PG 就绪 ..."
for i in $(seq 1 30); do
    if docker exec "${CONTAINER_NAME}" pg_isready -U postgres -d "${TEST_DB}" >/dev/null 2>&1; then
        echo "  PG 就绪（${i}s）"
        break
    fi
    sleep 1
done

# ---------- 3. 恢复备份 ----------
echo "[STEP 3] 恢复备份 ..."
if ! gunzip -c "${BACKUP_FILE}" | docker exec -i "${CONTAINER_NAME}" psql -U postgres -d "${TEST_DB}" -v ON_ERROR_STOP=1 >/tmp/restore.log 2>&1; then
    echo "[FATAL] 恢复失败，日志末尾 50 行：" >&2
    tail -50 /tmp/restore.log >&2
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    echo "Restore test FAILED: psql restore error" | mailx -s "[PMIS] 备份恢复演练失败" "${ALERT_MAIL}" || true
    exit 3
fi
echo "  恢复完成"

# ---------- 4. 校验关键表行数 ----------
echo "[STEP 4] 校验关键表行数 ..."
FAIL=0
for TABLE in "${!TABLE_MIN_ROWS[@]}"; do
    MIN=${TABLE_MIN_ROWS[$TABLE]}
    ROWS=$(docker exec "${CONTAINER_NAME}" psql -U postgres -d "${TEST_DB}" -tAc "SELECT count(*) FROM ${TABLE}" 2>/dev/null || echo "-1")
    if [ -z "${ROWS}" ] || [ "${ROWS}" = "-1" ]; then
        echo "  [FAIL] ${TABLE}: 表不存在或查询失败"
        FAIL=1
    elif [ "${ROWS}" -lt "${MIN}" ]; then
        echo "  [FAIL] ${TABLE}: rows=${ROWS} < min=${MIN}"
        FAIL=1
    else
        echo "  [OK]   ${TABLE}: rows=${ROWS} (min=${MIN})"
    fi
done

# ---------- 5. 清理临时容器 ----------
echo "[STEP 5] 清理临时容器 ..."
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

# ---------- 6. 汇总结果 ----------
if [ "${FAIL}" = "1" ]; then
    echo "============================================================"
    echo "[PMIS Restore Test] FAILED at $(date '+%F %T')"
    echo "============================================================"
    echo "Restore test FAILED: data validation error" | mailx -s "[PMIS] 备份恢复演练失败" "${ALERT_MAIL}" || true
    exit 4
fi

echo "============================================================"
echo "[PMIS Restore Test] SUCCESS at $(date '+%F %T')"
echo "  backup_file=${BACKUP_FILE}"
echo "  container=${CONTAINER_NAME} (已清理)"
echo "============================================================"
echo "Restore test SUCCESS" | mailx -s "[PMIS] 备份恢复演练成功" "${ALERT_MAIL}" || true
exit 0
