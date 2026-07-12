#!/bin/bash
# =============================================================================
# check-inline-fqn.sh — 检测 Java 源文件中的行内全限定类名（FQN）违规
#
# 公司代码规范：禁止行内 FQN 用法，必须使用标准 import 语句后引用简单类名。
# 规则文件：.trae/rules/no-inline-fqn.md
# 详细文档：deploy/docs/architecture/coding-standards.md
#
# 用法：
#   ./check-inline-fqn.sh                    # 检测 ydsz-pmis-backend 目录
#   ./check-inline-fqn.sh <src-dir>          # 检测指定目录
#   ./check-inline-fqn.sh <src-dir> --strict # 严格模式：有违规即 exit 1
#
# 例外（不报违规）：
#   - import 语句
#   - package 语句
#   - 注释行（// 或 *）
#   - 字符串字面量中的 FQN
#   - Javadoc {@link FQN} / @throws FQN
#   - 带 // FQN-OK 注释的行（同名类冲突等合法场景）
#
# 退出码：
#   0 — 无违规（或非严格模式）
#   1 — 有违规（严格模式）
# =============================================================================
set -euo pipefail

SRC_DIR="${1:-ydsz-pmis-backend}"
STRICT="${2:-}"

VIOLATION_COUNT=0

echo "🔍 扫描行内 FQN 违规: $SRC_DIR"
echo "-----------------------------------"

# 查找所有 .java 文件，逐行检测
find "$SRC_DIR" -name '*.java' -type f | while read -r file; do
    # 使用 grep 检测非 import/package/注释行中的 FQN 模式
    # 模式：com.xxx.yyy.ClassName 或 org.xxx.yyy.ClassName
    while IFS= read -r line_num; do
        [ -z "$line_num" ] && continue

        line_content=$(sed -n "${line_num}p" "$file")

        # 跳过注释行（以 // 或 * 或 /* 开头）
        trimmed=$(echo "$line_content" | sed 's/^[[:space:]]*//')
        case "$trimmed" in
            \/\/*|\*) continue ;;
        esac

        # 跳过带 FQN-OK 注释的行
        if echo "$line_content" | grep -q 'FQN-OK'; then
            continue
        fi

        # 跳过 Javadoc 引用 {@link ...} 或 @throws
        if echo "$line_content" | grep -qE '@\{?link|@throws|@code|\{@link'; then
            continue
        fi

        # 跳过字符串字面量中的 FQN（行内只包含 "..." 形式的 FQN）
        # 检测是否在代码部分（非字符串）包含 FQN
        # 简化检测：如果行中 com.xxx 出现在引号内，跳过
        if echo "$line_content" | grep -qE '"[^"]*com\.[a-z]' && ! echo "$line_content" | grep -qE 'com\.[a-z].*\.[A-Z][a-zA-Z]*[^"]*$'; then
            continue
        fi

        echo "❌ $file:$line_num"
        echo "   $line_content"
        VIOLATION_COUNT=$((VIOLATION_COUNT + 1))
    done < <(
        grep -nE '(com|org|java|javax|jakarta)\.[a-z]+\.[a-z]+(\.[a-z]+)*\.[A-Z][a-zA-Z0-9_]*' "$file" \
        | grep -v '^\s*[0-9]*:\s*import ' \
        | grep -v '^\s*[0-9]*:\s*package ' \
        | cut -d: -f1
    )
done

echo "-----------------------------------"
if [ "$VIOLATION_COUNT" -eq 0 ]; then
    echo "✅ 检测完成，无行内 FQN 违规。"
else
    echo "⚠️  检测完成，发现 $VIOLATION_COUNT 处行内 FQN 违规。"
    if [ "$STRICT" = "--strict" ]; then
        exit 1
    fi
fi
