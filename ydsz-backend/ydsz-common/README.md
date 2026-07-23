# ydsz-common

> YDSZ 公共能力底座（L1-L6 分层公共依赖库，不独立部署）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 公共依赖库（**不独立部署**，仅作为 jar 依赖） |
| **打包** | `pom`（聚合模块，本身不产出 jar） |
| **作用** | 被 10 个部署单元 + 1 个规则引擎库依赖 |
| **构建顺序** | 1/12（Maven 构建最先编译） |
| **JVM 进程** | 无（仅作为 jar 依赖） |

## L1-L6 分层架构

本模块按 DDD 分层组织 **27 个子模块**，依赖方向严格自下而上（上层依赖下层，不可反向）：

```
L1 基础设施层  → ydsz-common-core
L2 工具模块层  → ydsz-common-util, ydsz-common-json
L3 基础服务层  → ydsz-common-domain, ydsz-common-exception
L4 基础数据层  → ydsz-common-jdbc, ydsz-common-redis, ydsz-common-lock,
                 ydsz-common-cache
L5 业务服务层  → ydsz-common-auth, ydsz-common-safe, ydsz-common-feign,
                 ydsz-common-audit, ydsz-common-file, ydsz-common-notify,
                 ydsz-common-queue, ydsz-common-docs, ydsz-common-excel,
                 ydsz-common-netty, ydsz-common-socket,
                 ydsz-common-search, ydsz-common-event,
                 ydsz-common-config, ydsz-common-seata, ydsz-common-sentry
L6 应用层     → ydsz-common-base, ydsz-common-web, ydsz-common-app
```

### 子模块职责速查

| 层级 | 模块 | 职责 |
|---|---|---|
| L1 | [common-core](ydsz-common-core/README.md) | 统一响应/请求模型、TraceId、请求上下文、JobHandler、DAG、特性开关、重试模板、线程池监控 |
| L2 | [common-util](ydsz-common-util/README.md) | 99 个工具类（JSON/加密/HTTP/IP/Spring/雪花 ID/Bean 拷贝 等） |
| L2 | [common-json](ydsz-common-json/README.md) | 高性能 JSON 引擎（ASM 字节码、SIMD 向量化、Schema 校验、YdszJsonPath、树模型） |
| L3 | [common-domain](ydsz-common-domain/README.md) | DDD 基类（BaseEntity/AggregateRoot）、领域事件、规范模式、分页、树形结构 |
| L3 | [common-exception](ydsz-common-exception/README.md) | 统一异常体系、错误码管理、ProblemDetail (RFC 7807)、i18n、异常构建器 |
| L4 | [common-jdbc](ydsz-common-jdbc/README.md) | MyBatis-Plus 增强、动态数据源、行/列权限、逻辑删除、乐观锁、租户隔离、字段填充 |
| L4 | [common-redis](ydsz-common-redis/README.md) | Redis 6 种 ops + 9 种高级 ops、布隆过滤器、延迟队列、限流、缓存击穿防护 |
| L4 | [common-lock](ydsz-common-lock/README.md) | 分布式锁（4 种实现）、@Idempotent 幂等、@YdszDistributedLock、WatchDog、读写锁、信号量 |
| L4 | [common-cache](ydsz-common-cache/README.md) | 高性能多策略本地缓存框架（Window-TinyLFU/LRU/LFU/TTL/MultiLevel）、三防、熔断降级 |
| L5 | [common-auth](ydsz-common-auth/README.md) | JWT、RBAC 4 注解 + 3 切面、@DataScope 数据权限、TOTP 2FA、权限缓存热更新 |
| L5 | [common-safe](ydsz-common-safe/README.md) | @Sensitive 7 种脱敏、@Xss、@RateLimit、CSRF、SQL 注入防护、验证码、安全事件告警 |
| L5 | [common-feign](ydsz-common-feign/README.md) | OpenFeign 增强、统一编解码、ResponseUnwrapDecoder、Resilience4j 熔断、动态客户端 |
| L5 | [common-audit](ydsz-common-audit/README.md) | @OperationLog + @Audit、事件驱动异步落库、Disruptor 高性能批写、4 种分片策略 |
| L5 | [common-file](ydsz-common-file/README.md) | 7 种存储平台、分片上传、断点续传、文件去重（秒传）、文件类型安全检测 |
| L5 | [common-notify](ydsz-common-notify/README.md) | 5 种通知渠道（邮件/短信/企微/钉钉/飞书）、SpEL 模板引擎、重试队列、DKIM 签名 |
| L5 | [common-queue](ydsz-common-queue/README.md) | 5 种 MQ（Redis×3/Kafka/RocketMQ/RabbitMQ/ActiveMQ）、死信队列、消息轨迹、去重 |
| L5 | [common-docs](ydsz-common-docs/README.md) | 8 种格式解析、预处理 Pipeline、安全扫描、PII 检测（5 种）、文本水印、PDF 脱敏、OCR |
| L5 | [common-excel](ydsz-common-excel/README.md) | 高性能 Excel 读写（SAX 流式/SXSSF 大文件）、并发写入、模板填充、公式注入防护 |
| L5 | [common-netty](ydsz-common-netty/README.md) | Netty TCP Server/Client 抽象、断线重连、心跳检测、SSL/TLS、LengthField 编解码 |
| L5 | [common-socket](ydsz-common-socket/README.md) | WebSocket 实时推送、集群广播、离线消息存储、认证拦截、消息限流 |
| L5 | [common-search](ydsz-common-search/README.md) | 统一搜索引擎（PG 全文检索/ES）、多 Provider 架构、搜索缓存、蓝绿重建、游标分页 |
| L5 | [common-event](ydsz-common-event/README.md) | 事务性 Outbox 模式、可靠事件投递、Outbox 处理器、健康检查 |
| L5 | [common-config](ydsz-common-config/README.md) | 敏感配置加密（AES-256-GCM、SHA-256 密钥派生、ENC() 格式） |
| L5 | [common-seata](ydsz-common-seata/README.md) | Seata 分布式事务集成（AT/TCC/SAGA 模式） |
| L5 | [common-sentry](ydsz-common-sentry/README.md) | 统一系统指标监控（ELK+Logstash / Loki+Alloy 双方案、SLA、告警收敛、Grafana 仪表盘） |
| L6 | [common-base](ydsz-common-base/README.md) | HTTP 公共基座（CORS/时区/I18n/安全头/TraceId/请求日志/全局响应包装/OpenAPI） |
| L6 | [common-web](ydsz-common-web/README.md) | **PC Web 端基座**（继承 base，叠加 Spring Security/WebAuthFilter/Session） |
| L6 | [common-app](ydsz-common-app/README.md) | **移动端 App 基座**（继承 base，叠加 API 签名验证/AppAuthHandler） |

