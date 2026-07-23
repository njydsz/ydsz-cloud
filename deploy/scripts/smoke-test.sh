#!/usr/bin/env bash
# =============================================================================
#  YDSZ · 部署后冒烟测试（Linux/macOS）
# -----------------------------------------------------------------------------
#  用法:
#    bash deploy/scripts/smoke-test.sh [GATEWAY_URL]
#
#  默认: http://127.0.0.1:9000
#
#  测试项（对齐 SRE 上线前 checklist）:
#    1. Gateway 健康 /actuator/health
#    2. 各微服务通过 gateway 路由的 /actuator/health/{liveness,readiness}
#    3. Gateway /actuator/gateway/routes 路由表完整
#    4. 登录接口 (默认 admin/admin123，可通过 SMOKE_USER/SMOKE_PASS 覆盖) — 拿到 token
#    5. 用 token 调用 /userinfo/users/me — 鉴权链路
#    6. Swagger UI /swagger-ui.html 可访问
#    7. CORS 预检 OPTIONS 请求
#    8. 内部头剥离校验（伪造 X-User-Id 应被网关剥离）
#    9. 路径穿越拦截（/../etc/passwd 应返回 400）
# =============================================================================

set -uo pipefail

GATEWAY_URL="${1:-http://127.0.0.1:9000}"
TIMEOUT=10
PASS=0
FAIL=0
SKIP=0

# 登录凭据（保留默认值方便开发，生产可通过环境变量覆盖）
SMOKE_USER="${SMOKE_USER:-admin}"
SMOKE_PASS="${SMOKE_PASS:-admin123}"

# 颜色输出
RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[1;33m'
NC=$'\033[0m'

ok()   { echo "${GREEN}[PASS]${NC} $1"; PASS=$((PASS+1)); }
fail() { echo "${RED}[FAIL]${NC} $1"; FAIL=$((FAIL+1)); }
skip() { echo "${YELLOW}[SKIP]${NC} $1"; SKIP=$((SKIP+1)); }

echo "================================================================"
echo "  YDSZ · Smoke Test"
echo "  Gateway: ${GATEWAY_URL}"
echo "================================================================"
echo ""

# ----------------------------------------------------------------------------
# 1. Gateway 主健康检查
# ----------------------------------------------------------------------------
echo "▶ 1. Gateway 健康检查"
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${TIMEOUT} "${GATEWAY_URL}/actuator/health")
if [[ "${HEALTH}" == "200" ]]; then
    BODY=$(curl -s --max-time ${TIMEOUT} "${GATEWAY_URL}/actuator/health")
    ok "Gateway /actuator/health → 200 (body: ${BODY})"
else
    fail "Gateway /actuator/health → ${HEALTH} (期望 200)"
fi

# ----------------------------------------------------------------------------
# 2. Gateway 路由表
# ----------------------------------------------------------------------------
echo ""
echo "▶ 2. Gateway 路由表"
ROUTES=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${TIMEOUT} "${GATEWAY_URL}/actuator/gateway/routes")
if [[ "${ROUTES}" == "200" ]]; then
    ok "Gateway /actuator/gateway/routes → 200"
else
    skip "Gateway /actuator/gateway/routes → ${ROUTES} (可能未开启 actuator 详情)"
fi

# ----------------------------------------------------------------------------
# 3. 后端微服务健康检查（通过 gateway 路由）
# ----------------------------------------------------------------------------
echo ""
echo "▶ 3. 后端微服务健康检查（通过 Gateway 路由）"
# 端口分配（2026-07-08 统一，与 cd-deploy.yml matrix 一致）:
#   gateway 9000 / userinfo 9001 / system 9002 / project 9003 / message 9004
#   cronjob 9005 / workflow 9006 / agent 9007
# 通过 Gateway 路由访问，端口仅作参考（实际由 Gateway 转发）
SERVICES=(
    "ydsz-userinfo:9001"
    "ydsz-system:9002"
    "ydsz-project:9003"
    "ydsz-message:9004"
    "ydsz-cronjob:9005"
    "ydsz-workflow:9006"
    "ydsz-agent:9007"
)
for SVC in "${SERVICES[@]}"; do
    NAME="${SVC%%:*}"
    CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${TIMEOUT} "${GATEWAY_URL}/${NAME}/actuator/health")
    if [[ "${CODE}" == "200" ]]; then
        ok "${NAME}/actuator/health → 200"
    else
        fail "${NAME}/actuator/health → ${CODE} (期望 200)"
    fi
done

