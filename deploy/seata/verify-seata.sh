#!/usr/bin/env bash
# =============================================================================
#  PMIS Seata Server 启动验证脚本
#  ----------------------------------------------------------------------------
#  验证项：
#    1) 容器运行状态
#    2) 7091 Admin 端口健康检查
#    3) 8091 HTTP 端口健康检查
#    4) Nacos 注册情况
#    5) PostgreSQL 业务库表结构
#  退出码：0=通过 1=失败
# =============================================================================
set -euo pipefail

SEATA_HOST="${PMIS_SEATA_HOST:-127.0.0.1}"
SEATA_HTTP_PORT="${PMIS_SEATA_HTTP_PORT:-8091}"
SEATA_ADMIN_PORT="${PMIS_SEATA_ADMIN_PORT:-7091}"
NACOS_HOST="${PMIS_NACOS_HOST:-127.0.0.1}"
NACOS_PORT="${PMIS_NACOS_PORT:-8848}"
PG_HOST="${PG_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-pmis}"
PG_PASSWORD="${PG_PASSWORD:-pmis@2026}"
PG_DB="${PG_DB:-pmis}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠ $*${NC}"; }
err()  { echo -e "${RED}✗ $*${NC}"; }
fail() { err "$*"; exit 1; }

echo "==========================================="
echo "  PMIS Seata 启动验证"
echo "  Host:    ${SEATA_HOST}"
echo "  HTTP:    ${SEATA_HTTP_PORT}"
echo "  Admin:   ${SEATA_ADMIN_PORT}"
echo "==========================================="

# ---------- 1) 容器运行状态 ----------
echo "[1/5] 检查 Seata 容器状态"
if command -v docker >/dev/null 2>&1; then
  CONTAINER_STATUS=$(docker inspect -f '{{.State.Status}}' pmis-seata 2>/dev/null || echo "not-found")
  if [ "${CONTAINER_STATUS}" != "running" ]; then
    fail "Seata 容器未运行，状态：${CONTAINER_STATUS}"
  fi
  ok "Seata 容器运行正常"
else
  warn "docker 命令未找到，跳过容器检查（请手动确认）"
fi

# ---------- 2) Admin 端口健康检查 ----------
echo "[2/5] 检查 ${SEATA_ADMIN_PORT} Admin 端口"
HTTP_CODE=$(curl -sS -o /tmp/seata_admin_health.json -w "%{http_code}" \
  --max-time 5 "http://${SEATA_HOST}:${SEATA_ADMIN_PORT}/api/v1/server/health" || echo "000")
if [ "${HTTP_CODE}" != "200" ]; then
  fail "Admin 端口 HTTP 状态码 ${HTTP_CODE}（期望 200）"
fi
HEALTH=$(jq -r '.data.status' < /tmp/seata_admin_health.json 2>/dev/null || echo "unknown")
if [ "${HEALTH}" != "UP" ]; then
  fail "Admin 健康状态：${HEALTH}（期望 UP）"
fi
ok "Admin 端口健康 (${HEALTH})"

# ---------- 3) HTTP 端口健康检查 ----------
echo "[3/5] 检查 ${SEATA_HTTP_PORT} HTTP 端口"
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" \
  --max-time 5 "http://${SEATA_HOST}:${SEATA_HTTP_PORT}/" || echo "000")
if [ "${HTTP_CODE}" != "200" ] && [ "${HTTP_CODE}" != "404" ]; then
  fail "HTTP 端口状态码 ${HTTP_CODE}（期望 200/404）"
fi
ok "HTTP 端口响应正常（状态 ${HTTP_CODE}）"

# ---------- 4) Nacos 注册检查 ----------
echo "[4/5] 检查 Seata 在 Nacos 中的注册"
NACOS_CODE=$(curl -sS -o /tmp/seata_nacos.json -w "%{http_code}" \
  --max-time 5 "http://${NACOS_HOST}:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=seata-server&groupName=SEATA_GROUP" || echo "000")
if [ "${NACOS_CODE}" != "200" ]; then
  warn "Nacos 注册查询失败（HTTP ${NACOS_CODE}），跳过（可能未配置 Nacos 注册）"
else
  INSTANCES=$(jq -r '.hosts | length' < /tmp/seata_nacos.json 2>/dev/null || echo "0")
  if [ "${INSTANCES}" -lt 1 ]; then
    warn "Nacos 中未发现 seata-server 实例（配置可能不同步）"
  else
    ok "Nacos 中注册了 ${INSTANCES} 个 seata-server 实例"
  fi
fi

# ---------- 5) PostgreSQL undo_log 表 ----------
echo "[5/5] 检查 PostgreSQL undo_log 表"
if command -v psql >/dev/null 2>&1; then
  PGPASSWORD="${PG_PASSWORD}" psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" -tAc \
    "SELECT to_regclass('public.undo_log') IS NOT NULL;" 2>/dev/null | tr -d '[:space:]' > /tmp/seata_undo.log
  HAS_UNDO=$(cat /tmp/seata_undo.log)
  if [ "${HAS_UNDO}" != "t" ]; then
    fail "undo_log 表未创建，请先执行 deploy/sql/04_undo_log.sql"
  fi
  ok "undo_log 表已存在"
else
  warn "psql 未找到，跳过表结构检查（请手动确认）"
fi

echo "==========================================="
ok "Seata 启动验证通过"
echo "  Admin Web UI: http://${SEATA_HOST}:${SEATA_ADMIN_PORT}"
echo "  业务分组:    pmis-tx-group (映射 default)"
echo "  客户端配置:  ${SEATA_HOST}:${SEATA_HTTP_PORT}"
echo "==========================================="
exit 0
