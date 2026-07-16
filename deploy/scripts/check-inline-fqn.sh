#!/bin/bash
# =============================================================================
# check-inline-fqn.sh — 检测 Java 源文件中的行内全限定类名（FQN）违规 + @SuppressWarnings 违规
#
# 公司代码规范（强制）：
#   1. 禁止行内 FQN 用法，必须使用标准 import 语句后引用简单类名。
#   2. 禁止使用 @SuppressWarnings 注解，所有警告必须从根源修复。
# 规则文件：.trae/rules/no-inline-fqn.md
# 详细文档：deploy/docs/architecture/coding-standards.md
#
# 用法：
#   ./check-inline-fqn.sh                          # 检测 ydsz-backend 目录
#   ./check-inline-fqn.sh <src-dir>                # 检测指定目录
#   ./check-inline-fqn.sh <src-dir> --strict       # 严格模式：有违规即 exit 1
#
# 修复的 Bug（v2.0）：
#   1. 子 shell 变量丢失：改用 tmpfile 方案，确保 VIOLATION_COUNT 正确传递
#   2. 错误跳过 @throws：移除 @throws 跳过逻辑，@throws FQN 是违规（与规则文件一致）
#   3. 无法检测注解 FQN：增加 @FQN 模式检测
#   4. 字符串检测逻辑漏洞：改用更精确的引号内 FQN 排除
#   5. 未检测 catch/instanceof：增加对应模式
#
# v3.0 新增：
#   - 增加 @SuppressWarnings 注解检测
#
# 例外（不报违规）：
#   - import 语句
#   - package 语句
#   - 字符串字面量中的 FQN（双引号内的 FQN）
#   - Javadoc {@link FQN} 引用（仅 {@link} 标签）
#   - @ConditionalOnClass(name = "FQN") 注解的字符串参数
#   - 带 // FQN-OK 注释的行（同名类冲突等合法场景）
#
# 退出码：
#   0 — 无违规（或非严格模式）
#   1 — 有违规（严格模式）
# =============================================================================
set -euo pipefail

SRC_DIR="${1:-ydsz-backend}"
STRICT="${2:-}"

TMPFILE=$(mktemp)
SUPPRESSFILE=$(mktemp)
trap 'rm -f "$TMPFILE" "$SUPPRESSFILE"' EXIT

echo "🔍 扫描行内 FQN + @SuppressWarnings 违规: $SRC_DIR"
echo "-----------------------------------"

# FQN 正则模式：匹配 com.xxx.YyyClass / org.xxx.YyyClass / java.xxx.YyyClass / javax.xxx.YyyClass / jakarta.xxx.YyyClass
# 要求至少 2 段包名 + 1 段大写开头的类名
FQN_PATTERN='(com|org|java|javax|jakarta|net|io)\.[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*\.[A-Z][a-zA-Z0-9_]*'

