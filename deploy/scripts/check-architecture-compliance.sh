#!/bin/bash
#
# 架构合规性检查脚本
# 用于自动化检查项目架构规范的执行情况
#
# 检查项：
# 1.  重复工具类检查（禁止业务模块自定义 Helper/Utils/Util）
# 2.  常量管理检查（禁止业务模块自定义常量类）
# 3.  NameAssembler 使用规范检查
# 4.  事件驱动架构规范检查
# 5.  Excel 导出标准化检查
# 6.  前端通用组件使用检查
# 7.  缓存名称一致性检查（禁止硬编码缓存名，必须使用 CacheConstants）
# 8.  Feign Client 熔断规范检查（禁止缺少 FallbackFactory）
# 9.  公共 JSON 工具复用检查（禁止业务模块直接 new ObjectMapper/Gson）
# 10. 业务模块自定义异常处理器检查（禁止业务模块自定义 @RestControllerAdvice）
# 11. 跨模块数据库访问检查（禁止业务模块 Mapper 引用其他模块的表）
# 12. 公共能力绕过检查（检测业务模块直接使用 RedisTemplate）
# 13. 跨模块 Mapper/Entity 直接注入检查（禁止业务模块 server 层 import 其他模块的 Mapper/Entity）
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
  VIOLATIONS=$((VIOLATIONS + 1))
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
log_info "检查项 1/13: 重复工具类检查"

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
log_info "检查项 2/13: 常量管理检查"

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
log_info "检查项 3/13: NameAssembler 使用规范检查"

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
log_info "检查项 4/13: 事件驱动架构规范检查"

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
log_info "检查项 5/13: Excel 导出标准化检查"

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
log_info "检查项 6/13: 前端通用组件使用检查"

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
# 7. 缓存名称一致性检查
# ==========================================
log_info "检查项 7/13: 缓存名称一致性检查"

