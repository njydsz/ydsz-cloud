#!/bin/bash
# =====================================================================
#  PMIS 微服务统一启动入口（Docker 容器内）
# ---------------------------------------------------------------------
#  职责：
#  1) 合并 JAVA_OPTS + JVM_OPTS + 透传环境变量
#  2) 启动时打印服务名 / JVM 关键参数 / 端口
#  3) 支持外部 /opt/pmis/conf/application.yml 覆盖 jar 内配置
#  4) 输出慢启动诊断信息（> 60s 启动时降级告警）
# =====================================================================
set -e

APP_JAR="/opt/pmis/app.jar"
APP_NAME="${APP_NAME:-pmis-app}"
SERVER_PORT="${SERVER_PORT:-9000}"

echo "==========================================="
echo "  PMIS Service Starting: ${APP_NAME}"
echo "  Date:       $(date -Iseconds)"
echo "  Port:       ${SERVER_PORT}"
echo "  HeapDump:   ${JAVA_OPTS:-default}"
echo "  JAVA_HOME:  ${JAVA_HOME:-/opt/java/openjdk}"
echo "==========================================="

# 加载外部配置（如果挂载到 /opt/pmis/conf）
if [ -d "/opt/pmis/conf" ]; then
    echo "[ENTRY] external config mounted at /opt/pmis/conf"
    EXTRA_ARGS="--spring.config.additional-location=optional:classpath:/,optional:file:/opt/pmis/conf/"
else
    EXTRA_ARGS=""
fi

# 启动应用（exec 让 tini 正确接收信号）
START_TS=$(date +%s)
exec java $JAVA_OPTS $JVM_OPTS \
    -jar "${APP_JAR}" \
    --server.port="${SERVER_PORT}" \
    --spring.application.name="${APP_NAME}" \
    ${EXTRA_ARGS} &
PID=$!

# 后台监控：启动超过 60s 还在启动中则输出诊断
(
    sleep 60
    if kill -0 $PID 2>/dev/null; then
        echo "[ENTRY] WARN: Application still starting after 60s, consider tuning heap"
    fi
) &

wait $PID
