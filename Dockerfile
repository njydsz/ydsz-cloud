# =============================================================
# 多阶段构建：ydsz-cloud 业务模块通用 Docker 镜像
# 适用于所有 ydzs-* 子模块（gateway / system / cronjob / ...）
# 业务模块在构建时只需将此 Dockerfile 拷贝到模块目录下
# 并通过 --build-arg MODULE_DIR=... 传入构建上下文即可复用
# =============================================================
# 维护信息（OCI 规范 labels）
LABEL org.opencontainers.image.authors="ydsz-dev@njydsz.com" \
      org.opencontainers.image.title="ydsz-cloud-service" \
      org.opencontainers.image.description="Ydsz Cloud business service container" \
      org.opencontainers.image.version="26.09.01-SNAPSHOT" \
      org.opencontainers.image.vendor="njydsz" \
      org.opencontainers.image.licenses="Proprietary"

# 构建参数：允许调用方指定业务模块子目录；默认使用根 pom
ARG MODULE_DIR=.

# =============================================================
# 阶段 1: build（编译 + 打包，跳过测试——测试在 CI 阶段完成）
# 使用 Maven 3.9 + Eclipse Temurin 21 Alpine 镜像
# =============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# 先单独 COPY pom.xml（利用 Docker 层缓存加速依赖解析）
COPY pom.xml .
COPY ${MODULE_DIR}/pom.xml ${MODULE_DIR}/pom.xml

# 下载依赖层（仅当 pom 变化时失效；业务代码变更不会重跑依赖下载）
# 注意：多模块项目需要先解析所有子模块的 pom
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -B

# 拷贝业务模块源码
COPY ${MODULE_DIR}/src ${MODULE_DIR}/src

# 打包（跳过测试 & checkstyle；CI 已单独校验）
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -DskipTests -Dcheckstyle.skip=true -B -pl ${MODULE_DIR} -am

# =============================================================
# 阶段 2: runtime（运行时镜像，仅含 JRE 21 Alpine）
# 从 build 阶段拷贝 fat-jar，构建最小化生产镜像
# =============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# 构建参数：要从 build 阶段拷贝的 JAR 路径（由调用方传入）
ARG JAR_FILE=target/*.jar
ARG MODULE_DIR=.

# 元数据 LABEL（与上方阶段呼应，最终镜像保留）
LABEL maintainer="ydsz-dev@njydsz.com" \
      version="26.09.01-SNAPSHOT" \
      description="ydsz-cloud business module runtime"

# 拷贝 fat-jar：去掉 Spring Boot 分层后缀即可被 java -jar 执行
COPY --from=build /workspace/${MODULE_DIR}/${JAR_FILE} app.jar

# 暴露 Spring Boot 默认端口
EXPOSE 8080

# 健康检查：每 30 秒探测一次，超时 10 秒，连续失败 3 次标记 unhealthy
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM 参数：容器感知 + 生产调优
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8"

# 启动入口
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
