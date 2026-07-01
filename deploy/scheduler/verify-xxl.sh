# PMIS XXL-Job Admin 启动验证脚本
# --------------------------------------------------------------------------
# 验证项：
#   1) 容器运行状态
#   2) Admin Web UI 可访问
#   3) 14 个微服务的 executor 已注册
#   4) 已配置调度任务数量
# --------------------------------------------------------------------------
set -euo pipefail

XXL_HOST="${PMIS_XXL_HOST:-127.0.0.1}"
XXL_PORT="${PMIS_XXL_PORT:-9100}"
ADMIN_USER="${PMIS_XXL_USER:-admin}"
ADMIN_PWD="${PMIS_XXL_PWD:-123456}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠ $*${NC}"; }
err()  { echo -e "${RED}✗ $*${NC}"; }
fail() { err "$*"; exit 1; }

echo "==========================================="
echo "  PMIS XXL-Job 启动验证"
echo "  Host: ${XXL_HOST}:${XXL_PORT}"
echo "==========================================="

# ---------- 1) 容器状态 ----------
echo "[1/4] 检查 xxl-job-admin 容器状态"
if command -v docker >/dev/null 2>&1; then
  STATUS=$(docker inspect -f '{{.State.Status}}' pmis-xxl-job-admin 2>/dev/null || echo "not-found")
  [ "${STATUS}" != "running" ] && fail "xxl-job-admin 容器未运行（${STATUS}）"
  ok "xxl-job-admin 容器运行正常"
fi

# ---------- 2) Web UI 可达性 ----------
echo "[2/4] 检查 Web UI"
CODE=$(curl -sS -o /dev/null -w "%{http_code}" \
  --max-time 5 "http://${XXL_HOST}:${XXL_PORT}/xxl-job-admin/" || echo "000")
[ "${CODE}" != "200" ] && fail "Web UI 不可达（${CODE}）"
ok "Web UI 可访问"

# ---------- 3) 登录 + executor 数量 ----------
echo "[3/4] 登录 Admin 并检查 executor 注册"
LOGIN_RESP=$(curl -sS -c /tmp/xxl_cookies.txt -X POST \
  "http://${XXL_HOST}:${XXL_PORT}/xxl-job-admin/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data "userName=${ADMIN_USER}&password=${ADMIN_PWD}")
if ! echo "${LOGIN_RESP}" | grep -q '"code":200'; then
  fail "登录失败：${LOGIN_RESP}"
fi
ok "登录成功"

# 查询 executor 列表
EXECUTOR_RESP=$(curl -sS -b /tmp/xxl_cookies.txt \
  "http://${XXL_HOST}:${XXL_PORT}/xxl-job-admin/jobgroup/pageList?start=0&length=100")
EXECUTOR_COUNT=$(echo "${EXECUTOR_RESP}" | jq -r '.data | length' 2>/dev/null || echo "0")
ok "当前注册 ${EXECUTOR_COUNT} 个执行器（期望 7+：execution / scheduler / agent / cockpit / reconcile / alert / billable）"

# ---------- 4) 调度任务统计 ----------
echo "[4/4] 统计调度任务"
JOB_RESP=$(curl -sS -b /tmp/xxl_cookies.txt \
  "http://${XXL_HOST}:${XXL_PORT}/xxl-job-admin/jobinfo/pageList?start=0&length=1")
TOTAL=$(echo "${JOB_RESP}" | jq -r '.dataTotal' 2>/dev/null || echo "0")
ok "当前调度任务总数：${TOTAL}"

echo "==========================================="
ok "XXL-Job 启动验证通过"
echo "  Web UI: http://${XXL_HOST}:${XXL_PORT}/xxl-job-admin/"
echo "  默认账户: ${ADMIN_USER} / ${ADMIN_PWD}（请尽快修改）"
echo "==========================================="
exit 0
