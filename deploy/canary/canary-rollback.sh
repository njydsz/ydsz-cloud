#!/usr/bin/env bash
# =============================================================================
#  PMIS 金丝雀发布 - 紧急回滚脚本 (批次 20 P3-4)
#  --------------------------------------------------------------------------
#  用途:
#    在金丝雀发布过程中, 一键把指定服务的流量切回 100% stable 旧版本,
#    常用于错误率/延迟突破阈值或健康检查失败等应急场景.
#
#  行为:
#    - 调用 canary-shift.sh 将权重置为 0, 100% 流量回退到 stable
#    - Gateway 通过 Nacos 配置热更新, 通常 5s 内生效
#    - 输出 oncall 通知提示, 提醒 30min 内完成根因分析
#
#  用法:
#    ./canary-rollback.sh <service>
#    例: ./canary-rollback.sh project
#
#  推荐替代 (批次 23+):
#    kubectl argo rollouts abort pmis-<service> -n pmis-prod
#    详见 deploy/argo-rollouts/ops-commands.md §3.4
# =============================================================================
set -euo pipefail

# 解析脚本所在目录, 用于定位同目录的 canary-shift.sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 第一个入参: 服务名 (例: project / finance / agent)
SERVICE="${1:-}"

# 参数校验: 必须传入 service 名称
if [[ -z "$SERVICE" ]]; then
  echo "Usage: $0 <service>" >&2
  echo "  例: $0 project    # 回滚 pmis-project 流量回 100% stable" >&2
  exit 1
fi

echo "===> 紧急回滚: ${SERVICE} -> 100% stable"
# 复用 canary-shift.sh 权重为 0 的能力, 保持单一逻辑入口
bash "${SCRIPT_DIR}/canary-shift.sh" "${SERVICE}" 0
echo "===> 回滚完成, 已通知 oncall, 请在 30min 内做根因分析"
