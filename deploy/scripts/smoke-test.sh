#!/usr/bin/env bash
# =============================================================================
# YDSZ 冒烟测试脚本
# -----------------------------------------------------------------------------
# 作用:  部署后对服务进行基本的健康检查 + 核心端点可达性验证
# 用法:  STAGING_URL=https://staging.ydsz.ydsz.cn ./deploy/scripts/smoke-test.sh
#        PRODUCTION_URL=https://ydsz.ydsz.cn ./deploy/scripts/smoke-test.sh
# =============================================================================
set -euo pipefail

BASE_URL="${STAGING_URL:-${PRODUCTION_URL:-http://localhost:8080}}"
MAX_RETRIES=30
RETRY_INTERVAL=5

echo "=========================================="
echo "  YDSZ Smoke Test"
echo "  Target: ${BASE_URL}"
echo "=========================================="

# 等待服务就绪
wait_for_ready() {
  local url="$1"
  local name="$2"
  for i in $(seq 1 ${MAX_RETRIES}); do
    if curl -sf "${url}" > /dev/null 2>&1; then
      echo "✅ ${name} is ready (attempt ${i})"
      return 0
    fi
    echo "  Waiting for ${name}... (attempt ${i}/${MAX_RETRIES})"
    sleep ${RETRY_INTERVAL}
  done
  echo "❌ ${name} is not ready after ${MAX_RETRIES} attempts"
  return 1
}

# 测试 Gateway 健康
wait_for_ready "${BASE_URL}/actuator/health" "Gateway"

# 测试 Gateway actuator
HEALTH_RESPONSE=$(curl -sf "${BASE_URL}/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')
HEALTH_STATUS=$(echo "${HEALTH_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")

if [ "${HEALTH_STATUS}" != "UP" ]; then
  echo "❌ Gateway health check failed: ${HEALTH_STATUS}"
  exit 1
fi
echo "✅ Gateway health: UP"

# 测试核心端点可达性（通过 Gateway 路由）
ENDPOINTS=(
  "/actuator/health"
  "/api/v1/auth/captcha"
)

for endpoint in "${ENDPOINTS[@]}"; do
  HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "${BASE_URL}${endpoint}" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "401" ] || [ "${HTTP_CODE}" = "403" ]; then
    echo "✅ ${endpoint} → ${HTTP_CODE}"
  else
    echo "❌ ${endpoint} → ${HTTP_CODE} (expected 200/401/403)"
    exit 1
  fi
done

echo "=========================================="
echo "  ✅ All smoke tests passed!"
echo "=========================================="
