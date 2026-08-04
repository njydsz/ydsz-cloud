#!/usr/bin/env bash
# =============================================================================
#  YDSZ 压测执行脚本
# -----------------------------------------------------------------------------
#  用法：
#    ./run.sh gateway          # 网关基准压测
#    ./run.sh project          # 项目 CRUD 场景
#    ./run.sh workflow         # 工作流审批场景
#    ./run.sh all              # 全部场景
#
#  环境变量：
#    BASE_URL   网关地址（默认 http://localhost:9000）
#    TOKEN      测试用户 Token（业务场景需要）
#    K6_IMAGE   k6 镜像（默认 grafana/k6）
# =============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:9000}"
TOKEN="${TOKEN:-}"
K6_IMAGE="${K6_IMAGE:-grafana/k6}"
SCENARIO="${1:-gateway}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIOS_DIR="${SCRIPT_DIR}/scenarios"

mkdir -p "${SCRIPT_DIR}/reports"
REPORT_FILE="${SCRIPT_DIR}/reports/$(date +%Y%m%d-%H%M%S)-${SCENARIO}.json"

echo "=============================================="
echo "  YDSZ 压测启动: ${SCENARIO}"
echo "  BASE_URL: ${BASE_URL}"
echo "  报告输出: ${REPORT_FILE}"
echo "=============================================="

run_scenario() {
  local name="$1"
  local script="${SCENARIOS_DIR}/${name}.js"

  if [[ ! -f "${script}" ]]; then
    echo "❌ 场景脚本不存在: ${script}" >&2
    exit 1
  fi

  echo "▶ 执行场景 ${name} ..."
  docker run --rm \
    -e BASE_URL="${BASE_URL}" \
    -e TOKEN="${TOKEN}" \
    -v "${SCENARIOS_DIR}:/scenarios:ro" \
    -v "${SCRIPT_DIR}/reports:/reports" \
    "${K6_IMAGE}" run \
      --summary-trend-stats="avg,min,med,p(90),p(95),p(99),max" \
      --out json="/reports/${name}-$(date +%H%M%S).json" \
      "/scenarios/${name}.js"
}

case "${SCENARIO}" in
  gateway)  run_scenario gateway-benchmark ;;
  project)  run_scenario project-crud ;;
  workflow) run_scenario workflow-approval ;;
  agent)    run_scenario agent-chat ;;
  all)
    run_scenario gateway-benchmark
    run_scenario project-crud
    run_scenario workflow-approval
    ;;
  *) echo "未知场景: ${SCENARIO}（可选: gateway/project/workflow/agent/all）" >&2; exit 1 ;;
esac

echo "✅ 压测完成，报告目录: ${SCRIPT_DIR}/reports/"
