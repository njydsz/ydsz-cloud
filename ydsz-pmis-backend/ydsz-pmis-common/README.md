# ydsz-pmis-common

> PMIS 公共能力底座（L1-L6 分层公共依赖库，不独立部署）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 公共依赖库（**不独立部署**，仅作为 jar 依赖） |
| **打包** | `pom`（聚合模块，本身不产出 jar） |
| **作用** | 被 10 个部署单元 + 1 个规则引擎库依赖 |
| **构建顺序** | 1/12（Maven 构建最先编译） |
| **JVM 进程** | 无（仅作为 jar 依赖） |

## L1-L6 分层架构

本模块按 DDD 分层组织 **17 个子模块**，依赖方向严格自下而上（上层依赖下层，不可反向）：

```
L1 基础设施层  → ydsz-pmis-common-core
L2 工具模块层  → ydsz-pmis-common-util
L3 基础服务层  → ydsz-pmis-common-domain, ydsz-pmis-common-exception
L4 基础数据层  → ydsz-pmis-common-jdbc, ydsz-pmis-common-redis, ydsz-pmis-common-lock
L5 业务服务层  → ydsz-pmis-common-auth, ydsz-pmis-common-safe, ydsz-pmis-common-feign,
                ydsz-pmis-common-audit, ydsz-pmis-common-file, ydsz-pmis-common-notify,
                ydsz-pmis-common-queue
L6 应用层     → ydsz-pmis-common-base, ydsz-pmis-common-web, ydsz-pmis-common-app
```

### 子模块职责速查

| 层级 | 模块 | 职责 |
|---|---|---|
| L1 | common-core | 统一响应/请求模型、TraceId、请求上下文、JobHandler |
| L2 | common-util | 80+ 工具类（JSON/加密/HTTP/IP/Spring/雪花 ID 等） |
| L3 | common-domain | DDD 基类（BaseEntity/AggregateRoot）、领域事件、规范模式、分页、树 |
| L3 | common-exception | 15+ 异常类、统一错误码、ProblemDetail (RFC 7807)、i18n、双栈异常处理器 |
| L4 | common-jdbc | MyBatis-Plus 增强、动态数据源、行/列权限、逻辑删除、乐观锁、租户隔离 |
| L4 | common-redis | Redis 6 种 ops + 9 种高级 ops、布隆过滤器、延迟队列、限流、缓存击穿防护 |
| L4 | common-lock | 分布式锁（4 种实现）、@Idempotent 幂等、@YdszDistributedLock、WatchDog、读写锁、信号量 |
| L5 | common-auth | JWT、RBAC 4 注解 + 3 切面、@DataScope 数据权限、TOTP 2FA |
| L5 | common-safe | @Sensitive 7 种脱敏、@Xss、@RateLimit、CSRF、SQL 注入防护、验证码、安全事件总线 |
| L5 | common-feign | OpenFeign 增强、统一编解码、ResponseUnwrapDecoder、DefaultFallbackFactory、Resilience4j 熔断 |
| L5 | common-audit | @OperationLog + @Audit、AuditAspect、事件驱动异步落库、Disruptor 高性能批写、4 种分片策略 |
| L5 | common-file | 7 种存储平台（Local/OSS/Minio/S3/COS/OBS/Qiniu）、分片上传、断点续传、文件去重、Tika 类型检测 |
| L5 | common-notify | 5 种通知渠道（邮件/短信/企微/钉钉/飞书）、SpEL 模板引擎、重试队列、滑动窗口限流 |
| L5 | common-queue | 5 种 MQ（Redis×3/Kafka/RocketMQ/RabbitMQ/ActiveMQ）、死信队列、消息轨迹、消费者限流 |
| L5 | common-doc | OpenAPI 3.0 + SpringDoc + Knife4j UI 增强 + Markdown 文档导出 |
| L6 | common-base | HTTP 公共基座（CORS/时区/I18n/安全头/TraceId/请求日志/全局响应包装） |
| L6 | common-web | **PC Web 端基座**（继承 base，叠加 Spring Security/WebAuthFilter/Session） |
| L6 | common-app | **移动端 App 基座**（继承 base，叠加 AppSignatureFilter 防重放/AppAuthHandler） |

> **注意**：`common-web` 与 `common-app` 是两个**平行**的应用层入口，分别面向 PC Web 服务和移动端 App。后端微服务统一使用 `common-web`，`common-app` 仅用于未来移动端项目。

## 自动配置机制

所有 17 个子模块统一使用 Spring Boot 3+ 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 机制自动装配（**不使用** `spring.factories`）。

各服务的启动类通过 `@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.{service}", "com.njydsz.pmis.common"})` 扫描 common 包，激活自动配置。

> **例外**：`ydsz-pmis-gateway` 为 reactive 栈（WebFlux），**不依赖** `common-web`（servlet 栈），只挑选 `common-core` / `common-exception` / `common-auth` 三个细粒度子模块。

## Nacos 共享配置

**位置**：`deploy/common/nacos/ydsz-pmis-common.yaml`

> ⚠️ 注意：共享配置模板**不在** `ydsz-pmis-common/src/main/resources/` 下（本模块是聚合 POM，无 `src` 目录）。实际位于 `deploy/common/nacos/`，通过导入脚本部署到 Nacos。

