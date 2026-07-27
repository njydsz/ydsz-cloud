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
| L5 | [common-safe](ydsz-common-safe/README.md) | @Sensitive 7 种脱敏、@Xss、@SentinelRateLimit、CSRF、SQL 注入防护、验证码、安全事件告警 |
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

## SPI 扩展点速查表

ydsz-common 不使用 Dubbo `@SPI` 注解，所有扩展点通过三种 Spring 原生机制暴露：

| 机制 | 注册方式 | 典型场景 |
|---|---|---|
| **`@ConditionalOnMissingBean` 覆盖** | 业务方提供同类型 Bean 即可替换默认实现 | 单一实现的策略替换（如 `LockStrategy` / `EventStore` / `TraceContext`） |
| **`List<T>` / `ObjectProvider<List<T>>` 自动收集** | 业务方 `@Component` 注册，框架自动聚合 | 多实现扩展（如 `NotifyChannelStrategy` / `SearchProvider` / `YdszJsonModule`） |
| **`@ConditionalOnProperty` 切换** | 通过配置项值选择实现 | 后端切换（如 `ydsz.sentry.metrics.primary=micrometer` / `ydsz.seata.tcc-log-store=redis`） |

> 标识为 **SPI** 的接口在 JavaDoc 中明确声明"业务方可实现"；其余为通过 `@ConditionalOnMissingBean` 暴露的覆盖点。

### L1-L2 基础层

| 子模块 | 扩展点接口 | 职责 | 注册方式 |
|---|---|---|---|
| common-core | `TraceIdSupplier` | TraceId 生成策略 | `@ConditionalOnMissingBean` |
| common-core | `FeatureFlagService` | 特性开关后端 | `@ConditionalOnMissingBean` |
| common-util | `WorkerIdRegistry` **SPI** | 雪花 WorkerId 注册中心（Redis/ZK/ETCD/Nacos） | `@Component` |
| common-util | `PasswordEncoder` | 密码编码器（BCrypt/SCrypt/Argon2） | `@Component` |
| common-json | `YdszJsonModule` | JSON 编解码模块 | `List<YdszJsonModule>` 自动收集 |
| common-json | `JsonSerializer` / `JsonDeserializer` | 自定义序列化器 | `@Component` |
| common-json | `PropertyNamingStrategy` | 属性命名策略 | `@Component` |
| common-json | `JsonMetricsCallback` | JSON 指标回调 | `@ConditionalOnMissingBean` |

### L3 基础服务层

| 子模块 | 扩展点接口 | 职责 | 注册方式 |
|---|---|---|---|
| common-domain | `EventStore` **SPI** | 领域事件存储（默认 OutboxEventStore） | `@ConditionalOnMissingBean` |
| common-domain | `TreeNodeProvider` **SPI** | 树节点懒加载 | `@Component` |
| common-domain | `Repository` | DDD 聚合根仓储 | `@Component` |
| common-domain | `Specification` | 规约模式（可组合业务规则） | `@Component` |
| common-exception | `ExceptionAlertListener` | 异常告警监听器 | `List<ExceptionAlertListener>` 自动收集 |

### L4 基础数据层

| 子模块 | 扩展点接口 | 职责 | 注册方式 |
|---|---|---|---|
| common-jdbc | `DataScopeIdExpander` | 数据权限范围 ID 扩展（公司/部门/项目级联） | `@Component` |
| common-jdbc | `DataSourceLoadBalanceStrategy` | 数据源负载均衡策略 | `@ConditionalOnMissingBean` |
| common-jdbc | `FieldFillHandler` | MyBatis-Plus 审计字段填充 | `@ConditionalOnMissingBean` |
| common-redis | `RedisKeyPrefixProvider` | Redis Key 前缀自定义 | `@ConditionalOnMissingBean` |
| common-lock | `LockStrategy` | 分布式锁策略工厂 | `@ConditionalOnMissingBean` |
| common-lock | `IdempotentStrategy` | 幂等键策略 | `@ConditionalOnMissingBean` |
| common-lock | `DistributedLocker` | 分布式锁核心契约 | 由 `LockStrategy` 间接扩展 |
| common-cache | `CacheLoader<K,V>` | 缓存回源加载 | `@Component` |
| common-cache | `CacheWriter<K,V>` | Write-Through/Write-Behind 回写 | `@Component` |
| common-cache | `Expiry<K,V>` | 自定义 TTL/TTI 过期 | `@Component` |
| common-cache | `Weigher<K,V>` | 条目权重计算 | `@Component` |
| common-cache | `RemovalListener<K,V>` | 条目驱逐/失效回调 | `@Component` |
| common-cache | `CacheInvalidationBroadcaster` | 多级缓存跨节点广播 | `@ConditionalOnMissingBean` |

