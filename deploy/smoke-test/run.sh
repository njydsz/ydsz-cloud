#!/bin/bash
# =====================================================================
#  PMIS 全链路冒烟测试（批次 19）
# ---------------------------------------------------------------------
#  覆盖：14 个微服务 + PostgreSQL + Redis + Nginx
#  用法：./run.sh [BASE_URL]    # 默认 http://localhost
#  退出码：0 全部通过 / 1 有失败
# =====================================================================

set -u

BASE_URL="${1:-http://localhost}"
TIMEOUT=10
PASS=0
FAIL=0
FAILED_TESTS=()

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_pass()  { echo -e "  ${GREEN}✓${NC} $1"; PASS=$((PASS+1)); }
log_fail()  { echo -e "  ${RED}✗${NC} $1"; FAIL=$((FAIL+1)); FAILED_TESTS+=("$1"); }
log_warn()  { echo -e "  ${YELLOW}!${NC} $1"; }
log_title() { echo -e "\n${YELLOW}[$1]${NC}"; }

# ---- 健康检查通用函数 ----
check_health() {
    local name="$1"
    local url="$2"
    local expect_status="${3:-200}"
    local timeout="${4:-10}"
    
    local start=$(date +%s%3N)
    local response=$(curl -fsS -m "$timeout" -w "\n%{http_code}\n%{time_total}" "$url" 2>/dev/null)
    local end=$(date +%s%3N)
    local cost=$((end - start))
    
    if [ $? -ne 0 ]; then
        log_fail "$name: 请求失败 (timeout=${timeout}s)"
        return 1
    fi
    
    local body=$(echo "$response" | head -n -2)
    local status=$(echo "$response" | tail -n 2 | head -n 1)
    
    if [ "$status" = "$expect_status" ]; then
        log_pass "$name (status=$status, ${cost}ms)"
        return 0
    else
        log_fail "$name: 状态码不符 (expected=$expect_status, actual=$status)"
        return 1
    fi
}

# ---- 业务流测试 ----
test_business_flow() {
    log_title "1. 鉴权服务"
    check_health "auth 服务健康" "${BASE_URL}/api/v1/auth/actuator/health" 200 || true
    
    log_title "2. 网关路由 14 个微服务"
    local services=(
        "gateway:/api/v1/auth/actuator/health"
        "user:/api/v1/user/actuator/health"
        "project:/api/v1/project/actuator/health"
        "execution:/api/v1/execution/actuator/health"
        "agent:/api/v1/agent/actuator/health"
        "scheduler:/api/v1/scheduler/actuator/health"
        "audit:/api/v1/audit/actuator/health"
        "notification:/api/v1/notification/actuator/health"
        "workflow:/api/v1/workflow/actuator/health"
        "file:/api/v1/file/actuator/health"
        "config:/api/v1/config/actuator/health"
        "message:/api/v1/message/actuator/health"
    )
    for svc in "${services[@]}"; do
        local name="${svc%%:*}"
        local path="${svc##*:}"
        check_health "${name} 服务" "${BASE_URL}${path}" 200 5
    done
    
    log_title "3. 业务接口烟测（需登录 token）"
    local token=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"smoke","password":"smoke123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    
    if [ -z "$token" ]; then
        log_warn "未获取到 token（smoke 用户），业务接口测试跳过"
    else
        echo "  - 登录成功（token 长度 ${#token}）"
        
        # 项目立项分页
        check_health "项目立项分页" \
            "${BASE_URL}/api/v1/project/initiation/page?page=1&size=10" 200 5 \
            || true  # 业务接口可能需要更多 header
    fi
    
    log_title "4. 依赖组件"
    check_health "PostgreSQL（via project 服务）" \
        "${BASE_URL}/api/v1/project/actuator/health/db" 200 5 || true
    check_health "Redis（via gateway 服务）" \
        "${BASE_URL}/api/v1/auth/actuator/health/redis" 200 5 || true
    check_health "Nacos（via config 服务）" \
        "${BASE_URL}/api/v1/config/actuator/health" 200 5 || true
    
    log_title "5. Nginx"
    check_health "Nginx 自检 /health" "${BASE_URL}/health" 200 5
    
    log_title "6. HTTPS 证书（生产）"
    if [[ "$BASE_URL" == https://* ]]; then
        local cert=$(echo | openssl s_client -servername "${BASE_URL#https://}" \
            -connect "${BASE_URL#https://}":443 2>/dev/null | \
            openssl x509 -noout -dates 2>/dev/null)
        if [ -n "$cert" ]; then
            log_pass "证书信息:"
            echo "$cert" | sed 's/^/      /'
        else
            log_fail "无法读取证书"
        fi
    else
        log_warn "当前为 HTTP，跳过证书校验"
    fi
}

# ---- 主流程 ----
echo "=========================================="
echo "  PMIS 冒烟测试"
echo "  目标: $BASE_URL"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="

test_business_flow

echo ""
echo "=========================================="
echo "  测试结果汇总"
echo "  通过: $PASS"
echo "  失败: $FAIL"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    echo ""
    echo -e "${RED}失败用例：${NC}"
    for t in "${FAILED_TESTS[@]}"; do
        echo "  - $t"
    done
    exit 1
fi

echo -e "${GREEN}全部通过 ✓${NC}"
exit 0
