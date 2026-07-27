#!/bin/bash
#
# 架构合规性检查脚本
# 用于自动化检查项目架构规范的执行情况
#
# 检查项：
# 1. 重复工具类检查（禁止业务模块自定义 Helper/Utils/Util）
# 2. 常量管理检查（禁止业务模块自定义常量类）
# 3. NameAssembler 使用规范检查
# 4. 事件驱动架构规范检查
# 5. Excel 导出标准化检查
# 6. 前端通用组件使用检查
#
# 使用方式：
#   ./check-architecture-compliance.sh [--strict]
#
# 参数：
#   --strict  严格模式，发现违规时返回非零退出码（用于 CI 阻断）
#
# @author ydsz-team
# @since 1.0.0
#

set -e

# 颜色定义
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_ROOT="$PROJECT_ROOT/ydsz-backend"
FRONTEND_ROOT="$PROJECT_ROOT/ydsz-frontend"

# 严格模式标志
STRICT_MODE=false
if [[ "$1" == "--strict" ]]; then
  STRICT_MODE=true
fi

# 违规计数
VIOLATIONS=0

# 输出函数
log_info() {
  echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
  echo -e "${GREEN}[PASS]${NC} $1"
}

log_warning() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[FAIL]${NC} $1"
  ((VIOLATIONS++))
}

# 分隔线
separator() {
  echo "================================================================"
}

separator
log_info "开始架构合规性检查"
separator

# ==========================================
# 1. 重复工具类检查
# ==========================================
log_info "检查项 1/6: 重复工具类检查"

# 检查业务模块中是否存在自定义的 Helper/Utils/Util 类（排除 common 模块）
DUPLICATE_UTILS=$(find "$BACKEND_ROOT/ydsz-"* -type f -name "*Helper.java" -o -name "*Utils.java" -o -name "*Util.java" \
  | grep -v "ydsz-common" \
  | grep -v "ydsz-literule/ydsz-literule-core" \
  | grep -v "/target/" \
  | grep -v "/test/" || true)

if [[ -n "$DUPLICATE_UTILS" ]]; then
  log_error "发现业务模块中存在重复工具类："
  echo "$DUPLICATE_UTILS" | while read -r file; do
    echo "  - $file"
  done
  log_warning "建议：使用 ydsz-common-util 或 ydsz-common-core 中的通用工具类"
else
  log_success "未发现重复工具类"
fi

# ==========================================
# 2. 常量管理检查
# ==========================================
log_info "检查项 2/6: 常量管理检查"

# 检查业务模块中是否存在自定义常量类（排除 common 模块和特定业务常量）
DUPLICATE_CONSTANTS=$(find "$BACKEND_ROOT/ydsz-"* -type f -name "*Constants.java" -o -name "*Constant.java" \
  | grep -v "ydsz-common" \
  | grep -v "/target/" \
  | grep -v "/test/" \
  | grep -v "FlowConstants" \
  | grep -v "MessageConstants" \
  | grep -v "RuleConstants" || true)

if [[ -n "$DUPLICATE_CONSTANTS" ]]; then
  log_warning "发现业务模块中存在自定义常量类（建议迁移至 ydsz-common-core）："
  echo "$DUPLICATE_CONSTANTS" | while read -r file; do
    echo "  - $file"
  done
else
  log_success "未发现重复常量类"
fi

# ==========================================
# 3. NameAssembler 使用规范检查
# ==========================================
log_info "检查项 3/6: NameAssembler 使用规范检查"