### L5 业务服务层（认证/安全/链路）

| 子模块 | 扩展点接口 | 职责 | 注册方式 |
|---|---|---|---|
| common-auth | `AuthenticationProvider` **SPI** | 认证提供者（JWT/自定义） | `@ConditionalOnBean` |
| common-auth | `AuthHandler` | 认证信息解析（Web/App） | `@Component` |
| common-auth | `DataPermissionResolver` | 数据权限解析 | `@ConditionalOnMissingBean` |
| common-auth | `DataPermissionCustomSqlProvider` **SPI** | 数据权限动态 SQL | `@Component`（支持 `getOrder()`） |
| common-auth | `RolePermissionLoader` **SPI** | 角色权限加载（Redis/DB/远程） | `@Bean` |
| common-auth | `ColumnPermissionResolver` | 列级权限解析 | `@Component` |
| common-auth | `RbacUserInfoService` | RBAC 用户信息加载 | `@Component` |
| common-auth | `CacheKeyStrategy` | 权限缓存 Key 策略 | `@Component` |
| common-auth | `PermissionChangeListener` | 权限变更回调 | `List<PermissionChangeListener>` 自动收集 |
| common-auth | `TokenService` | Token 生成/校验/刷新 | `@ConditionalOnMissingBean` |
| common-auth | `AuthMetrics` / `PermissionMetrics` | 认证鉴权指标采集 | `@Component` |
| common-safe | `CaptchaStore` | 验证码存储（默认本地内存） | `@ConditionalOnMissingBean` |
| common-safe | `CaptchaGenerator` | 验证码生成器（图形/算术/滑块） | `@ConditionalOnMissingBean` |
| common-safe | `CsrfTokenRepository` | CSRF Token 存储 | `@ConditionalOnMissingBean` |
| common-safe | `CsrfTokenGenerator` | CSRF Token 生成器 | `@ConditionalOnMissingBean` |
| common-safe | `SecurityAlertListener` | 安全事件告警回调 | `List<SecurityAlertListener>` 自动收集 |
| common-safe | `RateLimitRuleProvider` **SPI** | 限流规则提供者（动态加载） | `@ConditionalOnMissingBean` |
| common-safe | `RateLimitRuleListener` | 限流规则热更新回调 | `@Component` |
| common-safe | `RateLimiter` | 限流算法（令牌桶/滑动窗口/漏桶） | `@Component` |
| common-safe | `ClusterRateLimiter` | 集群限流器 | `@Component` |
| common-feign | `FeignTraceHandler` **SPI** | Feign 链路追踪（SkyWalking/Zipkin） | `@Component` |
| common-feign | `FeignCircuitBreakerStrategy` | Feign 熔断器（Resilience4j/Sentinel） | `@ConditionalOnMissingBean` |

### L5 业务服务层（审计/文件/通知/队列）