# ----------------------------------------------------------------------------
# 4. 登录接口（admin/admin123）
# ----------------------------------------------------------------------------
echo ""
echo "▶ 4. 登录接口"
LOGIN_RESP=$(curl -s --max-time ${TIMEOUT} -X POST "${GATEWAY_URL}/ydsz-userinfo/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${SMOKE_USER}\",\"password\":\"${SMOKE_PASS}\"}")
TOKEN=$(echo "${LOGIN_RESP}" | grep -oE '"token":"[^"]+"' | head -1 | cut -d'"' -f4)
if [[ -n "${TOKEN}" ]]; then
    ok "POST /auth/login → 拿到 token (前16字符: ${TOKEN:0:16}...)"
else
    fail "POST /auth/login → 未拿到 token (响应: ${LOGIN_RESP:0:200})"
fi

# ----------------------------------------------------------------------------
# 5. 鉴权链路（用 token 调用 /userinfo/users/me）
# ----------------------------------------------------------------------------
echo ""
echo "▶ 5. 鉴权链路"
if [[ -n "${TOKEN}" ]]; then
    ME_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${TIMEOUT} \
        "${GATEWAY_URL}/ydsz-userinfo/users/me" \
        -H "Authorization: Bearer ${TOKEN}")
    if [[ "${ME_CODE}" == "200" ]]; then
        ok "GET /users/me with token → 200"
    else
        fail "GET /users/me with token → ${ME_CODE} (期望 200)"
    fi
else
    skip "GET /users/me (无 token,跳过)"
fi

# ----------------------------------------------------------------------------
# 6. Swagger UI 可访问性
# ----------------------------------------------------------------------------
echo ""
echo "▶ 6. Swagger UI"
SWAGGER=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${TIMEOUT} "${GATEWAY_URL}/swagger-ui.html" -L)
if [[ "${SWAGGER}" == "200" ]]; then
    ok "/swagger-ui.html → 200"
else
    skip "/swagger-ui.html → ${SWAGGER} (可能未启用)"
fi

# ----------------------------------------------------------------------------
# 7. CORS 预检 OPTIONS
# ----------------------------------------------------------------------------
echo ""
echo "▶ 7. CORS 预检"
CORS_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${TIMEOUT} -X OPTIONS "${GATEWAY_URL}/ydsz-userinfo/auth/login" \
    -H "Origin: http://localhost:5173" \
    -H "Access-Control-Request-Method: POST" \
    -H "Access-Control-Request-Headers: Content-Type")
if [[ "${CORS_CODE}" == "200" || "${CORS_CODE}" == "204" ]]; then
    ok "OPTIONS preflight → ${CORS_CODE}"
else
    fail "OPTIONS preflight → ${CORS_CODE} (期望 200/204)"
fi

# ----------------------------------------------------------------------------
# 8. 内部头剥离（伪造 X-User-Id 应被剥离或忽略）
# ----------------------------------------------------------------------------
echo ""
echo "▶ 8. 内部头剥离"
FORGED_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${TIMEOUT} \
    "${GATEWAY_URL}/ydsz-userinfo/users/me" \
    -H "X-User-Id: 99999" \
    -H "X-Username: fake-admin" \
    -H "X-User-Roles: ROLE_ADMIN")
if [[ "${FORGED_CODE}" == "401" || "${FORGED_CODE}" == "403" ]]; then
    ok "伪造 X-User-Id → ${FORGED_CODE} (网关已剥离伪造头)"
else
    fail "伪造 X-User-Id → ${FORGED_CODE} (期望 401/403,可能未剥离伪造头)"
fi

# ----------------------------------------------------------------------------
# 9. 路径穿越拦截
# ----------------------------------------------------------------------------
echo ""
echo "▶ 9. 路径穿越拦截"
TRAVERSAL_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${TIMEOUT} \
    "${GATEWAY_URL}/ydsz-userinfo/../../etc/passwd")
if [[ "${TRAVERSAL_CODE}" == "400" ]]; then
    ok "/../../etc/passwd → 400 (路径穿越已拦截)"
else
    fail "/../../etc/passwd → ${TRAVERSAL_CODE} (期望 400,可能存在路径穿越漏洞)"
fi

# ----------------------------------------------------------------------------
# 汇总
# ----------------------------------------------------------------------------
echo ""
echo "================================================================"
echo "  Smoke Test 汇总"
echo "================================================================"
echo "  ${GREEN}PASS: ${PASS}${NC}  ${RED}FAIL: ${FAIL}${NC}  ${YELLOW}SKIP: ${SKIP}${NC}"
echo ""

if [[ ${FAIL} -gt 0 ]]; then
    echo "${RED}存在失败项,请检查后再上线!${NC}"
    exit 1
fi
echo "${GREEN}所有关键项通过,可继续上线流程。${NC}"
exit 0
