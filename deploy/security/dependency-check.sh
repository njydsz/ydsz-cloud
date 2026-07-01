#!/bin/bash
# =====================================================================
#  PMIS 依赖安全扫描（OWASP dependency-check）
# ---------------------------------------------------------------------
#  工具：OWASP Dependency-Check 8.x
#  范围：14 个后端微服务（Java）+ 1 个前端（Node.js）
#  阈值：CVSS >= 7.0 失败
#  输出：HTML + JSON
# =====================================================================

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}/../../ydsz-pmis-backend"
FRONTEND_DIR="${SCRIPT_DIR}/../../ydsz-pmis-frontend"
REPORT_DIR="${SCRIPT_DIR}/reports/dependency-check-$(date +%Y%m%d-%H%M%S)"

mkdir -p "${REPORT_DIR}"

DC_TOOL="${DC_TOOL:-dependency-check.sh}"
if ! command -v "${DC_TOOL}" &> /dev/null; then
    DC_TOOL="${HOME}/dependency-check/bin/dependency-check.sh"
fi

echo "=========================================="
echo "  PMIS 依赖安全扫描"
echo "  报告: ${REPORT_DIR}"
echo "=========================================="

# 1. 后端 Java 依赖扫描
echo "[1/3] Java 依赖扫描..."
"${DC_TOOL}" \
    --project "pmis-backend" \
    --scan "${BACKEND_DIR}" \
    --format HTML \
    --format JSON \
    --out "${REPORT_DIR}" \
    --failBuildOnCVSS 7 \
    --suppression "${SCRIPT_DIR}/suppressions.xml" \
    --nvdApiKey "${NVD_API_KEY:-}" \
    2>&1 | tee "${REPORT_DIR}/backend.log"

BACKEND_EXIT=$?

# 2. 前端 Node.js 依赖扫描
echo "[2/3] Node.js 依赖扫描（npm audit）..."
cd "${FRONTEND_DIR}"
npm audit --audit-level=high --json > "${REPORT_DIR}/frontend-audit.json" 2>&1
FRONTEND_EXIT=$?

# 3. 生成汇总报告
echo "[3/3] 生成汇总报告..."
{
    echo "=== 依赖安全扫描报告 ==="
    echo "扫描时间: $(date)"
    echo ""
    echo "后端 Java (CVSS >= 7.0 阈值):"
    grep -oP '"cvssV3":\{"baseScore":\K[0-9.]+' "${REPORT_DIR}/dependency-check-report.json" 2>/dev/null | sort -rn | uniq -c | head -20
    echo ""
    echo "前端 npm audit:"
    node -e "
        const data = require('${REPORT_DIR}/frontend-audit.json');
        const metadata = data.metadata?.vulnerabilities || {};
        console.log('  high:    ', metadata.high || 0);
        console.log('  critical:', metadata.critical || 0);
        console.log('  moderate:', metadata.moderate || 0);
    " 2>/dev/null
} > "${REPORT_DIR}/summary.txt"

cat "${REPORT_DIR}/summary.txt"

# 退出码
if [ $BACKEND_EXIT -ne 0 ] || [ $FRONTEND_EXIT -ne 0 ]; then
    echo ""
    echo -e "\033[0;31m❌ 发现高危依赖，请升级后重试\033[0m"
    exit 1
fi

echo -e "\033[0;32m✅ 无高危依赖\033[0m"
exit 0