| 子模块 | 扩展点接口 | 职责 | 注册方式 |
|---|---|---|---|
| common-audit | `AuditStorage` **SPI** | 审计日志存储（JDBC/ELK/MQ） | `@ConditionalOnMissingBean` |
| common-audit | `AuditRecorder` | 审计写入策略（异步/Disruptor） | `@ConditionalOnMissingBean` |
| common-audit | `TableShardingStrategy` | 审计分表策略（日/月/年） | `@ConditionalOnMissingBean` |
| common-audit | `DiffValueFormatter` | 字段差异格式化 | `@Component` |
| common-file | `IFileStorageProvider` | 文件存储工厂（Local/MinIO/S3/OSS/COS/OBS/Qiniu） | `@ConditionalOnMissingBean` |
| common-file | `CheckpointStore` / `MultipartContextStore` | 分片上传断点续传存储 | `@ConditionalOnMissingBean` |
| common-file | `CheckpointService` | 分片上传协调 | `@ConditionalOnMissingBean` |
| common-file | `VirusScanner` | 病毒扫描（默认 NoOp，可对接 ClamAV） | `@ConditionalOnMissingBean` |
| common-file | `UploadProgressListener` | 上传进度回调 | `@Component` |
| common-notify | `NotifyChannelStrategy` | 通知渠道（Email/SMS/WeCom/DingTalk/Feishu） | `List<NotifyChannelStrategy>` 自动收集 |
| common-notify | `NotifyService` | 统一通知编排 | `@ConditionalOnMissingBean` |
| common-notify | `TemplateEngine` | 模板引擎（Freemarker/Thymeleaf） | `ObjectProvider<TemplateEngine>` |
| common-notify | `SmsProvider` / `EmailProvider` | SMS/Email 提供商 | `@ConditionalOnMissingBean` |
| common-notify | `DeadLetterHandler` | 通知死信处理 | `@ConditionalOnMissingBean` |
| common-queue | `IMessageQueueProvider` | MQ 对象工厂 | `@ConditionalOnMissingBean` |
| common-queue | `IMessageHandler` / `IMessagePublisher` / `IMessageSubscriber` | 消息处理/发布/订阅 | `@Component` |
| common-queue | `MessageDeduplicator` | 消息去重器 | `@ConditionalOnMissingBean` |
| common-queue | `MessageTraceRecorder` | 消息轨迹记录 | `@ConditionalOnMissingBean` |
| common-queue | `RetryPolicy` | 消息重试退避策略 | `@Component` |

### L5 业务服务层（文档/Excel/网络/搜索/事件/事务/监控）