**部署**：
```bash
# Ubuntu
deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# Windows
deploy\windows\scripts\import-nacos-config.bat pmis dev
```

**共享配置提供的能力**：

| 配置块 | 内容 |
|---|---|
| `spring.datasource` | Druid 主数据源（PostgreSQL） |
| `spring.datasource.dynamic` | baomidou 动态数据源（master/slave 读写分离） |
| `spring.data.redis` | Redis（支持 Sentinel HA） |
| `spring.cache.type` | Spring Cache 统一用 Redis |
| `spring.jackson.*` | 统一日期格式 / 时区 / 非空序列化 |
| `management.*` | Actuator + Prometheus 指标暴露 |
| `mybatis-plus.*` | 共享基础配置（map-underscore / logic-delete / id-type / log-impl） |
| `feign.client.config` | Feign 超时（default 3s / project 10s / agent 30s） |
| `spring.cloud.openfeign.circuitbreaker` | Feign + Sentinel 熔断 |
| `resilience4j.*` | 重试 + 熔断器（feignRetry / dbRetry / default） |
| `springdoc` + `knife4j` | OpenAPI / Knife4j 共享配置 |
| `logging.*` | 日志级别 + pattern（含 traceId） |
| `pmis.jwt` / `pmis.security` / `pmis.kms` / `pmis.sentry` | JWT、IP 白名单、KMS、Sentry |
| `jasypt.encryptor` | 配置加密 |

**单一来源原则**：所有 10 个部署单元的公共配置，**只能**在 `deploy/common/nacos/ydsz-pmis-common.yaml` 维护。各服务 `bootstrap.yml` 通过 `spring.cloud.nacos.config.shared-configs` 引用此 dataId，服务特有配置才写到 `ydsz-pmis-{service}-{env}.yaml`。

## 10 个部署单元清单

| 序号 | 服务名 | 端口 | 启动类 scanBasePackages | 依赖的 common 子模块 |
|---|---|---|---|---|
| 1 | ydsz-pmis-gateway | 9000 | `com.njydsz.pmis.gateway`（裸 @SpringBootApplication） | common-core / common-exception / common-auth |
| 2 | ydsz-pmis-userinfo | 9001 | userinfo + common | 全量（通过 common-web） |
| 3 | ydsz-pmis-system | 9002 | system + common | 全量（通过 common-web） |
| 4 | ydsz-pmis-project | 9003 | project + common + literule | 全量（通过 common-web） |
| 5 | ydsz-pmis-message | 9004 | message + common | 全量（通过 common-web） |
| 6 | ydsz-pmis-cronjob | 9005 | cronjob + common | 全量（通过 common-web） |
| 7 | ydsz-pmis-workflow | 9006 | workflow + common | 全量（通过 common-web） |
| 8 | ydsz-pmis-agent | 9007 | agent + common + project | 全量（通过 common-web） |
| 9 | ydsz-pmis-sales | 9010 | sales + common + literule | 全量（通过 common-web） |
| 10 | ydsz-pmis-finance | 9011 | finance + common + literule | 全量（通过 common-web） |

> **literule** 不是独立部署单元，是 jar 库，被 sales/project/finance 作为业务规则引擎嵌入。

## 目录结构

```
ydsz-pmis-common/
├── pom.xml                    # 聚合 POM（packaging=pom）
├── ydsz-pmis-common-core/     # L1 基础设施层
├── ydsz-pmis-common-util/     # L2 工具模块层
├── ydsz-pmis-common-domain/   # L3 基础服务层
├── ydsz-pmis-common-exception/# L3 基础服务层
├── ydsz-pmis-common-jdbc/     # L4 基础数据层
├── ydsz-pmis-common-redis/    # L4 基础数据层
├── ydsz-pmis-common-lock/     # L4 基基础数据层
├── ydsz-pmis-common-auth/     # L5 业务服务层
├── ydsz-pmis-common-safe/     # L5 业务服务层
├── ydsz-pmis-common-feign/    # L5 业务服务层
├── ydsz-pmis-common-audit/    # L5 业务服务层
├── ydsz-pmis-common-file/     # L5 业务服务层
├── ydsz-pmis-common-notify/   # L5 业务服务层
├── ydsz-pmis-common-queue/    # L5 业务服务层
├── ydsz-pmis-common-base/     # L6 应用层（HTTP 公共基座）
├── ydsz-pmis-common-web/      # L6 应用层（PC Web 端基座）
└── ydsz-pmis-common-app/      # L6 应用层（移动端 App 基座）
```

## 构建

```bash
# 仅构建 common 模块（含所有 17 个子模块）
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-common -am clean install

# 全量构建
mvn clean install
```

## 测试

```bash
# 仅测试 common 模块
mvn -pl ydsz-pmis-common -am test

# 全模块回归
mvn test
```

## 版本与变更

- **首发版本**：v1.0.0（2026-06-30）
- **当前版本**：v1.3.0-SNAPSHOT
- **变更需走 PR + Code Review**

---

> 任何对本模块的修改（注解 / 切面 / Feign / 工具类）都会影响所有 10 个部署单元，
> 必须经过充分测试与跨服务联调验证。