# 检查 Java 代码中是否存在硬编码的 cacheNames 字符串（"xxx" 形式），
# 而非使用 CacheConstants 中定义的常量。排除 common-cache 自身和测试代码。
HARDCODED_CACHE_NAMES=$(grep -rnE '@Cacheable\(cacheNames\s*=\s*".*"' \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-cache" \
  | grep -v "/target/" \
  | grep -v "/test/" || true)

if [[ -z "$HARDCODED_CACHE_NAMES" ]]; then
  # 同时也检查 @CacheEvict
  HARDCODED_CACHE_NAMES=$(grep -rnE '@CacheEvict\(cacheNames\s*=\s*".*"' \
    "$BACKEND_ROOT/ydsz-"*/src/main/java \
    --include="*.java" \
    | grep -v "ydsz-common-cache" \
    | grep -v "/target/" \
    | grep -v "/test/" || true)
fi

if [[ -n "$HARDCODED_CACHE_NAMES" ]]; then
  log_error "发现硬编码缓存名称（应使用 CacheConstants 中定义的常量）："
  echo "$HARDCODED_CACHE_NAMES" | head -10 | while read -r line; do
    echo "  - $line"
  done
  log_warning "建议：将缓存名称定义到 ydsz-common-core/CacheConstants.java，代码中引用常量"
else
  log_success "未发现硬编码缓存名称"
fi

# ==========================================
# 8. Feign Client 熔断规范检查
# ==========================================
log_info "检查项 8/13: Feign Client 熔断规范检查"

# 检查 @FeignClient 注解是否缺少 fallbackFactory 参数
# 排除 common-feign 自身和测试代码
FEIGN_WITHOUT_FALLBACK=$(grep -rn '@FeignClient' \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-feign" \
  | grep -v "/target/" \
  | grep -v "/test/" \
  | grep -v "fallbackFactory" \
  | grep -v "fallback\s*=" || true)

if [[ -n "$FEIGN_WITHOUT_FALLBACK" ]]; then
  log_error "发现缺少 fallbackFactory 的 Feign Client（必须配对 FallbackFactory 避免级联故障）："
  echo "$FEIGN_WITHOUT_FALLBACK" | head -10 | while read -r line; do
    echo "  - $line"
  done
  log_warning "建议：为每个 @FeignClient 添加 fallbackFactory 参数，指向对应的 FallbackFactory 实现类"
else
  log_success "所有 Feign Client 均已配置 fallbackFactory"
fi

# ==========================================
# 9. 公共 JSON 工具复用检查（禁止业务模块直接 new ObjectMapper/Gson）
# ==========================================
log_info "检查项 9/13: 公共 JSON 工具复用检查"

DIRECT_JSON_INIT=$(grep -rn 'new ObjectMapper()\|new Gson()\|new FastJson' \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-json" \
  | grep -v "/target/" \
  | grep -v "/test/" || true)

if [[ -n "$DIRECT_JSON_INIT" ]]; then
  log_error "发现业务模块直接实例化 ObjectMapper/Gson/FastJson（应使用 YdszJson）："
  echo "$DIRECT_JSON_INIT" | while read -r line; do
    echo "  - $line"
  done
  log_warning "建议：使用 com.njydsz.common.json.YdszJson.toJson() / fromJson()"
else
  log_success "未发现直接实例化 JSON 工具的代码"
fi

# ==========================================
# 10. 业务模块自定义 @RestControllerAdvice 检查
# ==========================================
log_info "检查项 10/13: 业务模块自定义异常处理器检查"

CUSTOM_ADVICE=$(grep -rn '@RestControllerAdvice\|@ControllerAdvice' \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-web" \
  | grep -v "ydsz-common-exception" \
  | grep -v "ydsz-common-app" \
  | grep -v "ydsz-common-safe" \
  | grep -v "ydsz-common-jdbc" \
  | grep -v "/target/" \
  | grep -v "/test/" || true)

if [[ -n "$CUSTOM_ADVICE" ]]; then
  log_error "发现业务模块自定义 @RestControllerAdvice/@ControllerAdvice（应使用 common-web 的 GlobalResponseAdvice）："
  echo "$CUSTOM_ADVICE" | while read -r line; do
    echo "  - $line"
  done
  log_warning "建议：删除业务模块的自定义异常处理器，统一使用 ydsz-common-web 的 GlobalResponseAdvice"
else
  log_success "业务模块未自定义异常处理器"
fi

# ==========================================
# 11. 跨模块数据库访问检查（禁止业务模块 Mapper 引用其他模块的表）
# ==========================================
log_info "检查项 11/13: 跨模块数据库访问检查"

# 检查业务模块的 Mapper XML 或注解中是否引用了其他模块的表前缀
# 各模块合法表前缀映射
declare -A MODULE_TABLES
MODULE_TABLES["ydsz-workflow"]="ydsz_flow_"
MODULE_TABLES["ydsz-project"]="ydsz_execution_|ydsz_cost_|ydsz_profit_|ydsz_finance_|ydsz_evm_|ydsz_ops_|ydsz_satisfaction_|ydsz_billable_|ydsz_rule_execution_|ydsz_rule_decision_|ydsz_rule_canary_|ydsz_rule_scorecard_|ydsz_rule_decision_tree|ydsz_rule_script|ydsz_rule_ab_"
MODULE_TABLES["ydsz-userinfo"]="ydsz_user_|ydsz_role_|ydsz_menu_|ydsz_dept_|ydsz_post_|ydsz_job_level|ydsz_dict_"
MODULE_TABLES["ydsz-system"]="ydsz_operation_log|ydsz_login_audit|ydsz_data_export|ydsz_sensitive_op|ydsz_file|ydsz_config|ydsz_tenant|ydsz_report_sub|ydsz_export_record|ydsz_meta_schema|ydsz_dict_version"
MODULE_TABLES["ydsz-message"]="ydsz_msg_|ydsz_notify_|ydsz_template_|ydsz_channel_"
MODULE_TABLES["ydsz-cronjob"]="ydsz_job_|ydsz_job_log|ydsz_job_level|ydsz_job_level_rate"
MODULE_TABLES["ydsz-literule"]="ydsz_rule_def|ydsz_rule_version|ydsz_rule_template|ydsz_rule_test|ydsz_rule_var|ydsz_rule_chain|ydsz_rule_dep|ydsz_rule_pack|ydsz_rule_node|ydsz_rule_event|ydsz_rule_log"
MODULE_TABLES["ydsz-agent"]="ydsz_agent_"
MODULE_TABLES["ydsz-nextwiki"]="ydsz_wiki_"

# 简化检测：扫描各业务模块 Mapper 中的 @Select/@Update/@Insert/@Delete SQL 语句
# 如果发现引用了不属于本模块的表前缀，则报告违规
CROSS_MODULE_DB=""
for module in "${!MODULE_TABLES[@]}"; do
  module_path="$BACKEND_ROOT/$module"
  if [[ ! -d "$module_path" ]]; then
    continue
  fi
  allowed_prefixes="${MODULE_TABLES[$module]}"
  # 查找该模块中所有 Mapper SQL 语句中引用的表名
  MAPPER_SQLS=$(grep -rnE '@(Select|Update|Insert|Delete)\s*\(' \
    "$module_path"/src/main/java \
    --include="*.java" \
    | grep -v "/target/" \
    | grep -v "/test/" || true)
  if [[ -n "$MAPPER_SQLS" ]]; then
    # 检查是否引用了 ydsz_ 开头的表但不属于本模块
    for other_module in "${!MODULE_TABLES[@]}"; do
      if [[ "$other_module" == "$module" ]]; then
        continue
      fi
      other_prefixes="${MODULE_TABLES[$other_module]}"
      # 将允许的前缀转为 grep -E 模式
      allowed_pattern=$(echo "$other_prefixes" | tr '|' '\n' | head -1)
      # 简化：只检测明显跨模块引用（如 workflow 引用 ydsz_msg_ 表）
      while IFS= read -r other_prefix; do
        # 跳过自身模块允许的前缀
        if echo "$allowed_prefixes" | grep -q "$other_prefix"; then
          continue
        fi
        CROSS_REF=$(echo "$MAPPER_SQLS" | grep -i "from\s\+${other_prefix}\|into\s\+${other_prefix}\|update\s\+${other_prefix}\|join\s\+${other_prefix}" || true)
        if [[ -n "$CROSS_REF" ]]; then
          CROSS_MODULE_DB="${CROSS_MODULE_DB}\n${CROSS_REF}"
        fi
      done <<< "$(echo "$other_prefixes" | tr '|' '\n')"
    done
  fi
done

if [[ -n "$CROSS_MODULE_DB" ]]; then
  log_error "发现跨模块数据库表访问（应通过 Feign Client 调用对方服务 API）："
  echo -e "$CROSS_MODULE_DB" | head -10 | while read -r line; do
    [[ -n "$line" ]] && echo "  - $line"
  done
  log_warning "建议：跨模块数据访问应通过 @FeignClient 接口调用，禁止直连其他模块的数据库表"
else
  log_success "未发现跨模块数据库表访问"
fi

# ==========================================
# 12. 公共能力绕过检查（RedisTemplate/RedissonClient 直接注入业务模块）
# ==========================================
log_info "检查项 12/13: 公共能力绕过检查"

# 检查业务模块是否直接注入 RedisTemplate（应使用 RedisService 或 Cache 接口）
# 白名单：gateway 模块（网关层基础设施，合理使用）、literule（分布式数据结构，有特殊需求）
DIRECT_REDISTEMPLATE=$(grep -rn 'RedisTemplate\|StringRedisTemplate' \
  "$BACKEND_ROOT/ydsz-"*/src/main/java \
  --include="*.java" \
  | grep -v "ydsz-common-redis" \
  | grep -v "ydsz-common-cache" \
  | grep -v "ydsz-common-queue" \
  | grep -v "ydsz-common-socket" \
  | grep -v "ydsz-common-seata" \
  | grep -v "ydsz-common-search" \
  | grep -v "ydsz-common-safe" \
  | grep -v "ydsz-common-notify" \
  | grep -v "ydsz-common-auth" \
  | grep -v "ydsz-common-file" \
  | grep -v "ydsz-common-lock" \
  | grep -v "ydsz-gateway" \
  | grep -v "/target/" \
  | grep -v "/test/" \
  | grep -v "import " \
  | grep -v "\* " || true)

if [[ -n "$DIRECT_REDISTEMPLATE" ]]; then
  log_warning "发现业务模块直接使用 RedisTemplate/StringRedisTemplate（建议使用 RedisService 或 Cache 接口）："
  echo "$DIRECT_REDISTEMPLATE" | head -10 | while read -r line; do
    echo "  - $line"
  done
  log_warning "建议：使用 ydsz-common-redis 的 RedisService 或 ydsz-common-cache 的 Cache 接口"
else
  log_success "业务模块未直接使用 RedisTemplate"
fi

# ==========================================
# 13. 跨模块 Mapper/Entity 直接注入检查
# ==========================================
log_info "检查项 13/13: 跨模块 Mapper/Entity 直接注入检查"

# 检测业务模块 server 层是否直接 import 其他业务模块的 Mapper 或 Entity
# 这是跨模块直连数据库的最常见违规模式：
#   - import com.njydsz.{other}.infra.mapper.SomeMapper  → 直接注入对方 Mapper
#   - import com.njydsz.{other}.domain.entity.SomeEntity  → 直接引用对方实体（用于 LambdaQueryWrapper 等）
# 合法做法：通过 @FeignClient 调用对方服务 API，或通过 Outbox 事件驱动通信

BUSINESS_MODULES="workflow project userinfo system message cronjob literule agent nextwiki"
CROSS_MODULE_MAPPER=""

for module in $BUSINESS_MODULES; do
  module_server_path="$BACKEND_ROOT/ydsz-${module}/ydsz-${module}-server/src/main/java"
  if [[ ! -d "$module_server_path" ]]; then
    continue
  fi
  for other_module in $BUSINESS_MODULES; do
    # 跳过自身模块
    if [[ "$other_module" == "$module" ]]; then
      continue
    fi
    # 检测 import com.njydsz.{other_module}.infra.mapper.* 或 .domain.entity.*
    VIOLATIONS_FOUND=$(grep -rn \
      "import com\.njydsz\.${other_module}\.infra\.mapper\.\|import com\.njydsz\.${other_module}\.domain\.entity\." \
      "$module_server_path" \
      --include="*.java" 2>/dev/null || true)
    if [[ -n "$VIOLATIONS_FOUND" ]]; then
      CROSS_MODULE_MAPPER="${CROSS_MODULE_MAPPER}\n${VIOLATIONS_FOUND}"
    fi
  done
done

if [[ -n "$CROSS_MODULE_MAPPER" ]]; then
  log_error "发现跨模块 Mapper/Entity 直接注入（应通过 @FeignClient 调用对方服务 API）："
  echo -e "$CROSS_MODULE_MAPPER" | head -15 | while read -r line; do
    [[ -n "$line" ]] && echo "  - $line"
  done
  if [[ $(echo -e "$CROSS_MODULE_MAPPER" | grep -c .) -gt 15 ]]; then
    echo "  ... 还有更多"
  fi
  log_warning "建议：跨模块数据访问必须通过 @FeignClient 接口调用，禁止直连其他模块的 Mapper/Entity"
else
  log_success "未发现跨模块 Mapper/Entity 直接注入"
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