| 子模块 | 扩展点接口 | 职责 | 注册方式 |
|---|---|---|---|
| common-docs | `DocumentParser` | 文档解析器（PDF/Word/Excel/PPT） | `DocumentParserRegistry` 自动收集 |
| common-docs | `DocumentPreprocessor` | 文档预处理（OCR/去噪） | `@Component` |
| common-docs | `DocumentSecurityScanner` | 文档安全扫描 | `@Component` |
| common-docs | `DocumentRedactor` | 文档脱敏 | `@Component` |
| common-docs | `PiiDetector` | PII 检测 | `PiiDetectorComposite` 自动聚合 |
| common-docs | `WatermarkProvider` | 文档水印 | `@Component` |
| common-docs | `OcrEngine` | OCR 引擎（Tesseract/PaddleOCR/百度） | `@ConditionalOnMissingBean` |
| common-excel | `CellValueConverter` **SPI** | 单元格值转换器（支持 `priority()`） | `ConverterRegistry.registerCustomConverter()` |
| common-excel | `ReadHandler` / `ReadListener` | Excel 读取回调 | `@Component` |
| common-excel | `TabularRowMapper` / `ColumnarRowMapper` | 行映射器 | `@Component` |
| common-excel | `TabularWriteListener` / `TabularReadListener` | Excel 写入/读取监听器 | `@Component` |
| common-netty | `ChannelEventListener` | Channel 事件回调 | `List<ChannelEventListener>` 自动收集 |
| common-socket | `MessageSerializer` | WebSocket 消息序列化（JSON/Protobuf） | `@ConditionalOnMissingBean` |
| common-socket | `OfflineMessageStore` **SPI** | 离线消息存储（Redis/DB） | `@ConditionalOnMissingBean` |
| common-socket | `WebSocketConnectionListener` | 连接生命周期回调 | `List<WebSocketConnectionListener>` 自动收集 |
| common-socket | `MessageFilter` | 消息过滤器 | `List<MessageFilter>` 自动收集 |
| common-socket | `StompMessageInterceptor` | STOMP 拦截器 | `@ConditionalOnMissingBean` |
| common-search | `SearchProvider<T>` **SPI** | 业务实体 → 索引文档转换 | `List<SearchProvider<?>>` 自动收集 |
| common-search | `SearchEngine` | 搜索引擎（PG/ES/OpenSearch） | `@ConditionalOnMissingBean` |
| common-search | `ContentExtractor` **SPI** | 文件内容 → 纯文本 | `ObjectProvider<ContentExtractor>` |
| common-search | `ContentIndexer` | 内容索引器 | `@ConditionalOnMissingBean` |
| common-event | `EventPublishGateway` **SPI** | 事件投递网关（RocketMQ/Redis Stream/Kafka） | `@ConditionalOnMissingBean` |
| common-event | `OutboxEventStore` | Outbox 事件存储（实现 `EventStore`） | `@ConditionalOnMissingBean` |
| common-config | `ConfigChangeListener` **SPI** | 配置变更回调（Spring Cloud RefreshEvent） | `ObjectProvider<List<ConfigChangeListener>>` |
| common-seata | `TccTransactionLogStore` | TCC 事务日志存储（内存/Redis/DB） | `@ConditionalOnMissingBean` |
| common-seata | `XidPropagator` | XID 跨服务传播 | `@ConditionalOnMissingBean` |
| common-seata | `DistributedTransactionManager` | 分布式事务管理器（AT/TCC/Local） | `@ConditionalOnMissingBean` |
| common-seata | `TccAction<T>` **SPI** | TCC Try/Confirm/Cancel 动作 | `@Component` |
| common-sentry | `MetricsCollector` **SPI** | 指标采集（Micrometer/其他） | `@ConditionalOnMissingBean` |
| common-sentry | `AlertPublisher` **SPI** | 告警发布（PagerDuty/钉钉/企微） | `@ConditionalOnMissingBean` |
| common-sentry | `LogPublisher` **SPI** | 日志发布（ELK/Loki/Kafka） | `@ConditionalOnMissingBean` |
| common-sentry | `SlaCollector` **SPI** | SLA 指标计算 | `@ConditionalOnMissingBean` |
| common-sentry | `TraceContext` **SPI** | 链路追踪（SkyWalking/OpenTelemetry） | `@ConditionalOnMissingBean` |

### L6 应用层

| 子模块 | 扩展点接口 | 职责 | 注册方式 |
|---|---|---|---|
| common-base | `DocExporter` | 文档导出器 | `@Component` |
| common-base | `RequestIdResolver` | 请求 ID 解析 | `@ConditionalOnMissingBean` |
| common-web / common-app | （复用 base/auth/safe 扩展点） | — | — |

### 扩展点开发指引

业务方实现扩展点时的推荐步骤：

1. **选择扩展点**：在上表中找到需要扩展的接口，确认其注册方式（`@ConditionalOnMissingBean` 覆盖 vs `@Component` 多实现收集）。
2. **实现接口**：在业务模块中 `implements` 对应接口，使用 `@Component` 或 `@Service` 注解注册为 Bean。
3. **覆盖默认实现**（仅 `@ConditionalOnMissingBean` 场景）：业务方 Bean 自动覆盖默认实现，无需额外配置。
4. **多实现排序**：若接口支持 `Ordered` / `getOrder()`（如 `DataPermissionCustomSqlProvider` / `CellValueConverter`），通过 `getOrder()` 控制执行顺序。
5. **配置切换**（仅 `@ConditionalOnProperty` 场景）：在 `application.yml` 中通过配置项值切换实现，如 `ydsz.seata.tcc-log-store=redis`。
6. **避免循环依赖**：扩展点实现不应反向依赖 ydsz-common 的 `@Configuration` 装配类（参见 [ArchitectureTest](ydsz-common-app/src/test/java/com/njydsz/common/app/ArchitectureTest.java)）。

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
