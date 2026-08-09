# ydsz-cloud 配置优先级矩阵

**版本**: 1.0.0  
**更新日期**: 2026-08-09  
**范围**: 所有使用 `ydsz-common-config` 模块的服务

---

## 概述

本文档明确 ydsz-cloud 项目中所有配置源的加载优先级，避免因覆盖顺序不确定导致的运行时行为异常。

## 优先级从高到低

| 优先级 | 配置源 | 示例 | 适用场景 |
|--------|--------|------|----------|
| 1 (最高) | 命令行参数 | `--server.port=8080` | 临时覆盖、CI/CD 注入 |
| 2 | Java 系统属性 | `-Dydsz.json.max-depth=512` | 容器启动脚本设置 |
| 3 | 操作系统环境变量 | `YDSZ_JSON_MAX_DEPTH=512` | 敏感信息（密钥、密码） |
| 4 | Profile 专属 application 文件 | `application-prod.yml` | 环境差异化配置 |
| 5 | 共享 Config Server | Nacos / Apollo 远程配置 | 分布式统一配置 |
| 6 | 本地 application.yml | `application.yml` | 默认开发和通用配置 |
| 7 (最低) | 代码内 `@Value` 默认值 | `@Value("${timeout:30}")` | 兜底默认 |

## 核心原则

### 1. 外部配置优先于内部

所有敏感信息（数据库密码、API 密钥、第三方 Token）**必须**通过环境变量或 Config Server 注入，**不允许**硬编码到代码或本地 yaml 文件中。

### 2. Profile 隔离

```
application.yml          ← 共享基线（所有 profile 生效）
application-dev.yml      ← 开发环境覆盖（仅 dev profile 加载）
application-prod.yml     ← 生产环境覆盖（仅 prod profile 加载）
```

激活多个 profile 时，后激活的覆盖先激活的（`spring.profiles.include` 顺序）。

### 3. 配置命名规范

| 层级 | 前缀 | 示例 |
|------|------|------|
| ydsz-cloud 框架配置 | `ydsz.` | `ydsz.json.max-depth` |
| Spring Boot 原生配置 | `spring.` | `spring.datasource.url` |
| JVM 标准配置 | `java.` | `java.awt.headless` |
| 业务域自定义配置 | `<domain>.` | `project.max-members` |

### 4. 动态刷新支持

标记 `@RefreshScope` 或使用 `ydsz.common.config.ConfigChangeListener` 的配置项，在 Config Server 推送变更后自动生效，无需重启。

**已知可热更新的配置**：

- `ydsz.json.*` — JSON 序列化行为
- `ydsz.safe.*` — 安全策略（SQL 注入、XSS、SSRF 防护）
- `ydsz.thread.*` — 线程池参数
- `ydsz.cache.*` — 缓存 TTL 和策略
- `ydsz.audit.*` — 审计日志开关

**需要重启的配置**：

- 数据源连接池大小
- Redis 连接拓扑
- 服务端口绑定

## 安全配置最佳实践

### 敏感字段加密

使用 Jasypt 加密配置项，配置值以 `ENC(...)` 包裹：

```yaml
# application-prod.yml
spring:
  datasource:
    password: ENC(encryptedPasswordHere)
```

主密码通过环境变量注入：

```bash
export JASYPT_ENCRYPTOR_PASSWORD=theMasterPassword
```

### 配置白名单

通过 `ydsz.config.allowed-prefix` 限制运行时可热更新的配置前缀，防止误改关键配置。

## 调试指南

### 查看当前生效的所有配置

```
GET /actuator/env
```

或查看配置解析报告：

```
GET /actuator/configprops
```

### 排查配置覆盖

启用 DEBUG 日志：

```yaml
logging:
  level:
    org.springframework.boot.context.config: DEBUG
    com.njydsz.common.config: DEBUG
```

### 本地启动指定 Profile

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev,local-nacos
```

## 引用

- Spring Boot 官方文档：Externalized Configuration
- Spring Cloud 官方文档：Config First Bootstrap
- Nacos 官方文档：配置管理
- 12-Factor App：III. 配置
