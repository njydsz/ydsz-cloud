# Docker 部署目录（批次 19 补全）

PMIS 微服务的容器化部署产物，目录结构：

```
deploy/docker/
├── Dockerfile.base          # JDK 21 基础运行时镜像（非 root + tini + JVM 调优）
├── Dockerfile.gateway       # 网关层示例（其他 13 个服务按此模板）
├── docker-entrypoint.sh     # 统一启动入口（支持外部配置覆盖）
├── build-images.sh          # 14 个微服务批量构建脚本
├── docker-compose.yml       # 开发环境编排（PostgreSQL/Redis/Nacos/PMIS 全栈）
├── README.md                # 本文件
```

## 快速开始

### 1. 构建所有镜像

```bash
cd deploy/docker
chmod +x build-images.sh docker-entrypoint.sh
./build-images.sh 1.0.0
```

### 2. 启动开发环境

```bash
docker compose -f docker-compose.yml up -d
```

### 3. 验证服务

```bash
# 14 个微服务启动检查
./smoke-test/run.sh

# 跨服务调用链
./smoke-test/feign-test.sh
```

## 镜像分层

| 层 | 大小 | 说明 |
|----|------|------|
| `pmis-base:1.0.0` | ~280MB | 基础运行时（Temurin JDK 21 JRE + tini + locale） |
| `pmis-{service}:1.0.0` | ~350MB | 业务服务（基础层 + 业务 jar ~50MB） |

## 关键设计

1. **非 root 运行**：所有微服务以 UID 10001 (appuser) 运行
2. **JVM 容器感知**：`MaxRAMPercentage=75.0` 自动适配容器内存限制
3. **G1GC 默认**：低延迟场景友好
4. **慢启动诊断**：启动超 60s 自动输出 WARN
5. **配置外置**：`/opt/pmis/conf/application.yml` 可挂载覆盖 jar 内配置
6. **健康检查**：默认指向 `/actuator/health`（由子镜像 ENV 覆盖端口）
7. **优雅停机**：tini PID 1 信号转发，容器 stop 时 JVM 收到 SIGTERM

## 国产化 JDK 切换

如需替换为基础镜像中的 OpenJDK 21 为 Alibaba Dragonwell 21：

```dockerfile
# Dockerfile.base 第一行改为
FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:21-alpine
```

或 Bellsoft Liberica：

```dockerfile
FROM bellsoft/liberica-openjdk-alpine:21
```
