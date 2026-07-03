#!/bin/bash
# =====================================================================
#  PMIS 微服务统一启动入口（Docker 容器内）
#  ---------------------------------------------------------------------
#  职责:
#    1) 合并 JAVA_OPTS + JVM_OPTS, 并透传所有业务环境变量
#    2) 启动时打印服务名 / JVM 关键参数 / 端口, 便于容器日志排查
#    3) 支持外部 /opt/pmis/conf/application.yml 覆盖 jar 内置配置
#    4) 启动超过 60s 仍在运行则输出慢启动诊断, 提示调优
#
#  挂载示例 (覆盖配置):
#    docker run -v /host/conf:/opt/pmis/conf pmis-iam:1.0.0
#
#  注意:
#    - 容器 stop 时 tini (PID 1) 负责转发 SIGTERM 给 java 进程
#    - 启动命令在后台执行, 主进程通过 wait 阻塞, 保证容器不退出
# =====================================================================
set -e

# ---------- 应用 jar 与基础环境变量 ----------
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

# ---------- 1. 加载外部配置（挂载 /opt/pmis/conf 时生效） ----------
# Spring Boot 配置加载顺序: classpath:/ 优先, file:/opt/pmis/conf/ 覆盖
# 使用 optional: 前缀避免目录不存在时启动失败
if [ -d "/opt/pmis/conf" ]; then
    echo "[ENTRY] external config mounted at /opt/pmis/conf"
    EXTRA_ARGS="--spring.config.additional-location=optional:classpath:/,optional:file:/opt/pmis/conf/"
else
    EXTRA_ARGS=""
fi

# ---------- 2. 启动 JVM 进程 ----------
# 后台运行 + wait 是为了让监控子 shell 有机会执行, 同时容器不退出
# 注意: 不使用 exec, 以便 tini (PID 1) 能正确向 java 进程转发信号
START_TS=$(date +%s)
java $JAVA_OPTS $JVM_OPTS \
    -jar "${APP_JAR}" \
    --server.port="${SERVER_PORT}" \
    --spring.application.name="${APP_NAME}" \
    ${EXTRA_ARGS} &
PID=$!

# ---------- 3. 慢启动诊断（> 60s 仍在运行） ----------
# 应用可能因 Bean 初始化慢、Flyway 脚本多、远程配置拉取等耗时
# kill -0 用于探测进程是否仍存活（未崩溃）
(
    sleep 60
    if kill -0 $PID 2>/dev/null; then
        echo "[ENTRY] WARN: Application still starting after 60s, consider tuning heap"
    fi
) &

# 阻塞主进程, 容器保持运行直到 java 进程退出
wait $PID
