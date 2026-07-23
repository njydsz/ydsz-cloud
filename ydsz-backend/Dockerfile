# =============================================================================
#  YDSZ · 后端微服务统一 Dockerfile（多阶段构建）
# -----------------------------------------------------------------------------
#  适用模块: gateway / system / userinfo / project / workflow / agent / cronjob
#            finance / sales / message / nextwiki
#  构建示例:
#    docker build -t ydsz/gateway:v1.3.0 \
#      --build-arg MODULE_NAME=ydsz-gateway \
#      --build-arg APP_PORT=9000 \
#      -f ydsz-backend/Dockerfile ydsz-backend/
#
#  批量构建: 参见 deploy/scripts/build-images.sh / build-images.ps1
#
#  设计要点（对齐阿里/字节容器化规范）:
#    1. 多阶段构建，构建期与运行期镜像分离，运行期镜像 ≤ 200MB
#    2. 显式非 root 用户（ydsz:65532），只读根文件系统友好
#    3. tini 作为 PID 1，正确处理 SIGTERM 优雅停机
#    4. JVM 容器化参数：-XX:+UseContainerSupport + MaxRAMPercentage
#    5. BuildKit 缓存挂载加速 Maven 构建（.m2 / target）
#    6. 健康检查通过 Spring Boot Actuator
#    7. 时区固定 Asia/Shanghai
# =============================================================================
# syntax=docker/dockerfile:1.7

# -----------------------------------------------------------------------------
#  Stage 1: Builder（Maven 构建）
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

ARG MODULE_NAME=ydsz-gateway
ARG APP_PORT=9000

# 时区
ENV TZ=Asia/Shanghai \
    MAVEN_OPTS="-Xmx1g -XX:+UseG1GC" \
    MAVEN_CONFIG=/root/.m2

WORKDIR /build

# 先拷贝父 POM 与所有模块的 pom.xml，利用 Docker 层缓存加速依赖解析
COPY pom.xml ./
COPY ydsz-common/pom.xml        ydsz-common/
COPY ydsz-gateway/pom.xml       ydsz-gateway/
COPY ydsz-system/pom.xml        ydsz-system/
COPY ydsz-userinfo/pom.xml      ydsz-userinfo/
COPY ydsz-literule/pom.xml      ydsz-literule/
COPY ydsz-project/pom.xml       ydsz-project/
COPY ydsz-cronjob/pom.xml       ydsz-cronjob/
COPY ydsz-workflow/pom.xml      ydsz-workflow/
COPY ydsz-agent/pom.xml         ydsz-agent/
COPY ydsz-message/pom.xml      ydsz-message/
COPY ydsz-nextwiki/pom.xml      ydsz-nextwiki/

# 预下载依赖（仅当 pom 变化时重新执行，利用 BuildKit 缓存）
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE_NAME} -am dependency:go-offline -DskipTests || true

# 拷贝源码（增量构建）
COPY ydsz-common/        ydsz-common/
COPY ydsz-gateway/       ydsz-gateway/
COPY ydsz-system/        ydsz-system/
COPY ydsz-userinfo/      ydsz-userinfo/
COPY ydsz-literule/      ydsz-literule/
COPY ydsz-project/       ydsz-project/
COPY ydsz-cronjob/       ydsz-cronjob/
COPY ydsz-workflow/      ydsz-workflow/
COPY ydsz-agent/         ydsz-agent/
COPY ydsz-message/       ydsz-message/
COPY ydsz-nextwiki/      ydsz-nextwiki/
COPY checkstyle.xml spotbugs-exclude.xml ./

# 构建指定模块及其依赖模块，跳过测试（CI 流水线已单独跑测试）
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl ${MODULE_NAME} -am clean package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true

# 提取构建产物（spring-boot-maven-plugin 产出的可执行 jar）
RUN mkdir -p /artifacts && \
    cp ${MODULE_NAME}/target/${MODULE_NAME}.jar /artifacts/app.jar && \
    ls -lh /artifacts/app.jar

# -----------------------------------------------------------------------------
#  Stage 2: Runtime（最小化运行镜像）
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG MODULE_NAME=ydsz-gateway
ARG APP_PORT=9000

LABEL org.opencontainers.image.title="ydsz-${MODULE_NAME}" \
      org.opencontainers.image.source="https://github.com/njydsz/ydsz-pmis" \
      org.opencontainers.image.licenses="UNLICENSED" \
      org.opencontainers.image.description="南京云顶 YDSZ · ${MODULE_NAME} 微服务"

# 安装 tini（PID 1，处理信号）+ curl（健康检查）+ tzdata（时区）+ ca-certificates
RUN apk add --no-cache tini curl tzdata ca-certificates && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    # 创建非 root 用户
    addgroup -g 65532 -S ydsz && \
    adduser -u 65532 -S ydsz -G ydsz -h /app && \
    # 清理 apk 缓存
    rm -rf /var/cache/apk/*

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    # JVM 容器化参数（对齐 JDK 21 + Spring Boot 4 推荐）
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    MODULE_NAME=${MODULE_NAME} \
    APP_PORT=${APP_PORT}

WORKDIR /app

# 从 builder 拷贝 jar
COPY --from=builder /artifacts/app.jar /app/app.jar

# 创建日志目录并授权
RUN mkdir -p /app/logs && chown -R ydsz:ydsz /app

USER ydsz:ydsz

EXPOSE ${APP_PORT}

# Spring Boot Actuator 健康检查（liveness/readiness 由 K8s probe 直接访问）
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -sf http://127.0.0.1:${APP_PORT}/actuator/health || exit 1

# P0-3: SkyWalking Java Agent 支持（可选）
# K8s 环境：通过 init-container + emptyDir 注入 agent（见 patch-skywalking.yaml）
# Docker Compose 环境：通过 volume 挂载 agent 目录
#   docker run -v /path/to/skywalking-agent:/skywalking/agent:ro ...
# 或通过 SW_AGENT_ENABLED=true 环境变量激活（需 agent 已挂载到 /skywalking/agent）
ENV SW_AGENT_ENABLED=${SW_AGENT_ENABLED:-false}

# tini 作为 PID 1，正确转发 SIGTERM 给 JVM
# P0-3: 条件注入 SkyWalking agent — 若 /skywalking/agent/skywalking-agent.jar 存在则加载
ENTRYPOINT ["/sbin/tini", "--", "sh", "-c", "\
  if [ -f /skywalking/agent/skywalking-agent.jar ] && [ \"$SW_AGENT_ENABLED\" = \"true\" ]; then \
    AGENT_OPT=\"-javaagent:/skywalking/agent/skywalking-agent.jar\"; \
    echo '[SkyWalking] Agent enabled: ' $AGENT_OPT; \
  else \
    AGENT_OPT=''; \
  fi; \
  exec java $JAVA_OPTS $AGENT_OPT -jar /app/app.jar \
"]