# 查找所有 .java 文件，逐行检测
find "$SRC_DIR" -name '*.java' -type f | while read -r file; do
    # 使用 grep 检测包含 FQN 模式的行号
    grep -nE "$FQN_PATTERN" "$file" 2>/dev/null | while IFS=':' read -r line_num line_content; do
        # 跳过 import 行
        trimmed=$(echo "$line_content" | sed 's/^[[:space:]]*//')
        case "$trimmed" in
            import\ *) continue ;;
            package\ *) continue ;;
        esac

        # 跳过带 FQN-OK 注释的行（合法的同类名冲突）
        if echo "$line_content" | grep -q 'FQN-OK'; then
            continue
        fi

        # 跳过 Javadoc {@link FQN} 引用（仅 {@link} 标签可保留 FQN）
        # 检测：如果行中的 FQN 出现在 {@link ...} 内，跳过
        if echo "$line_content" | grep -qE '\{@link\s.*'"$FQN_PATTERN" ; then
            # 但如果同一行还有非 {@link} 中的 FQN，仍需报告
            # 简化处理：如果整行只有 {@link} 中的 FQN，跳过
            non_link_fqn=$(echo "$line_content" | sed 's/{@link[^}]*}//g')
            if ! echo "$non_link_fqn" | grep -qE "$FQN_PATTERN"; then
                continue
            fi
        fi

        # 跳过 @ConditionalOnClass(name = "FQN") 的字符串参数
        if echo "$line_content" | grep -qE '@ConditionalOnClass\(name\s*=\s*"' ; then
            continue
        fi

        # 跳过字符串字面量中的 FQN
        # 策略：提取双引号外的代码部分，检测其中是否仍有 FQN
        # 方法：将双引号字符串替换为空，然后检测剩余部分
        code_only=$(echo "$line_content" | sed 's/"[^"]*"//g')
        if ! echo "$code_only" | grep -qE "$FQN_PATTERN"; then
            continue
        fi

        # 跳过纯注释行（以 * 或 // 开头但不是 Javadoc 标签行）
        # Javadoc 标签行（@throws/@see/@param/@return）中的 FQN 是违规，不跳过
        # 但纯说明性注释行（如 * 这是说明文字）中的 FQN 可跳过
        if echo "$trimmed" | grep -qE '^(\*|//)' ; then
            # 如果是 Javadoc 标签行（@throws/@see/@param/@return 后跟 FQN），报告违规
            if echo "$trimmed" | grep -qE '^(\*\s*)?@(throws|see|param|return)\s+'"$(echo "$FQN_PATTERN" | sed 's/\\//g')" ; then
                # 这是违规：Javadoc 标签中的 FQN
                echo "$file:$line_num" >> "$TMPFILE"
                echo "   $line_content" >> "$TMPFILE"
                continue
            fi
            # 纯注释说明行，跳过
            continue
        fi

        # 报告违规
        echo "$file:$line_num" >> "$TMPFILE"
        echo "   $line_content" >> "$TMPFILE"
    done
done

# ========== @SuppressWarnings 检测 ==========
echo ""
echo "🔍 扫描 @SuppressWarnings 违规: $SRC_DIR"
echo "-----------------------------------"

find "$SRC_DIR" -name '*.java' -type f | while read -r file; do
    grep -n '@SuppressWarnings' "$file" 2>/dev/null | while IFS=':' read -r line_num line_content; do
        echo "$file:$line_num" >> "$SUPPRESSFILE"
        echo "   $line_content" >> "$SUPPRESSFILE"
    done
done

# 读取 suppressfile 统计违规数
SUPPRESS_COUNT=0
if [ -f "$SUPPRESSFILE" ]; then
    SUPPRESS_COUNT=$(wc -l < "$SUPPRESSFILE")
    SUPPRESS_COUNT=$((SUPPRESS_COUNT / 2))
fi

# 输出 @SuppressWarnings 违规详情
if [ "$SUPPRESS_COUNT" -gt 0 ]; then
    cat "$SUPPRESSFILE"
fi

# 读取 tmpfile 统计违规数
VIOLATION_COUNT=0
if [ -f "$TMPFILE" ]; then
    # 每两条行为一条违规（文件:行号 + 内容）
    VIOLATION_COUNT=$(wc -l < "$TMPFILE")
    VIOLATION_COUNT=$((VIOLATION_COUNT / 2))
fi

# 输出 FQN 违规详情
if [ "$VIOLATION_COUNT" -gt 0 ]; then
    cat "$TMPFILE"
fi

TOTAL_COUNT=$((VIOLATION_COUNT + SUPPRESS_COUNT))

echo "-----------------------------------"
if [ "$TOTAL_COUNT" -eq 0 ]; then
    echo "✅ 检测完成，无行内 FQN 违规，无 @SuppressWarnings 违规。"
else
    echo "⚠️  检测完成，发现 $VIOLATION_COUNT 处行内 FQN 违规，$SUPPRESS_COUNT 处 @SuppressWarnings 违规（共 $TOTAL_COUNT 处）。"
    if [ "$STRICT" = "--strict" ]; then
        echo "❌ 严格模式：CI 阻断。请修复上述违规后重新提交。"
        exit 1
    fi
fi
