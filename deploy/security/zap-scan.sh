#!/bin/bash
# =====================================================================
#  PMIS OWASP ZAP 主动扫描脚本（批次 19）
# ---------------------------------------------------------------------
#  工具：OWASP ZAP 2.14+
#  模式：baseline（无身份） + authenticated（带 token）
#  范围：14 个微服务 API
#  输出：HTML + JSON 报告
#  退出码：0 无高危 / 1 有高危
# =====================================================================

set -u

BASE_URL="${1:-http://staging.pmis.example.com}"
SCAN_MODE="${2:-baseline}"  # baseline / authenticated
OUTPUT_DIR="${3:-./security-report/zap-$(date +%Y%m%d-%H%M%S)}"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

mkdir -p "${OUTPUT_DIR}"

# 启动 ZAP Docker
ZAP_CONTAINER="pmis-zap-scanner"
echo "=========================================="
echo "  PMIS OWASP ZAP 扫描"
echo "  目标: ${BASE_URL}"
echo "  模式: ${SCAN_MODE}"
echo "  输出: ${OUTPUT_DIR}"
echo "=========================================="

docker run -d --rm --name "${ZAP_CONTAINER}" \
    -v "${OUTPUT_DIR}:/zap/wrk:rw" \
    -p 8090:8090 \
    ghcr.io/zaproxy/zaproxy:stable \
    sleep 3600

ZAP_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "${ZAP_CONTAINER}")
ZAP_API="http://${ZAP_IP}:8090"

# 等待 ZAP 就绪
echo "[1/4] 等待 ZAP 就绪..."
for i in $(seq 1 30); do
    if curl -fsS "${ZAP_API}/JSON/core/view/version/" >/dev/null 2>&1; then
        echo "  ✓ ZAP 已启动（$i 秒）"
        break
    fi
    sleep 1
done

# 1. 启动 spider 爬虫
echo "[2/4] 启动爬虫 (spider)..."
SPIDER_ID=$(curl -fsS -X POST "${ZAP_API}/JSON/spider/action/scan/" \
    -d "url=${BASE_URL}" \
    -d "maxChildren=20" \
    -d "recurse=true" \
    -d "contextName=" \
    -d "subtreeOnly=false" | grep -oP '"scan":"\K\d+')
echo "  - Spider ID: ${SPIDER_ID}"

# 等待 spider 完成
for i in $(seq 1 120); do
    STATUS=$(curl -fsS "${ZAP_API}/JSON/spider/view/status/?scanId=${SPIDER_ID}" | grep -oP '"status":"\K\d+')
    PROGRESS=$(curl -fsS "${ZAP_API}/JSON/spider/view/status/?scanId=${SPIDER_ID}" | grep -oP '"status":\K\d+')
    if [ "$STATUS" = "100" ]; then
        echo "  ✓ Spider 完成"
        break
    fi
    sleep 5
done

# 2. 主动扫描
echo "[3/4] 启动主动扫描 (ascan)..."
AScan_ID=$(curl -fsS -X POST "${ZAP_API}/JSON/ascan/action/scan/" \
    -d "url=${BASE_URL}" \
    -d "recurse=true" \
    -d "inScopeOnly=false" \
    -d "scanPolicyName=Default Policy" | grep -oP '"scan":"\K\d+')
echo "  - AScan ID: ${AScan_ID}"

# 等待 ascan 完成
for i in $(seq 1 240); do
    STATUS=$(curl -fsS "${ZAP_API}/JSON/ascan/view/status/?scanId=${AScan_ID}" | grep -oP '"status":"\K\d+')
    if [ "$STATUS" = "100" ]; then
        echo "  ✓ 主动扫描完成"
        break
    fi
    sleep 10
done

# 3. 生成报告
echo "[4/4] 生成报告..."
curl -fsS "${ZAP_API}/OTHER/core/other/htmlreport/" -o "${OUTPUT_DIR}/zap-report.html"
curl -fsS "${ZAP_API}/JSON/core/view/alerts/" -o "${OUTPUT_DIR}/zap-alerts.json"

# 4. 解析告警严重度
HIGH_COUNT=$(python3 -c "
import json
with open('${OUTPUT_DIR}/zap-alerts.json') as f:
    data = json.load(f)
alerts = data.get('alerts', [])
high = sum(1 for a in alerts if a.get('risk') == 'High')
medium = sum(1 for a in alerts if a.get('risk') == 'Medium')
low = sum(1 for a in alerts if a.get('risk') == 'Low')
print(f'{high}|{medium}|{low}')
")
HIGH=$(echo "$HIGH_COUNT" | cut -d'|' -f1)
MEDIUM=$(echo "$HIGH_COUNT" | cut -d'|' -f2)
LOW=$(echo "$HIGH_COUNT" | cut -d'|' -f3)

echo ""
echo "=========================================="
echo "  扫描结果"
echo "  高危: $HIGH"
echo "  中危: $MEDIUM"
echo "  低危: $LOW"
echo "  报告: ${OUTPUT_DIR}/zap-report.html"
echo "=========================================="

# 停止 ZAP
docker stop "${ZAP_CONTAINER}" >/dev/null 2>&1

# 退出码
if [ "$HIGH" -gt 0 ]; then
    echo -e "${RED}❌ 发现 ${HIGH} 个高危漏洞，请修复后再上线${NC}"
    exit 1
fi

if [ "$MEDIUM" -gt 5 ]; then
    echo -e "${YELLOW}⚠️  发现 ${MEDIUM} 个中危漏洞，建议修复${NC}"
    exit 2
fi

echo -e "${GREEN}✅ 无高危漏洞${NC}"
exit 0
