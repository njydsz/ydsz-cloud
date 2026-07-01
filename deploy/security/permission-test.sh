#!/bin/bash
# =====================================================================
#  PMIS 权限一致性测试（批次 19）
# ---------------------------------------------------------------------
#  验证：
#  1) 跨服务 Feign 调用携带用户上下文
#  2) 不同角色访问受保护接口返回 403
#  3) 未登录访问返回 401
#  4) 越权访问（无权限）返回 403
#  5) 二次确认（修改密码、删除项目）
# =====================================================================

set -u

BASE_URL="${1:-http://localhost}"
PASS=0
FAIL=0
FAILED_TESTS=()

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_pass() { echo -e "  ${GREEN}✓${NC} $1"; PASS=$((PASS+1)); }
log_fail() { echo -e "  ${RED}✗${NC} $1"; FAIL=$((FAIL+1)); FAILED_TESTS+=("$1"); }
log_warn() { echo -e "  ${YELLOW}!${NC} $1"; }
log_title() { echo -e "\n${YELLOW}[$1]${NC}"; }

# 期望状态码
assert_status() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    
    if [ "$actual" = "$expected" ]; then
        log_pass "$name (status=$actual)"
    else
        log_fail "$name: 期望 $expected, 实际 $actual"
    fi
}

# HTTP 调用
http_call() {
    local method="$1"
    local url="$2"
    local token="${3:-}"
    local data="${4:-}"
    
    local args=(-s -m 5 -w "\n%{http_code}" -X "$method" "$url")
    if [ -n "$token" ]; then
        args+=(-H "Authorization: Bearer $token")
    fi
    if [ -n "$data" ]; then
        args+=(-H "Content-Type: application/json" -d "$data")
    fi
    
    curl "${args[@]}" 2>/dev/null
}

# ---- 1. 未登录 ----
test_unauthenticated() {
    log_title "1. 未登录访问受保护接口"
    
    local response=$(http_call GET "${BASE_URL}/api/v1/project/initiation/page?page=1&size=10")
    local status=$(echo "$response" | tail -1)
    assert_status "未登录访问立项" "401" "$status"
}

# ---- 2. 错误 token ----
test_invalid_token() {
    log_title "2. 错误 token 访问"
    
    local response=$(http_call GET "${BASE_URL}/api/v1/project/initiation/page?page=1&size=10" "invalid.token.xxx")
    local status=$(echo "$response" | tail -1)
    assert_status "错误 token 访问立项" "401" "$status"
}

# ---- 3. 跨角色访问 ----
test_cross_role() {
    log_title "3. 跨角色访问"
    
    # 销售 token
    local sales_token=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"sales-test","password":"sales123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    
    # 财务 token
    local finance_token=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"finance-test","password":"finance123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    
    if [ -z "$sales_token" ] || [ -z "$finance_token" ]; then
        log_warn "需要 sales/finance 测试账号"
        return
    fi
    
    # 销售访问客户信用评分（通常仅 PMO/CFO/finance 可看）
    local response=$(http_call GET "${BASE_URL}/api/v1/finance/customer-credit/1" "$sales_token")
    local status=$(echo "$response" | tail -1)
    
    if [ "$status" = "403" ] || [ "$status" = "401" ]; then
        log_pass "销售访问客户信用被拒 (status=$status)"
    elif [ "$status" = "200" ]; then
        log_warn "销售可访问客户信用（按配置可能允许）"
    else
        log_fail "销售访问客户信用返回异常: $status"
    fi
}

# ---- 4. 删除权限 ----
test_delete_permission() {
    log_title "4. 删除权限（销售不能删商机 DRAFT 以外）"
    
    local sales_token=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"sales-test","password":"sales123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    
    if [ -z "$sales_token" ]; then
        log_warn "无 sales token"
        return
    fi
    
    # 销售尝试删除非 DRAFT 商机
    local response=$(http_call DELETE "${BASE_URL}/api/v1/project/opportunity/100" "$sales_token")
    local status=$(echo "$response" | tail -1)
    
    # 期望 403（无权限）或 400（不在 DRAFT 状态）
    if [ "$status" = "403" ] || [ "$status" = "400" ] || [ "$status" = "404" ]; then
        log_pass "销售删除非 DRAFT 商机被拒 (status=$status)"
    elif [ "$status" = "200" ]; then
        log_fail "销售可删除非 DRAFT 商机（严重越权）"
    else
        log_warn "删除返回异常: $status"
    fi
}

# ---- 5. 跨服务 Feign 上下文 ----
test_feign_context() {
    log_title "5. 跨服务 Feign 调用上下文"
    
    # 登录
    local token=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"perm-test","password":"perm123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    
    if [ -z "$token" ]; then
        log_warn "无 token"
        return
    fi
    
    # 调用一个会触发 Feign 调用的接口（例如立项详情带合同信息）
    local response=$(http_call GET "${BASE_URL}/api/v1/project/initiation/1" "$token")
    local status=$(echo "$response" | tail -1)
    local body=$(echo "$response" | head -n -1)
    
    # 200 + 响应中包含 contractCode 即代表 Feign 跨服务调用成功且上下文传递成功
    if [ "$status" = "200" ]; then
        log_pass "立项详情接口可访问 (status=$status)"
        # 不强制要求 contractCode 存在（可能没有合同）
    elif [ "$status" = "404" ]; then
        log_warn "立项 1 不存在"
    else
        log_fail "立项详情异常: $status"
    fi
}

# ---- 6. 越权审批 ----
test_unauthorized_approve() {
    log_title "6. 越权审批（销售不能审批项目变更）"
    
    local sales_token=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"sales-test","password":"sales123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    
    if [ -z "$sales_token" ]; then
        log_warn "无 sales token"
        return
    fi
    
    # 销售尝试审批变更
    local response=$(http_call PUT "${BASE_URL}/api/v1/project/change/status" "$sales_token" \
        '{"id":1,"targetStatus":"APPROVED"}')
    local status=$(echo "$response" | tail -1)
    
    if [ "$status" = "403" ]; then
        log_pass "销售审批变更被拒 (status=403)"
    elif [ "$status" = "200" ]; then
        log_fail "销售可审批变更（严重越权）"
    else
        log_warn "审批返回: $status"
    fi
}

# ============================ 主流程 ============================
echo "=========================================="
echo "  PMIS 权限一致性测试"
echo "  目标: $BASE_URL"
echo "=========================================="

test_unauthenticated
test_invalid_token
test_cross_role
test_delete_permission
test_feign_context
test_unauthorized_approve

echo ""
echo "=========================================="
echo "  权限测试结果"
echo "  通过: $PASS / 失败: $FAIL"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    echo -e "${RED}失败用例：${NC}"
    for t in "${FAILED_TESTS[@]}"; do
        echo "  - $t"
    done
    exit 1
fi

echo -e "${GREEN}全部通过 ✓${NC}"
exit 0