# 检查是否直接调用 Feign Client 而不是使用 NameAssembler
DIRECT_FEIGN_CALLS=$(grep -r "UserInfoClient\|DeptClient\|RoleClient" \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-feign" \
  | grep -v "NameAssembler" \
  | grep -v "/target/" \
  | grep -v "/test/" || true)

if [[ -n "$DIRECT_FEIGN_CALLS" ]]; then
  log_warning "发现直接调用 Feign Client 的代码（建议使用 NameAssembler）："
  echo "$DIRECT_FEIGN_CALLS" | head -5 | while read -r line; do
    echo "  - $line"
  done
  if [[ $(echo "$DIRECT_FEIGN_CALLS" | wc -l) -gt 5 ]]; then
    echo "  ... 还有更多"
  fi
else
  log_success "未发现直接调用 Feign Client 的代码"
fi

# ==========================================
# 4. 事件驱动架构规范检查
# ==========================================
log_info "检查项 4/6: 事件驱动架构规范检查"

# 检查是否使用 OutboxService 发布事件
OUTBOX_USAGE=$(grep -r "OutboxService" \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-event" \
  | grep -v "/target/" \
  | grep -v "/test/" | wc -l)

if [[ $OUTBOX_USAGE -gt 0 ]]; then
  log_success "发现 $OUTBOX_USAGE 处使用 OutboxService 发布事件"
else
  log_warning "未发现使用 OutboxService 发布事件的代码（建议参考事件驱动架构规范）"
fi

# 检查是否直接使用 ApplicationEventPublisher 发布跨服务事件
DIRECT_EVENT_PUBLISH=$(grep -r "ApplicationEventPublisher" \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-event" \
  | grep -v "/target/" \
  | grep -v "/test/" \
  | grep -v "OperationLogAspect" \
  | grep -v "AsyncConfig" || true)

if [[ -n "$DIRECT_EVENT_PUBLISH" ]]; then
  log_warning "发现直接使用 ApplicationEventPublisher 的代码（跨服务事件建议使用 OutboxService）："
  echo "$DIRECT_EVENT_PUBLISH" | head -5 | while read -r line; do
    echo "  - $line"
  done
else
  log_success "未发现直接发布跨服务事件的代码"
fi

# ==========================================
# 5. Excel 导出标准化检查
# ==========================================
log_info "检查项 5/6: Excel 导出标准化检查"

# 检查是否直接使用 EasyExcel 而不是使用 ExcelFacade
DIRECT_EASYEXCEL=$(grep -r "EasyExcel.write\|new ExcelWriter" \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-excel" \
  | grep -v "/target/" \
  | grep -v "/test/" || true)

if [[ -n "$DIRECT_EASYEXCEL" ]]; then
  log_error "发现直接使用 EasyExcel 的代码（建议使用 ydsz-common-excel 的 ExcelFacade）："
  echo "$DIRECT_EASYEXCEL" | while read -r line; do
    echo "  - $line"
  done
else
  log_success "未发现直接使用 EasyExcel 的代码"
fi

# ==========================================
# 6. 前端通用组件使用检查
# ==========================================
log_info "检查项 6/6: 前端通用组件使用检查"

# 检查是否使用 UserPicker 组件
USER_PICKER_EXISTS=$(find "$FRONTEND_ROOT/src/components/common" -name "UserPicker.vue" | wc -l)
if [[ $USER_PICKER_EXISTS -gt 0 ]]; then
  log_success "UserPicker 通用组件已存在"
else
  log_error "UserPicker 通用组件不存在"
fi

# 检查是否使用 DeptSelector 组件
DEPT_SELECTOR_EXISTS=$(find "$FRONTEND_ROOT/src/components/common" -name "DeptSelector.vue" | wc -l)
if [[ $DEPT_SELECTOR_EXISTS -gt 0 ]]; then
  log_success "DeptSelector 通用组件已存在"
else
  log_error "DeptSelector 通用组件不存在"
fi

# 检查业务页面是否重复实现用户选择器
DUPLICATE_USER_SELECTORS=$(grep -r "选择用户\|选择审批人\|UserSelector" \
  "$FRONTEND_ROOT/src/views" \
  --include="*.vue" \
  | grep -v "UserPicker" \
  | grep -v "DeptSelector" || true)

if [[ -n "$DUPLICATE_USER_SELECTORS" ]]; then
  log_warning "发现业务页面中可能存在重复实现用户选择器的代码（建议使用 UserPicker 组件）："
  echo "$DUPLICATE_USER_SELECTORS" | head -5 | while read -r line; do
    echo "  - $line"
  done
else
  log_success "未发现重复实现用户选择器的代码"
fi

# ==========================================
# 检查结果汇总
# ==========================================
separator
if [[ $VIOLATIONS -eq 0 ]]; then
  log_success "架构合规性检查通过！未发现严重违规项。"
  if [[ $STRICT_MODE == true ]]; then
    exit 0
  fi
else
  log_error "架构合规性检查发现 $VIOLATIONS 项违规！"
  if [[ $STRICT_MODE == true ]]; then
    log_error "严格模式下，检查失败，请修复上述问题后重新提交。"
    exit 1
  else
    log_warning "建议修复上述问题以提升代码质量。"
  fi
fi
separator
