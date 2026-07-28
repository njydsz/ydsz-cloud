#!/usr/bin/env bash
# =============================================================================
# YDSZ 行内 FQN 违规检测脚本 v3.0
# -----------------------------------------------------------------------------
# 作用:  扫描 Java 源代码中的行内全限定类名（FQN）和 @SuppressWarnings 违规
# 规则:  .trae/rules/no-inline-fqn.md (alwaysApply: true)
# 用法:  bash deploy/scripts/check-inline-fqn.sh ydsz-backend [--strict]
#        --strict: 有违规即 exit 1（CI 阻断模式）
# =============================================================================
set -euo pipefail

TARGET_DIR="${1:-ydsz-backend}"
STRICT="${2:-}"
VIOLATIONS=0

if [ "${STRICT}" = "--strict" ]; then
  echo "=== FQN + @SuppressWarnings 违规检测（严格模式）==="
else
  echo "=== FQN + @SuppressWarnings 违规检测 ==="
fi

echo "Target: ${TARGET_DIR}"
echo ""

# 检测行内 FQN（排除字符串字面量和 Javadoc {@link} ）
# 匹配模式: com.njydsz.xxx.Yyy 出现在代码行中（非字符串、非 import、非 package）
find_fqn_violations() {
  local files
  files=$(find "${TARGET_DIR}" -name "*.java" -type f 2>/dev/null || true)

  if [ -z "${files}" ]; then
    echo "未找到 Java 文件"
    return 0
  fi

  echo "${files}" | while IFS= read -r file; do
    # 跳过 import 和 package 语句行，跳过字符串中的 FQN
    grep -nE '(?<!import .*)(?<!package .*)(?<!".*)(?<!\{@link )com\.njydsz\.[a-z]+\.[A-Za-z]+\.[A-Z][A-Za-z]+' "${file}" 2>/dev/null || true
  done
}

# 检测 @SuppressWarnings
find_suppress_violations() {
  find "${TARGET_DIR}" -name "*.java" -type f -exec grep -l '@SuppressWarnings' {} \; 2>/dev/null || true
}

# FQN 检测
echo "--- FQN 违规检测 ---"
FQN_COUNT=0
# 使用简化的 grep 模式（兼容性更好）
while IFS= read -r file; do
  if [ -f "${file}" ]; then
    MATCHES=$(grep -n 'com\.njydsz\.[a-z]\+\.[a-z]\+\.[A-Z]' "${file}" 2>/dev/null | \
      grep -v '^\s*import ' | \
      grep -v '^\s*package ' | \
      grep -v '{@link ' | \
      grep -v '"' || true)
    if [ -n "${MATCHES}" ]; then
      echo "${MATCHES}" | while IFS= read -r line; do
        echo "  FQN: ${file}:${line}"
      done
      FQN_COUNT=$((FQN_COUNT + 1))
    fi
  fi
done < <(find "${TARGET_DIR}" -name "*.java" -type f 2>/dev/null)

if [ "${FQN_COUNT}" -eq 0 ]; then
  echo "✅ 未检测到 FQN 违规"
else
  echo "❌ 检测到 ${FQN_COUNT} 个文件存在 FQN 违规"
  VIOLATIONS=$((VIOLATIONS + FQN_COUNT))
fi

echo ""

# @SuppressWarnings 检测
echo "--- @SuppressWarnings 违规检测 ---"
SUPPRESS_FILES=$(find_suppress_violations)
SUPPRESS_COUNT=$(echo "${SUPPRESS_FILES}" | grep -c '.' 2>/dev/null || echo "0")

if [ "${SUPPRESS_COUNT}" -eq 0 ]; then
  echo "✅ 未检测到 @SuppressWarnings 违规"
else
  echo "❌ 检测到 ${SUPPRESS_COUNT} 个文件存在 @SuppressWarnings"
  echo "${SUPPRESS_FILES}" | while IFS= read -r f; do
    echo "  SUPPRESS: ${f}"
  done
  VIOLATIONS=$((VIOLATIONS + SUPPRESS_COUNT))
fi

echo ""
echo "=== 检测完成: ${VIOLATIONS} 个违规 ==="

if [ "${STRICT}" = "--strict" ] && [ "${VIOLATIONS}" -gt 0 ]; then
  exit 1
fi
