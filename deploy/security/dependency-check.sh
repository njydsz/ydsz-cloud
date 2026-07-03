#!/usr/bin/env bash
# =====================================================================
#  PMIS 依赖安全扫描脚本（OWASP dependency-check + npm audit）
#  --------------------------------------------------------------------
#  工具：OWASP Dependency-Check 8.x + npm audit
#  范围：14 个后端微服务（Java）+ 1 个前端（Node.js）
#  阈值：CVSS >= 7.0 视为高危，扫描失败
#  输出：HTML + JSON 报告 + 汇总 summary.txt
#  --------------------------------------------------------------------
#  环境变量：
#    NVD_API_KEY        NVD API Key（提升 NVD 漏洞库下载速度，可选）
#    DC_TOOL            dependency-check.sh 路径，默认在 PATH 中查找
#  --------------------------------------------------------------------
#  使用：./dependency-check.sh
#  退出码：0 无高危 / 1 发现高危
# =====================================================================
set -uo pipefail

# ---------- 路径解析 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}/../../ydsz-pmis-backend"
FRONTEND_DIR="${SCRIPT_DIR}/../../ydsz-pmis-frontend"

# 报告目录按时间戳归档，避免覆盖历史报告
REPORT_DIR="${SCRIPT_DIR}/reports/dependency-check-$(date +%Y%m%d-%H%M%S)"
mkdir -p "${REPORT_DIR}"

# ---------- 工具定位 ----------
# 优先使用环境变量，其次 PATH，最后用户家目录默认安装路径
DC_TOOL="${DC_TOOL:-dependency-check.sh}"
if ! command -v "${DC_TOOL}" &> /dev/null; then
    DC_TOOL="${HOME}/dependency-check/bin/dependency-check.sh"
fi

echo "=========================================="
echo "  PMIS 依赖安全扫描"
echo "  报告: ${REPORT_DIR}"
echo "=========================================="

# =====================================================================
# 1. 后端 Java 依赖扫描
# --------------------------------------------------------------------
# --project          项目名（影响报告标题）
# --scan             扫描根目录（递归解析 pom.xml / build.gradle）
# --format           输出格式：HTML（人读）+ JSON（CI 解析）
# --out              报告输出目录
# --failBuildOnCVSS  阈值：CVSS ≥ 7.0 退出码非零
# --suppression      抑制清单（suppressions.xml）用于排除已知误报
# --nvdApiKey        NVD API Key（避免被限流）
# =====================================================================
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

BACKEND_EXIT=${PIPESTATUS[0]}

# =====================================================================
# 2. 前端 Node.js 依赖扫描（npm audit）
# --------------------------------------------------------------------
# --audit-level=high  仅报告 high 及以上
# --json              输出 JSON 便于后续 Node 脚本解析
# =====================================================================
echo "[2/3] Node.js 依赖扫描（npm audit）..."
cd "${FRONTEND_DIR}"
npm audit --audit-level=high --json > "${REPORT_DIR}/frontend-audit.json" 2>&1
FRONTEND_EXIT=$?
cd "${SCRIPT_DIR}"

# =====================================================================
# 3. 生成汇总报告
# --------------------------------------------------------------------
# 从 OWASP dep-check JSON 提取 CVSS 分数 Top20，
# 从 npm audit JSON 提取 high/critical/moderate 计数。
# =====================================================================
echo "[3/3] 生成汇总报告..."
{
    echo "=== 依赖安全扫描报告 ==="
    echo "扫描时间: $(date)"
    echo ""
    echo "后端 Java (CVSS >= 7.0 阈值):"
    # 解析 dep-check-report.json 的 cvssV3.baseScore 字段，统计 Top20
    grep -oP '"cvssV3":\{"baseScore":\K[0-9.]+' \
        "${REPORT_DIR}/dependency-check-report.json" 2>/dev/null \
        | sort -rn | uniq -c | head -20
    echo ""
    echo "前端 npm audit:"
    # Node 脚本提取 vulnerabilities 计数，失败时降级输出
    node -e "
        const data = require('${REPORT_DIR}/frontend-audit.json');
        const metadata = data.metadata?.vulnerabilities || {};
        console.log('  high:    ', metadata.high || 0);
        console.log('  critical:', metadata.critical || 0);
        console.log('  moderate:', metadata.moderate || 0);
    " 2>/dev/null
} > "${REPORT_DIR}/summary.txt"

cat "${REPORT_DIR}/summary.txt"

# =====================================================================
# 退出码判定
# --------------------------------------------------------------------
# 后端或前端任一存在高危漏洞即整体失败
# =====================================================================
if [ "${BACKEND_EXIT}" -ne 0 ] || [ "${FRONTEND_EXIT}" -ne 0 ]; then
    echo ""
    echo -e "\033[0;31m❌ 发现高危依赖，请升级后重试\033[0m"
    exit 1
fi

echo -e "\033[0;32m✅ 无高危依赖\033[0m"
exit 0
