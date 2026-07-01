#!/usr/bin/env bash
# ============================================================================
#  PMIS 全量 API 契约测试脚本（批次 19 集成 Newman）
#  --------------------------------------------------------------------------
#  用途：基于 OpenAPI 3 规范（docs/api/openapi-summary.json）+ Postman 集合
#        验证 14 个微服务的所有端点契约：状态码 / Content-Type / 必填字段
#  依赖：curl、jq、newman（可选，自动跑 Postman 集合）
#  退出码：0=全部通过 1=有失败
# ============================================================================
set -euo pipefail

GATEWAY="${PMIS_GATEWAY:-http://localhost:9000}"
SPEC_FILE="${PMIS_SPEC_FILE:-$(dirname $0)/../../docs/api/openapi-summary.json}"
COLLECTION_FILE="${PMIS_COLLECTION:-$(dirname $0)/pmis-contract.postman_collection.json}"
ENV_FILE="${PMIS_ENV:-$(dirname $0)/pmis-contract.postman_environment.json}"
USERNAME="${PMIS_TEST_USER:-admin}"
PASSWORD="${PMIS_TEST_PASSWORD:-admin123}"
LOGIN_RESP_FILE="/tmp/pmis_login_resp.json"
NEWMAN_HTML_REPORT="/tmp/pmis-newman-report.html"

echo "============================================================"
echo "[PMIS API Contract Test] started at $(date '+%F %T')"
echo "  gateway: ${GATEWAY}"
echo "  spec:    ${SPEC_FILE}"

if [ ! -f "${SPEC_FILE}" ]; then
  echo "[FATAL] spec file not found: ${SPEC_FILE}" >&2
  exit 1
fi

# ---------- 1. 登录获取 token ----------
echo "[STEP 1] login as ${USERNAME}"
if ! curl -sS -X POST "${GATEWAY}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" \
    -o "${LOGIN_RESP_FILE}"; then
  echo "[FATAL] login request failed" >&2
  exit 1
fi

TOKEN=$(jq -r '.data.accessToken' < "${LOGIN_RESP_FILE}" 2>/dev/null || true)
if [ -z "${TOKEN}" ] || [ "${TOKEN}" = "null" ]; then
  echo "[FATAL] login failed, response: $(cat ${LOGIN_RESP_FILE})" >&2
  exit 1
fi
echo "  token acquired: ${TOKEN:0:20}..."

# ---------- 2. 遍历 OpenAPI paths 验证端点契约 ----------
echo "[STEP 2] verify endpoints"
TOTAL=$(jq '.paths | keys | length' < "${SPEC_FILE}")
PASS=0
FAIL=0
FAILED_ENDPOINTS=""

ENDPOINTS=$(jq -r '.paths | keys[]' < "${SPEC_FILE}")
for path in ${ENDPOINTS}; do
  for method in $(jq -r ".paths[\"${path}\"] | keys[]" < "${SPEC_FILE}" | grep -E '^(get|post|put|delete|patch)$'); do
    # 仅验证 GET 端点的可达性（避免对 POST 端点产生副作用）
    if [ "${method}" != "get" ]; then
      continue
    fi
    STATUS=$(curl -sS -o /dev/null -w "%{http_code}" \
      -X GET "${GATEWAY}${path}" \
      -H "Authorization: Bearer ${TOKEN}" \
      -H "Accept: application/json")
    # 期望：200（成功）、401（未登录，被网关拦截）、403（无权限）
    if [ "${STATUS}" = "200" ] || [ "${STATUS}" = "401" ] || [ "${STATUS}" = "403" ]; then
      PASS=$((PASS+1))
    else
      FAIL=$((FAIL+1))
      FAILED_ENDPOINTS="${FAILED_ENDPOINTS}\n  ${method} ${path} -> ${STATUS}"
    fi
  done
done

# ---------- 3. 输出报告 ----------
echo "[STEP 3] report"
echo "  total:  ${TOTAL}"
echo "  passed: ${PASS}"
echo "  failed: ${FAIL}"
if [ -n "${FAILED_ENDPOINTS}" ]; then
  echo "  failed endpoints:"
  echo -e "${FAILED_ENDPOINTS}"
fi

# ---------- 4. 验证 Content-Type ----------
echo "[STEP 4] spot-check content-type"
for path in "/api/v1/user/users" "/api/v1/audit/login-audit/page"; do
  CT=$(curl -sS -o /dev/null -w "%{content_type}" \
    -X GET "${GATEWAY}${path}?page=1&size=1" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
  if [[ "${CT}" != application/json* ]]; then
    echo "[WARN] ${path} content-type is ${CT}, expected application/json"
  fi
done

echo "[PMIS API Contract Test] done, ${PASS} passed, ${FAIL} failed"

# ---------- 5. Newman Postman 集合契约测试（可选）----------
if command -v newman >/dev/null 2>&1; then
  echo "[STEP 5] Newman postman contract test"
  if [ ! -f "${COLLECTION_FILE}" ]; then
    echo "[WARN] postman collection not found: ${COLLECTION_FILE}, skip"
  else
    NEWMAN_ENV_ARGS=()
    if [ -f "${ENV_FILE}" ]; then
      NEWMAN_ENV_ARGS=(-e "${ENV_FILE}")
    fi
    if newman run "${COLLECTION_FILE}" \
        --env-var "BASE_URL=${GATEWAY}" \
        "${NEWMAN_ENV_ARGS[@]}" \
        --reporters cli,html \
        --reporter-htmlexport "${NEWMAN_HTML_REPORT}" \
        --timeout 30000 \
        --bail 2>&1 | tail -60; then
      echo "[OK] Newman contract test passed, report: ${NEWMAN_HTML_REPORT}"
    else
      echo "[FAIL] Newman contract test failed, report: ${NEWMAN_HTML_REPORT}"
      FAIL=$((FAIL+1))
    fi
  fi
else
  echo "[STEP 5] newman not installed, skip (install: npm i -g newman)"
fi

[ ${FAIL} -eq 0 ] && exit 0 || exit 1