> **注意**：`common-web` 与 `common-app` 是两个**平行**的应用层入口，分别面向 PC Web 服务和移动端 App。后端微服务统一使用 `common-web`，`common-app` 仅用于未来移动端项目。

## 自动配置机制

所有 27 个子模块统一使用 Spring Boot 3+ 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 机制自动装配（**不使用** `spring.factories`）。

各服务的启动类通过 `@SpringBootApplication(scanBasePackages = {"com.njydsz.{service}", "com.njydsz.common"})` 扫描 common 包，激活自动配置。

> **例外**：`ydsz-gateway` 为 reactive 栈（WebFlux），**不依赖** `common-web`（servlet 栈），只挑选 `common-core` / `common-exception` / `common-auth` 三个细粒度子模块。

## Nacos 共享配置

**位置**：`deploy/common/nacos/ydsz-common.yaml`

> ⚠️ 注意：共享配置模板**不在** `ydsz-common/src/main/resources/` 下（本模块是聚合 POM，无 `src` 目录）。实际位于 `deploy/common/nacos/`，通过导入脚本部署到 Nacos。

**部署**：
```bash
# Ubuntu
deploy/ubuntu/scripts/import-nacos-config.sh ydsz dev

# Windows
deploy\windows\scripts\import-nacos-config.bat ydsz dev
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
| `ydsz.jwt` / `ydsz.security` / `ydsz.kms` / `ydsz.sentry` | JWT、IP 白名单、KMS、Sentry |
| `jasypt.encryptor` | 配置加密 |

**单一来源原则**：所有 10 个部署单元的公共配置，**只能**在 `deploy/common/nacos/ydsz-common.yaml` 维护。各服务 `bootstrap.yml` 通过 `spring.cloud.nacos.config.shared-configs` 引用此 dataId，服务特有配置才写到 `ydsz-{service}-{env}.yaml`。

## 10 个部署单元清单

| 序号 | 服务名 | 端口 | 启动类 scanBasePackages | 依赖的 common 子模块 |
|---|---|---|---|---|
| 1 | ydsz-gateway | 9000 | `com.njydsz.gateway`（裸 @SpringBootApplication） | common-core / common-exception / common-auth |
| 2 | ydsz-userinfo | 9001 | userinfo + common | 全量（通过 common-web） |
| 3 | ydsz-system | 9002 | system + common | 全量（通过 common-web） |
| 4 | ydsz-project | 9003 | project + common + literule（含 sales/finance 合并域） | 全量（通过 common-web） |
| 5 | ydsz-message | 9004 | message + common | 全量（通过 common-web） |
| 6 | ydsz-cronjob | 9005 | cronjob + common | 全量（通过 common-web） |
| 7 | ydsz-workflow | 9006 | workflow + common | 全量（通过 common-web） |
| 8 | ydsz-agent | 9007 | agent + common + project | 全量（通过 common-web） |

> **literule** 不是独立部署单元，是 jar 库，被 project 作为业务规则引擎嵌入。
>
> **2026-07-16 合并**：原 `ydsz-sales`（端口 9010）和 `ydsz-finance`（端口 9011）已合并到 `ydsz-project`。

## 目录结构

```
ydsz-common/
├── pom.xml                    # 聚合 POM（packaging=pom）
├── ydsz-common-core/     # L1 基础设施层
├── ydsz-common-util/     # L2 工具模块层
├── ydsz-common-json/     # L2 工具模块层（高性能 JSON 引擎）
├── ydsz-common-domain/   # L3 基础服务层
├── ydsz-common-exception/# L3 基础服务层
├── ydsz-common-jdbc/     # L4 基础数据层
├── ydsz-common-redis/    # L4 基础数据层
├── ydsz-common-lock/     # L4 基础数据层
├── ydsz-common-cache/    # L4 基础数据层（高性能多策略本地缓存）
├── ydsz-common-auth/     # L5 业务服务层
├── ydsz-common-safe/     # L5 业务服务层
├── ydsz-common-feign/    # L5 业务服务层
├── ydsz-common-audit/    # L5 业务服务层
├── ydsz-common-file/     # L5 业务服务层
├── ydsz-common-notify/   # L5 业务服务层
├── ydsz-common-queue/    # L5 业务服务层
├── ydsz-common-docs/     # L5 业务服务层（文档解析/安全/PII/水印/脱敏/OCR）
├── ydsz-common-excel/    # L5 业务服务层（高性能 Excel 读写）
├── ydsz-common-netty/    # L5 业务服务层（Netty TCP 通信）
├── ydsz-common-socket/   # L5 业务服务层（WebSocket 实时推送）
├── ydsz-common-search/   # L5 业务服务层（统一搜索引擎）
├── ydsz-common-event/    # L5 业务服务层（事务性 Outbox）
├── ydsz-common-config/   # L5 业务服务层（敏感配置加密）
├── ydsz-common-seata/    # L5 业务服务层（Seata 分布式事务）
├── ydsz-common-sentry/   # L5 业务服务层（统一系统指标监控）
├── ydsz-common-base/     # L6 应用层（HTTP 公共基座）
├── ydsz-common-web/      # L6 应用层（PC Web 端基座）
└── ydsz-common-app/      # L6 应用层（移动端 App 基座）
```

## 构建

```bash
# 仅构建 common 模块（含所有 27 个子模块）
cd ydsz-backend
mvn -pl ydsz-common -am clean install

# 全量构建
mvn clean install
```

## 测试

```bash
# 仅测试 common 模块
mvn -pl ydsz-common -am test

# 全模块回归
mvn test
```

## 版本与变更

- **首发版本**：v1.0.0（2026-06-30）
- **当前版本**：v1.0.0-SNAPSHOT
- **变更需走 PR + Code Review**

---

> 任何对本模块的修改（注解 / 切面 / Feign / 工具类）都会影响所有 10 个部署单元，
> 必须经过充分测试与跨服务联调验证。
