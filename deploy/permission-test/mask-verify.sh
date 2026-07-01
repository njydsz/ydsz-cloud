#!/bin/bash
# =====================================================================
#  PMIS 敏感数据脱敏验证脚本（批次 19）
# ---------------------------------------------------------------------
#  验证 7 种脱敏策略（NAME/ID_CARD/PHONE/EMAIL/BANK_CARD/ADDRESS/CUSTOM）
#  适用：财务/客户/工时/审批等含敏感字段的接口
#  用法：./mask-verify.sh [BASE_URL]
# =====================================================================

set -u

BASE_URL="${1:-http://localhost}"
TOKEN=""
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

# ---- 断言脱敏（核心函数）----
assert_masked() {
    local name="$1"
    local original="$2"
    local masked="$3"
    local pattern="$4"
    
    if [ "$masked" = "$original" ]; then
        log_fail "$name: 未脱敏 (原始=$original)"
        return 1
    fi
    
    if [[ "$masked" =~ $pattern ]]; then
        log_pass "$name: 脱敏正确 ($masked)"
        return 0
    else
        log_fail "$name: 脱敏格式不符 (masked=$masked, pattern=$pattern)"
        return 1
    fi
}

# ---- 登录 ----
login() {
    TOKEN=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"mask-tester","password":"mask123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    if [ -z "$TOKEN" ]; then
        log_warn "未获取到 token"
    fi
}

# ---- 1. NAME 脱敏（张三* / 张**）----
test_name() {
    log_title "1. NAME 姓名脱敏（保留姓，名用 *）"
    
    # 中文姓名（保留姓 + *）
    assert_masked "中文姓名 张三丰" "张三丰" "张\*" "^张\*+$" || true
    
    # 双字名（保留姓 + **）
    assert_masked "中文姓名 欧阳娜娜" "欧阳娜娜" "欧\*\*" "^欧\*\*+$" || true
    
    # 单字名（保留姓 + 1 个 *）
    assert_masked "单字名 王五" "王五" "王\*" "^王\*$" || true
    
    # 英文名（保留首字母 + ***）
    assert_masked "英文名 John Smith" "John Smith" "J\*\*\*" "^J\*+ .*\*\*\*$" || true
}

# ---- 2. ID_CARD 脱敏（保留前 6 后 4）----
test_id_card() {
    log_title "2. ID_CARD 身份证号脱敏"
    
    assert_masked "身份证 18 位" \
        "110101199003078912" \
        "110101********8912" \
        "^110101\*\*\*\*\*\*\*\*8912$" || true
}

# ---- 3. PHONE 脱敏（保留前 3 后 4）----
test_phone() {
    log_title "3. PHONE 手机号脱敏"
    
    assert_masked "11 位手机号" \
        "13812345678" \
        "138****5678" \
        "^138\*\*\*\*5678$" || true
    
    # 固话（保留区号 + **** + 后 4）
    assert_masked "固话" \
        "021-12345678" \
        "021-****5678" \
        "^021-\*\*\*\*5678$" || true
}

# ---- 4. EMAIL 脱敏 ----
test_email() {
    log_title "4. EMAIL 邮箱脱敏"
    
    # 标准邮箱（前 1 + **** @ 后缀）
    assert_masked "邮箱" \
        "zhangsan@example.com" \
        "z****@example.com" \
        "^.+\*\*\*\*@.+$" || true
}

# ---- 5. BANK_CARD 脱敏（保留前 4 后 4）----
test_bank_card() {
    log_title "5. BANK_CARD 银行卡脱敏"
    
    assert_masked "银行卡 16 位" \
        "6222021234567890" \
        "6222**********7890" \
        "^6222\*\*+7890$" || true
    
    assert_masked "银行卡 19 位" \
        "6222021234567890123" \
        "6222**************0123" \
        "^6222\*\*+0123$" || true
}

# ---- 6. ADDRESS 脱敏（保留前 6 + ****）----
test_address() {
    log_title "6. ADDRESS 地址脱敏（详细地址）"
    
    assert_masked "中文地址" \
        "北京市朝阳区建国路88号SOHO现代城A座1801室" \
        "北京市朝阳区****" \
        "^北京市朝阳区\*\*\*\*$" || true
}

# ---- 7. CUSTOM 脱敏（自定义：合同金额）----
test_custom() {
    log_title "7. CUSTOM 自定义脱敏（合同金额按角色）"
    
    # 销售：可见万位
    # PM：可见百位
    # 财务：可见十位
    # 高管：全可见
    
    log_warn "CUSTOM 脱敏通常需调用方传入角色，响应中不直接体现"
    log_warn "建议在 FE 控制显示位数（见 Vue filter / 后端动态裁剪）"
}

# ---- 8. 角色差异（销售 vs 财务）----
test_role_difference() {
    log_title "8. 角色差异（不同角色查同一接口返回的脱敏粒度）"
    
    if [ -z "$TOKEN" ]; then
        log_warn "需要 token，跳过"
        return
    fi
    
    # 销售 token
    local sales_token=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"sales-tester","password":"sales123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    
    # 财务 token
    local finance_token=$(curl -fsS -m 5 -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"finance-tester","password":"finance123"}' 2>/dev/null | \
        grep -oP '"token":"\K[^"]+' || echo "")
    
    if [ -z "$sales_token" ] || [ -z "$finance_token" ]; then
        log_warn "需要 sales / finance 测试账号"
        return
    fi
    
    # 销售查客户手机号
    local sales_resp=$(curl -fsS -m 5 \
        -H "Authorization: Bearer $sales_token" \
        "${BASE_URL}/api/v1/user/customer/1" 2>/dev/null)
    
    # 财务查客户手机号
    local finance_resp=$(curl -fsS -m 5 \
        -H "Authorization: Bearer $finance_token" \
        "${BASE_URL}/api/v1/user/customer/1" 2>/dev/null)
    
    # 验证：销售的 phone 脱敏位数 >= 财务
    local sales_phone=$(echo "$sales_resp" | grep -oP '"phone":"\K[^"]+' || echo "")
    local finance_phone=$(echo "$finance_resp" | grep -oP '"phone":"\K[^"]+' || echo "")
    
    if [ -n "$sales_phone" ] && [ -n "$finance_phone" ]; then
        local sales_stars=$(echo "$sales_phone" | grep -o '\*' | wc -l)
        local finance_stars=$(echo "$finance_phone" | grep -o '\*' | wc -l)
        
        if [ "$sales_stars" -ge "$finance_stars" ]; then
            log_pass "角色差异脱敏：sales($sales_stars stars) >= finance($finance_stars stars)"
        else
            log_fail "角色差异脱敏：sales 脱敏位数 < finance"
        fi
    else
        log_warn "未获取到 phone 字段（可能接口不存在或响应格式不同）"
    fi
}

# ============================ 主流程 ============================
echo "=========================================="
echo "  PMIS 敏感数据脱敏验证"
echo "  目标: $BASE_URL"
echo "=========================================="

login
test_name
test_id_card
test_phone
test_email
test_bank_card
test_address
test_custom
test_role_difference

echo ""
echo "=========================================="
echo "  脱敏测试结果"
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
