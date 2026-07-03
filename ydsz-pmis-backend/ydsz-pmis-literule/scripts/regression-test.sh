#!/bin/bash
#
# 规则引擎回归测试 CI 集成脚本（Linux/macOS 版）
#
# 调用规则引擎批量测试用例执行 API，根据通过率判断是否阻断 CI 流水线。
# 当 allPassed=false 时以非零退出码退出，阻断流水线。
#
# 用法:
#   ./regression-test.sh [BASE_URL] [TIMEOUT_SEC]
#   ./regression-test.sh http://192.168.1.100:8080 300
#
# 退出码:
#   0 = 全部测试通过
#   1 = 存在失败用例
#   2 = API 调用失败
#

BASE_URL="${1:-http://localhost:8080}"
TIMEOUT_SEC="${2:-120}"
API_URL="${BASE_URL}/api/v1/rules/test-cases/batch-run"

echo "[CI] 开始规则引擎回归测试..."
echo "[CI] API: ${API_URL}"

RESPONSE=$(curl -s -X POST "${API_URL}" \
    -H "Content-Type: application/json" \
    -d '{"ids":[]}' \
    --max-time "${TIMEOUT_SEC}" 2>&1)

if [ $? -ne 0 ]; then
    echo "[CI] API 调用失败: ${RESPONSE}"
    exit 2
fi

# 解析 JSON 响应（使用 python3 或 jq）
if command -v jq &> /dev/null; then
    DATA=$(echo "${RESPONSE}" | jq '.data')
    TOTAL=$(echo "${DATA}" | jq '.total')
    PASSED=$(echo "${DATA}" | jq '.passed')
    FAILED=$(echo "${DATA}" | jq '.failed')
    PASS_RATE=$(echo "${DATA}" | jq -r '.passRate')
    ALL_PASSED=$(echo "${DATA}" | jq '.allPassed')
elif command -v python3 &> /dev/null; then
    DATA=$(echo "${RESPONSE}" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(json.dumps(d))")
    TOTAL=$(echo "${DATA}" | python3 -c "import sys,json; print(json.load(sys.stdin)['total'])")
    PASSED=$(echo "${DATA}" | python3 -c "import sys,json; print(json.load(sys.stdin)['passed'])")
    FAILED=$(echo "${DATA}" | python3 -c "import sys,json; print(json.load(sys.stdin)['failed'])")
    PASS_RATE=$(echo "${DATA}" | python3 -c "import sys,json; print(json.load(sys.stdin)['passRate'])")
    ALL_PASSED=$(echo "${DATA}" | python3 -c "import sys,json; print(json.load(sys.stdin)['allPassed'])")
else
    echo "[CI] 需要 jq 或 python3 来解析 JSON 响应"
    exit 2
fi

echo ""
echo "========== 回归测试报告 =========="
echo "  总用例数: ${TOTAL}"
echo "  通过: ${PASSED}"
echo "  失败: ${FAILED}"
echo "  通过率: ${PASS_RATE}"
echo "==================================="
echo ""

if [ "${ALL_PASSED}" = "true" ] || [ "${ALL_PASSED}" = "True" ]; then
    echo "[CI] 回归测试全部通过"
    exit 0
else
    echo "[CI] 回归测试未通过，阻断流水线 (passRate=${PASS_RATE})"
    exit 1
fi
