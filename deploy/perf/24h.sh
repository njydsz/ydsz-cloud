#!/bin/bash
# =====================================================================
#  PMIS 24 小时 Soak 稳定性测试（批次 19）
# ---------------------------------------------------------------------
#  目标：验证系统连续 24h 在中等负载下无内存泄漏、无性能衰减
#  场景：
#    - 100 并发用户
#    - 持续 24 小时
#    - 70% 读 / 30% 写
#    - 涵盖核心 14 个微服务
#  输出：每小时采样一次 JVM/DB/Redis 指标
#  监控：Prometheus + Grafana 实时观察
# =====================================================================

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULT_DIR="${SCRIPT_DIR}/baseline/soak-$(date +%Y%m%d-%H%M%S)"
mkdir -p "${RESULT_DIR}"
LOG_FILE="${RESULT_DIR}/soak.log"
JTL_FILE="${RESULT_DIR}/soak.jtl"
DURATION_HOURS=24
DURATION_SECS=$((DURATION_HOURS * 3600))

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log() { echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"; }
log_info()  { log "${GREEN}INFO${NC}  $*"; }
log_warn()  { log "${YELLOW}WARN${NC}  $*"; }
log_error() { log "${RED}ERROR${NC} $*"; }

# ---- 主流程 ----
log_info "=========================================="
log_info "  PMIS 24h Soak 稳定性测试"
log_info "  持续: ${DURATION_HOURS}h (${DURATION_SECS}s)"
log_info "  目录: ${RESULT_DIR}"
log_info "=========================================="

# 1. 基线快照（测试前）
log_info "[1/4] 采集测试前基线..."
{
    echo "=== 系统资源（测试前）==="
    free -h
    echo "---"
    nproc
    echo "---"
    df -h /opt /var
    echo "---"
    uptime
} > "${RESULT_DIR}/baseline-before.txt"

# 2. 启动 JMeter 持续 24h
log_info "[2/4] 启动 JMeter 持续 ${DURATION_HOURS}h"
log_info "  - 100 并发用户（70% 读 + 30% 写）"
log_info "  - JTL 输出到 ${JTL_FILE}"

cd "${SCRIPT_DIR}/jmeter"
nohup jmeter -n -t 01-core-read.jmx \
    -JbaseUrl="${BASE_URL:-http://localhost}" \
    -JDURATION="${DURATION_SECS}" \
    -l "${JTL_FILE}" \
    >> "${LOG_FILE}" 2>&1 &

JMETER_PID=$!
log_info "  - JMeter PID: ${JMETER_PID}"

# 3. 每小时采样（系统指标 + 应用指标）
log_info "[3/4] 开始 ${DURATION_HOURS} 小时循环采样"
SAMPLE_COUNT=0
while [ $SAMPLE_COUNT -lt $DURATION_HOURS ]; do
    sleep 3600
    SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
    
    HOUR_DIR="${RESULT_DIR}/hour-$(printf '%02d' $SAMPLE_COUNT)"
    mkdir -p "${HOUR_DIR}"
    
    log_info "  - 采样 #${SAMPLE_COUNT}/${DURATION_HOURS}"
    
    # 系统资源
    {
        echo "=== CPU / 内存 / 磁盘 ==="
        ps aux | head -1
        ps aux | grep -E "(pmis|java)" | grep -v grep
        echo "---"
        free -h
        echo "---"
        df -h
    } > "${HOUR_DIR}/system.txt"
    
    # DB 连接数
    {
        echo "=== PostgreSQL 连接数 ==="
        PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -U pmis_app -d pmis -c "
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE state='active') AS active,
                   count(*) FILTER (WHERE state='idle') AS idle
            FROM pg_stat_activity
            WHERE application_name LIKE 'pmis%';"
    } > "${HOUR_DIR}/db.txt" 2>&1
    
    # Redis 内存
    {
        echo "=== Redis 内存 / 连接 ==="
        docker exec pmis-redis-1 redis-cli -a $REDIS_PASSWORD INFO memory | head -20
        echo "---"
        docker exec pmis-redis-1 redis-cli -a $REDIS_PASSWORD INFO clients | head -10
        echo "---"
        docker exec pmis-redis-1 redis-cli -a $REDIS_PASSWORD INFO stats | head -15
    } > "${HOUR_DIR}/redis.txt" 2>&1
    
    # JMeter 进度
    if kill -0 $JMETER_PID 2>/dev/null; then
        log_info "    ✓ JMeter 运行中"
    else
        log_error "    ✗ JMeter 已退出，请检查日志"
    fi
    
    # 内存增长趋势（对比基线）
    mem_now=$(free -m | awk '/^Mem:/ {print $3}')
    mem_base=$(awk '/^Mem:/ {print $3}' "${RESULT_DIR}/baseline-before.txt" | head -1)
    if [ -n "$mem_now" ] && [ -n "$mem_base" ]; then
        growth=$((mem_now - mem_base))
        growth_pct=$(echo "scale=2; $growth * 100 / $mem_base" | bc 2>/dev/null || echo "0")
        log_info "    内存增长: ${growth}MB (${growth_pct}%)"
        
        # 内存增长 > 20% 告警
        if [ "$(echo "$growth_pct > 20" | bc 2>/dev/null)" = "1" ]; then
            log_warn "    ⚠️  内存增长超过 20%，可能存在内存泄漏"
        fi
    fi
done

# 4. 停止 JMeter + 收尾
log_info "[4/4] 停止 JMeter 并收尾"
kill -TERM $JMETER_PID 2>/dev/null || true
sleep 10
kill -KILL $JMETER_PID 2>/dev/null || true

# 5. 生成 Soak 报告
log_info "生成 Soak 报告..."

JTL_FILE=$(ls ${RESULT_DIR}/soak.jtl 2>/dev/null | head -1)
if [ -f "$JTL_FILE" ]; then
    # 简单统计
    {
        echo "=== 24h Soak 测试报告 ==="
        echo "  开始: $(head -1 $JTL_FILE | awk '{print $1}')"
        echo "  结束: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "  总样本: $(wc -l < $JTL_FILE)"
        echo "  错误数: $(grep -c ',false,' $JTL_FILE || echo 0)"
        echo ""
        echo "  P50: $(awk -F',' 'NR>1 {print $2}' $JTL_FILE | sort -n | awk 'NR==int(NR/2){print; exit}')"
        echo "  P95: $(awk -F',' 'NR>1 {print $2}' $JTL_FILE | sort -n | awk 'NR==int(NR*0.95){print; exit}')"
        echo "  P99: $(awk -F',' 'NR>1 {print $2}' $JTL_FILE | sort -n | awk 'NR==int(NR*0.99){print; exit}')"
    } > "${RESULT_DIR}/soak-report.txt"
    
    cat "${RESULT_DIR}/soak-report.txt"
fi

log_info "=========================================="
log_info "  ✅ 24h Soak 完成"
log_info "  报告目录: ${RESULT_DIR}"
log_info "=========================================="
