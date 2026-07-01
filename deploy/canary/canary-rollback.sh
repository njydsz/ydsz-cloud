# =============================================================================
# PMIS 金丝雀回滚脚本 (批次 20 P3-4)
#
# 紧急回滚: 立即把流量切回 100% stable
# =============================================================================
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE="${1:-}"
if [[ -z "$SERVICE" ]]; then
  echo "Usage: $0 <service>" >&2
  exit 1
fi
echo "===> 紧急回滚: ${SERVICE} -> 100% stable"
bash "${SCRIPT_DIR}/canary-shift.sh" "${SERVICE}" 0
echo "===> 回滚完成, 已通知 oncall, 请在 30min 内做根因分析"
