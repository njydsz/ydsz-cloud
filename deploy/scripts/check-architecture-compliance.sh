#!/bin/bash
# PMIS 项目架构合规检查脚本 v2.0
#
# 检查内容：
#   1. 业务模块 ResultCode 枚举覆盖率
#   2. 公共模块 POM 依赖覆盖率（cache/event/notify/search/queue）
#   3. HealthIndicator 继承规范
#   4. SearchProvider 实现覆盖率
#   5. Executors 原生线程池残留检查
#   6. 前端 @ydsz/shared-api 依赖覆盖率
#
# 使用方式：bash deploy/scripts/check-architecture-compliance.sh
#
# @author ydsz-team
# @since 1.0.0

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/ydsz-backend"
FRONTEND_DIR="$PROJECT_ROOT/ydsz-frontend"

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; ((PASS_COUNT++)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; ((FAIL_COUNT++)); }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; ((WARN_COUNT++)); }

echo "============================================"
echo "  PMIS 架构合规检查 v2.0"
echo "============================================"
echo ""

# ========================================
# 1. 业务模块 ResultCode 枚举覆盖率
# ========================================
echo "--- 1. ResultCode 枚举覆盖率 ---"
MODULES=("project" "system" "workflow" "message" "cronjob" "literule" "agent" "userinfo" "nextwiki")
for mod in "${MODULES[@]}"; do
    # 查找 ResultCode/ExceptionCode 枚举文件
    found=$(find "$BACKEND_DIR/ydsz-$mod" -name "*ResultCode.java" -o -name "*ExceptionCode.java" 2>/dev/null | head -1)
    if [ -n "$found" ]; then
        pass "$mod 模块有 ResultCode/ExceptionCode 枚举"
    else
        fail "$mod 模块缺少 ResultCode/ExceptionCode 枚举"
    fi
done
echo ""

# ========================================
# 2. 公共模块 POM 依赖覆盖率
# ========================================
echo "--- 2. 公共模块 POM 依赖覆盖率 ---"
COMMON_DEPS=("common-cache" "common-event" "common-notify" "common-search")
for dep in "${COMMON_DEPS[@]}"; do
    echo "  检查 $dep 依赖..."
    for mod in "${MODULES[@]}"; do
        pom_file="$BACKEND_DIR/ydsz-$mod/ydsz-$mod-server/pom.xml"
        if [ -f "$pom_file" ]; then
            if grep -q "$dep" "$pom_file" 2>/dev/null; then
                pass "  $mod-server 有 $dep 依赖"
            else
                warn "  $mod-server 缺少 $dep 依赖"
            fi
        fi
    done
done
echo ""

# ========================================
# 3. HealthIndicator 继承规范
# ========================================
echo "--- 3. HealthIndicator 继承规范 ---"
for mod in "${MODULES[@]}"; do
    hi_file=$(find "$BACKEND_DIR/ydsz-$mod" -name "*HealthIndicator.java" -not -path "*/test/*" 2>/dev/null | head -1)
    if [ -n "$hi_file" ]; then
        if grep -q "extends AbstractModuleHealthIndicator" "$hi_file" 2>/dev/null; then
            pass "$mod HealthIndicator 继承 AbstractModuleHealthIndicator"
        else
            fail "$mod HealthIndicator 未继承 AbstractModuleHealthIndicator"
        fi
    else
        fail "$mod 模块缺少 HealthIndicator"
    fi
done
echo ""

# ========================================
# 4. SearchProvider 实现覆盖率
# ========================================
echo "--- 4. SearchProvider 实现覆盖率 ---"
for mod in "${MODULES[@]}"; do
    sp_file=$(find "$BACKEND_DIR/ydsz-$mod" -name "*SearchProvider.java" -not -path "*/test/*" 2>/dev/null | head -1)
    if [ -n "$sp_file" ]; then
        pass "$mod 模块有 SearchProvider"
    else
        fail "$mod 模块缺少 SearchProvider"
    fi
done
echo ""

# ========================================
# 5. Executors 原生线程池残留检查
# ========================================
echo "--- 5. Executors 原生线程池残留检查 ---"
for mod in "${MODULES[@]}"; do
    src_dir="$BACKEND_DIR/ydsz-$mod/ydsz-$mod-server/src/main/java"
    if [ -d "$src_dir" ]; then
        # 检查 Executors.newFixedThreadPool / newCachedThreadPool（排除 ScheduledThreadPool）
        found=$(grep -rn "Executors\.newFixedThreadPool\|Executors\.newCachedThreadPool\|Executors\.newVirtualThread" "$src_dir" 2>/dev/null | grep -v "//.*Executors" | head -1)
        if [ -z "$found" ]; then
            pass "$mod 模块无 Executors 原生线程池残留"
        else
            fail "$mod 模块有 Executors 残留: $found"
        fi
    fi
done
echo ""

# ========================================
# 6. 前端 @ydsz/shared-api 依赖覆盖率
# ========================================
echo "--- 6. 前端 @ydsz/shared-api 依赖覆盖率 ---"
if [ -d "$FRONTEND_DIR/apps" ]; then
    for app_dir in "$FRONTEND_DIR"/apps/*-web; do
        app_name=$(basename "$app_dir")
        pkg_json="$app_dir/package.json"
        if [ -f "$pkg_json" ]; then
            if grep -q "@ydsz/shared-api" "$pkg_json" 2>/dev/null; then
                pass "$app_name 有 @ydsz/shared-api 依赖"
            else
                fail "$app_name 缺少 @ydsz/shared-api 依赖"
            fi
        fi
    done
else
    warn "前端目录不存在，跳过检查"
fi
echo ""

# ========================================
# 汇总
# ========================================
echo "============================================"
echo "  检查结果汇总"
echo "============================================"
echo -e "  ${GREEN}PASS: $PASS_COUNT${NC}"
echo -e "  ${RED}FAIL: $FAIL_COUNT${NC}"
echo -e "  ${YELLOW}WARN: $WARN_COUNT${NC}"
echo ""

if [ "$FAIL_COUNT" -gt 0 ]; then
    echo -e "${RED}架构合规检查未通过！${NC}"
    exit 1
else
    echo -e "${GREEN}架构合规检查通过 ✅${NC}"
    exit 0
fi
