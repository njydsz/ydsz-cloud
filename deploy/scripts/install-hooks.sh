#!/bin/bash
# =============================================================================
# install-hooks.sh — 安装 Git Hooks
#
# 用法：bash deploy/scripts/install-hooks.sh
#
# 将 pre-commit hook 安装到 .git/hooks/ 目录。
# =============================================================================
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HOOKS_DIR="$REPO_ROOT/.git/hooks"

echo "📦 安装 Git Hooks..."

# 创建 hooks 目录（如果不存在）
mkdir -p "$HOOKS_DIR"

# 安装 pre-commit hook
HOOK_SRC="$REPO_ROOT/deploy/scripts/pre-commit"
HOOK_DST="$HOOKS_DIR/pre-commit"

if [ -f "$HOOK_SRC" ]; then
    cp "$HOOK_SRC" "$HOOK_DST"
    chmod +x "$HOOK_DST"
    echo "✅ pre-commit hook 已安装到 $HOOK_DST"
else
    echo "❌ pre-commit 脚本不存在: $HOOK_SRC"
    exit 1
fi

# 验证
if [ -x "$HOOK_DST" ]; then
    echo "✅ Git Hooks 安装完成。"
    echo "   提示：紧急情况下可使用 git commit --no-verify 跳过检查。"
else
    echo "❌ pre-commit hook 安装失败（权限问题）。"
    exit 1
fi
