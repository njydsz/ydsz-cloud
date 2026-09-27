# ydzs-common 全局引用深度分析报告

> 分析日期：2026-09-27 | 范围：ydsz-common 30 子模块 × 八大业务引擎 | 对标：Spring Cloud Alibaba / Netflix OSS / 美团 OCTO

---

## 一、执行摘要

| 维度 | 当前状态 | 行业对标差距 |
|------|---------|-------------|
| **全量复用** | 16/30 子模块被 8 引擎 100% 使用 | 达标，对标 Spring Boot Starter Core |
| **僵尸依赖** | **12 处 pom 声明但代码未使用** | 严重偏高，业界优秀实践 ≤2% |
| **能力复用不充分** | **≥8 个子模块**利用率 ≤3 引擎 | 偏低，中台能力推广不足 |
| **重复造轮子** | **≥11 处**手写替代方案 | 中等，密码/锁/缓存存在孤立实现 |
| **国际化覆盖** | **8/8 引擎缺失资源骨架文件** | 严重违规（违反 YDIZ-I18N-003）|
| **安全防护均匀度** | 6 个引擎的 @Xss/@SensitiveData 为零 | 防护网存在盲区 |

**核心结论**：ydsz-common 基础设施骨架已健全（16 个全量复用模块），但存在 **"隐性能力黑洞"** — 已建设的公共能力（i18n、Xss、分布式锁封装、缓存防击穿）因推广不足导致各引擎重复自研或弃不用。对标美团 OCTO "全公司基础设施全覆盖" 模式，当前 ydzs-common 从 "能力建设型" 进入 "深度治理期"。

---

## 二、ydzs-common 30 子模块能力清单

### 2.1 六层分级架构总览

| 层级 | 定位 | 数量 | 子模块 |
|------|------|------|--------|
| **L1 工具层** | 零外部依赖工具库 | 4 | `json` `util` `cache` `excel` |
| **L2 核心响应与基础设施** | 统一响应/分页/TraceId + 国际化基座 | 2 | `core` `locales` |
| **L3 领域基类** | DDD 基类/异常体系 | 2 | `domain` `exception` |
| **L4 数据基础** | 持久化增强 | 5 | `jdbc` `redis` `lock` `thread` `tenant` |
| **L5 业务服务** | 安全/认证/消息/事务/可观测 | 14 | `auth` `safe` `feign` `audit` `notify` `queue` `event` `config` `socket` `netty` `file` `docs` `search` `sentry` |
| **L6 应用基座** | Web/App 启动基类 | 3 | `base` `app` `web` |

### 2.2 各子模块关键能力

| 子模块 | 核心能力 | 关键 API |
|--------|---------|---------|
| `ydsz-common-json` | 自研高性能 JSON 引擎（类 Jackson 注解+树模型+Schema） | `JsonMapper`, `ObjectNode`, `@JsonClass`, `@JsonProperty` |
| `ydsz-common-util` | 工具库大全（字符串/加解密/ID/日期/集合/HTTP/Diff） | `StringUtils`, `MaskUtils`, `PwdUtils`, `SnowflakeIdGenerator`, `DiffCalculator` |
| `ydsz-common-cache` | 多级缓存（L1 Caffeine + L2 Redis）：TinyLFU/防击穿/指标 | `YdszCache`, `CacheProtectionGuard`, `WindowTinyLFUCache` |
| `ydsz-common-core` | 统一返回/结果码/请求上下文/Feature Flag | `YdszResponse<T>`, `PageResponse<T>`, `RequestContext`, `FeatureFlagService` |
| `ydsz-common-locales` | 国际化骨架（消息解析/动态切换/Strict 模式） | `I18nMessages`, `@EnableYdszI18n`, `MessageSourceHolder` |
| `ydsz-common-exception` | 统一异常体系（业务/系统/批量/脱敏/Otel） | `BusinessException`, `SysException`, `@ExceptionResultCode` |
| `ydsz-common-redis` | Redis 高级客户端（全数据结构 + 限流 + ID 生成） | `RedisStringOps`, `RedisRateLimiter`, `RedisIdGenerator` |
| `ydsz-common-lock` | 分布式锁全家桶（可重入/公平/读写/红锁 + WatchDog） | `@YdszDistributedLock`, `DistributedLocker`, `LockTemplate` |
| `ydsz-common-thread` | 线程池统一管理（虚拟线程/监控/报警/热更新） | `ThreadPoolRegistry`, `ExecutorUtils`, `ThreadPoolAlarmEvaluator` |
| `ydsz-common-safe` | 安全防护五件套（限流/熔断/幂等/CSRF/XSS/字段加密） | `@RateLimit`, `@Idempotent`, `@Xss`, `SensitiveUtils` |
| `ydsz-common-auth` | 统一 JWT/RBAC（令牌/黑名单/会话注册/CSRF/TOTP） | `JwtTokenService`, `RbacPermissionEvaluator`, `PermissionUtils` |
| `ydsz-common-queue` | 统一 MQ 抽象（多引擎：RLMQ/RocketMQ/Kafka/RabbitMQ + 死信） | `IMessageQueue`, `DeadLetterQueueService` |
| `ydsz-common-notify` | 多渠道通知（短信/邮件/钉钉/企微/飞书/站内信 + 去重/熔断） | `NotifyService`, `NotifyCircuitBreaker` |
| `ydsz-common-event` | 领域事件 + Outbox 模式 + Saga 编排 | `DomainEventPublisher`, `OutboxService`, `AbstractSaga` |
| `ydsz-common-tenant` | SaaS 多租户（上下文/数据源路由/Schema/缓存隔离） | `TenantContextHolder`, `TenantDataSourceRouter` |
| `ydsz-common-sentry` | 可观测性（OTel/SkyWalking trace + Loki 日志 + SLA + 告警） | `SentryFacade`, `SentryObservation`, `@SlaDefinition` |
| `ydsz-common-file` | 多云存储统一抽象（S3/OSS/COS/MinIO + 秒传/毒扫） | `IFileStorage`, `FileDedupService`, `VirusScanner` |
| `ydsz-common-search` | 统一搜索服务层（PG/内存/ES + 中文分词 + 熔断） | `UnifiedSearchService`, `SearchProvider`, `ChineseTokenizer` |
| `ydsz-common-feign` | OpenFeign 统一增强（签名/容错/限流/追踪/压缩） | `FeignCircuitBreakerGuard`, `FeignRequestInterceptor` |
| `ydsz-common-socket` | WebSocket 实时推送（集群/心跳/SSE/ACL/限流） | `RealtimePushTemplate`, `SsePushChannel`, `OnlineUserService` |
| `ydsz-common-netty` | Netty 深度定制（TCP/编解码/RPC/零拷贝/SSL） | `AbstractNettyServer`, `NettyRpcClient` |
| `ydsz-common-audit` | 操作日志审计（注解驱动/变更快照/分表/脱敏） | `@Audit`, `AuditRecorder`, `AuditQueryService` |
| `ydsz-common-config` | 配置热更新桥接（变更监听/审计/CLI） | `ConfigChangeBridge`, `ConfigChangeListener` |
| `ydsz-common-docs` | 多格式文档处理（PDF/Word/Excel/PPT + OCR/水印/PII） | `DocumentService`, `PiiDetector`, `OcrEngine` |
| `ydsz-common-domain` | DDD 基础（分页查询/数据权限/树形/规格模式） | `PageQuery`, `BaseQuery`, `TreeBuilder`, `Specification` |
| `ydsz-common-base` | 应用基座（健康检查/安全头/追踪/时区/CORS/文档） | `YdszAutoConfiguration`, `AbstractGlobalResponseAdvice` |
| `ydsz-common-web` | Web 全局配置（响应包装/认证/Webhook/Session） | `GlobalResponseAdvice`, `WebAuthFilter`, `WebhookDispatcher` |
| `ydsz-common-app` | 移动端 App 独立入口（与 web 平行隔离） | `AppGlobalResponseAdvice`, `AppAuthFilter` |

---

## 三、八大引擎引用热度矩阵

### 3.1 全量引用矩阵（行=引擎，列=30 子模块）

| 引擎 | json | cache | excel | util | core | locales | domain | exception | jdbc | redis | lock | thread | tenant | auth | safe | feign | audit | notify | queue | event | config | socket | netty | file | docs | search | sentry | base | app | web |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **system** | ✅ | ✅ | ✅ | ✅ | ✅ | 🔶 | ✅ | ✅ | ✅ | ✅ | ✅ | ⭕ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ⭕ |
| **userinfo** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ⭕ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ⭕ |
| **message** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ⭕ |
| **workflow** | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ⭕ | ⭕ |
| **cronjob** | ✅ | ✅ | ✅ | ✅ | ✅ | 🔶 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ⭕ |
| **nextwiki** | ✅ | ✅ | ❌ | ✅ | ✅ | 🔶 | ✅ | ✅ | ✅ | ✅ | ✅ | ⭕ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **literule** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ⭕ |
| **agent** | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ⭕ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ⭕ |

> **图例**：✅=pom+import 均确认 | ⭕=pom 声明但代码未使用（僵尸）| 🔶=代码 import 但 pom 未声明（隐式传递）| ❌=未引用

### 3.2 引用热度排名

| 排名 | 子模块 | Pom 声明引擎数 | 代码 import 引擎数 | 僵尸 pom | 仅传递依赖 | 热度评级 |
|------|--------|--------------|------------------|---------|-----------|---------|
| 1-16 | core/util/safe/base/audit/jdbc/auth/lock/sentry/event/json/redis/exception/cache/domain/feign | 8 | 8 | 0 | 0 | 🔥 全量 |
| 17 | search | 7 | 7 | 0 | 0 | 🟢 高频 |
| 18 | app | 8 | 7 | 1 | 0 | 🟢 高频 |
| 19 | thread | 8 | 5 | 3 | 0 | 🟡 中频 |
| 20 | excel | 5 | 5 | 0 | 0 | 🟡 中频 |
| 21 | notify/socket/queue | 4 | 4 | 0 | 0 | 🟠 低频 |
| 24 | **locales** | **1** | **4** | 0 | **3** | 🔴 **低热度 + 传递依赖** |
| 25 | tenant/config | 3 | 3 | 0 | 0 | 🔴 低热度 |
| 27 | file/netty/docs | 2-3 | 2 | 0-1 | 0 | 🔴 低热度 |
| 30 | **web** | **8** | **1** | **7** | 0 | 🔴 **最大僵尸 + 最低热度** |

---

## 四、核心问题诊断

### 4.1 僵尸依赖（Zombie Dependencies）— 12 处

> 对标 Azure SDK / Spring Boot Starter 设计：每个 Starter 只声明并被需要的模块使用。Netflix OSS 通过 ArchUnit 自动检测并强制清理僵尸依赖。

| 子模块 | 僵尸数 | 僵尸引擎 | 风险等级 |
|--------|--------|---------|---------|
| `ydsz-common-web` | **7** | system, userinfo, message, workflow, cronjob, literule, agent | 🔴 **高危** |
| `ydsz-common-thread` | **3** | system, userinfo, nextwiki | 🟡 中 |
| `ydsz-common-app` | 1 | workflow | 🟢 低 |
| `ydsz-common-netty` | 1 | agent | 🟢 低 |

**问题本质**：`ydsz-common-web` 8 个引擎全部 pom 声明但仅 nextwiki 实际使用。这种"全家桶式"依赖声明是反模式 —— 每次 common-web 升级都会触发 8 个引擎的重新构建验证窗口。对标 Azure SDK 的 "按需引用、独立演进" 模式，过度声明会降低交付效率。

### 4.2 传递依赖但未显式声明 — 1 子模块 3 引擎

| 子模块 | 隐性依赖引擎 | 传递路径 |
|--------|------------|---------|
| `ydsz-common-locales` | system, cronjob, nextwiki | 通过 util → locales 传递 |

**风险**：pom 中未显式声明但代码直接 import，一旦传递链路变更（如 util 移除 locales 依赖）将导致编译失败。

### 4.3 重复造轮子（11+ 处）

> 对标美团 OCTO 设计哲学："策略下沉"、"组件复用，不重复造轮子"。OCTO 将服务治理策略下沉到代理组件，避免各业务线重复实现。

| 序号 | 引擎 | 文件/位置 | 重复能力 | common 替代方案 | 严重度 |
|------|------|---------|---------|---------------|--------|
| 1 | userinfo | `OAuth2ApplicationService.java` | 直接注入 BCryptPasswordEncoder encode/matches | `PwdUtils` (强度固定 12，OWASP 推荐) | 🔴 高 |
| 2 | userinfo | `ProvisionOrchestrator.java:191` | `new BCryptPasswordEncoder().encode("Init@" + randomPart)` | `PwdUtils.encode()` | 🔴 高 |
| 3 | 7 引擎共 13 处 | 多模块 `@Cacheable` Spring 原生注解 | Spring Cache Abstraction | `@YdszCacheable` (TinyLFU + 防击穿 + 多级) | 🟡 中 |
| 4 | 6 引擎共 12 处 | `setIfAbsent()` / `tryLock()` 手写 Redis 锁 | `DistributedLocker` / `@YdszDistributedLock` (WatchDog/红锁/降级) | 🟡 中 |
| 5 | message/literule/agent | Resilience4j 直接使用（3 文件） | `SafeCircuitBreaker` / `@RateLimit`（已集成熔断+指标+多策略） | 🟡 中 |
| 6 | system | `WebAuthFilter` 内置 XSS 检查（手写 Jsoup.clean?） | `XssFilter` + `XssValidator` + `OwaspXssCleaner` | 🟡 中 |

### 4.4 国际化（i18n）能力 — 全体沦陷

> 规则 YDIZ-I18N-003 明确要求"业务模块必须创建 i18n 资源文件骨架（≥5 key），禁止零资源文件部署"。

| 引擎 | properties 文件骨架 | I18nMessages 使用文件数 | 合规状态 |
|------|--------------------|----------------------|---------|
| system | **0** | 1 | ❌ 仅传递使用，无骨架 |
| userinfo | **0** | **0** | ❌ 完全空白 |
| message | **0** | **0** | ❌ 完全空白 |
| workflow | **0** | **0** | ❌ 完全空白 |
| cronjob | **0** | 1 | ❌ 仅传递使用，无骨架 |
| nextwiki | **0** | 1 | ❌ 仅传递使用，无骨架 |
| literule | **0** | **0** | ❌ 完全空白 |
| agent | **0** | **18** | ⚠️ 代码有调用但无骨架（运行时 fallback 到默认值）|

**影响**：
- **rule 违规**：8/8 引擎违反自研规则 YDIZ-I18N-003
- **产品风险**：所有面向用户的错误提示、弹窗文案均为**硬编码中文**，无法多语言化
- **海外扩展阻碍**：对标美团海外产品（Keeta 等）依赖的 i18n 基座，当前完全不具备出海基础

### 4.5 安全防护能力 — 覆盖盲区

> 对标 Spring Cloud Alibaba Sentinel 的"全链路防护"理念：限流/熔断/XSS/脱敏应覆盖每个服务入口。

#### 4.5.1 限流与幂等（覆盖良好）

| 引擎 | @RateLimit | @Idempotent | 评价 |
|------|-----------|------------|------|
| system | 15 | 11 | ✅ 充分 |
| userinfo | 19 | 12 | ✅ 充分 |
| message | 18 | 18 | ✅ 充分 |
| workflow | 13 | 14 | ✅ 充分 |
| cronjob | 14 | 14 | ✅ 充分 |
| nextwiki | 4 | 16 | ⚠️ 限流偏少 |
| literule | 17 | 15 | ✅ 充分 |
| agent | 8 | 10 | ✅ 充分 |

#### 4.5.2 XSS 防护（存在 4 引擎盲区）

| 引擎 | @Xss 使用文件数 | 状态 |
|------|----------------|------|
| message | 29 | ✅ 标杆 |
| userinfo | 9 | ✅ |
| system | 7 | ✅ |
| workflow | 2 | ⚠️ |
| **cronjob** | **0** | ❌ 完全盲区 |
| **nextwiki** | **0** | ❌ 完全盲区 |
| **literule** | **0** | ❌ 完全盲区 |
| **agent** | **0** | ❌ 完全盲区 |

**风险**：4 个引擎的 Controller 入参未做任何 XSS 过滤，攻击者可注入 `<script>` 标签或事件处理器。

#### 4.5.3 敏感数据脱敏（6 引擎盲区）

| 引擎 | @SensitiveData / @SensitiveOperation | 状态 |
|------|------------------------------------|------|
| workflow | 10 / 0 | ✅ 仅注解 |
| userinfo | 3 / 3 | ✅ |
| **system** | **0 / 0** | ❌ 完全盲区 |
| **message** | **0 / 0** | ❌ 完全盲区（消息内容含手机号/邮箱！）|
| **cronjob** | **0 / 0** | ❌ |
| **nextwiki** | **0 / 0** | ❌ |
| **literule** | **0 / 0** | ❌ |
| **agent** | **0 / 0** | ❌ |

**风险**：message 引擎（含手机号/邮箱/身份信息标识的消息推送日志）完全没有脱敏注解，是 PII 泄露高危区。

### 4.6 分布式锁与缓存 — 封装降级

#### 4.6.1 分布式锁使用率

| 引擎 | 定时任务数 | @YdszDistributedLock 使用率 | 手写锁（setIfAbsent/tryLock） | 评价 |
|------|----------|--------------------------|---------------------------|------|
| workflow | 10 | 13 | 1 | ✅ best |
| message | 7 | 6 | 2 | ✅ good |
| cronjob | 18 | 6 | 2 | ⚠️ 仅 33% |
| nextwiki | 4 | 2 | 2 | ⚠️ 50% |
| **system** | 2 | **0** | **0** | ❌ 未使用 |
| **agent** | 4 | **0** | 1 | ❌ 未使用 |
| userinfo | 4 | 1 | 3 | ⚠️ 25% |
| literule | 2 | 1 | 2 | ⚠️ 50% |

**问题**：
- system 和 agent 引擎**完全没有使用封装锁**，定时任务存在集群并发执行风险
- 6 个引擎存在手写 Redis 锁（`setIfAbsent/tryLock`），**缺少 WatchDog 续期、红锁降级、异常处理标准化**

#### 4.6.2 缓存防击穿能力

| 引擎 | Spring @Cacheable | @YdszCacheable | CacheProtectionGuard |
|------|------------------|------------|---------------------|
| system | **8** | **0** | **0** |
| workflow | 2 | 0 | 0 |
| nextwiki | 3 | 0 | 0 |
| agent | 0 | 0 | **1** |

**问题**：
- **系统引擎**有 8 处 Spring Cacheable 但零使用 CacheProtectionGuard — 这是缓存击穿高危场景
- 多级缓存（L1 Caffeine + L2 Redis）能力完全未启用，对标 Caffeine + Redis 多级缓存的行业最佳实践

### 4.7 隐式传递依赖 — 架构脆弱点

| 子模块 | 隐性依赖引擎 | 传递链路 | 消除方案 |
|--------|------------|---------|---------|
| `locales` | system, cronjob, nextwiki | 通过 util → locales 传递 | 在 system/cronjob/nextwiki 的 server/web 层 pom 中显式声明 locales 依赖 |
| `core` | 部分引擎（通过 web 传递） | web → core | 在直接使用 core 的模块中显式声明 |

---

## 五、行业对标分析

### 5.1 对标框架体系

| 对标体系 | ydzsz-common 对应模块 | 对标状态 |
|---------|---------------------|---------|
| **Spring Boot Starter Core** | `base` `core` `util` | ✅ 能力对标 |
| **Spring Cloud Netflix** (Eureka/Ribbon/Hystrix) | `feign` `safe` (CircuitBreaker) | ✅ 对标 + 增强（WatchDog、红锁） |
| **Spring Cloud Alibaba 三驾马车** | | |
| ↳ Nacos (注册+配置) | `config` (ConfigChangeBridge) | ⚠️ 仅变更监听，缺服务注册 |
| ↳ Sentinel (限流降级) | `safe` (RateLimit+CircuitBreaker) | ✅ 能力对标 |
| ↳ Seata (分布式事务) | `event` (Outbox + Saga) | ✅ 模式对标 |
| **美团 OCTO** (服务治理) | `feign` `sentry` `config` | ⚠️ 部分对标，缺服务注册发现 |
| **Redisson** (分布式锁) | `lock` | ✅ 对标 + 红锁增强 |
| **Caffeine + Redis** (多级缓存) | `cache` | ✅ 对标 + TinyLFU + 防击穿 |
| **Spring Security** (认证) | `auth` `safe` | ✅ 对标 + TOTP + 黑名单 |
| **Apache RocketMQ** | `queue` | ⚠️ 抽象层对标，待验证死信可靠性 |
| **阿里巴巴 FastJSON / Jackson** | `json` | ✅ 自研高性能对标 |
| **Elasticsearch + IK** | `search` | ⚠️ 策略层对标，缺向量检索 |
| **MinIO / 阿里云 OSS** | `file` | ✅ 多云统一 |
| **OpenTelemetry + Prometheus** | `sentry` | ✅ 可观测性对标 |
| **Azure SDK Shared Core** | `base` `core` | ⚠️ 缺少 Java 模块化 OSGi 级隔离经验 |

### 5.2 差距分析

| 维度 | 美团 OCTO / Spring Alibaba 实践 | ydzs-common 现状 | 差距 |
|------|-------------------------------|-----------------|------|
| **依赖治理** | ArchUnit + CI 门禁自动检测僵尸依赖 | 无自动化检测，靠人工发现 | 高 |
| **能力覆盖率** | 95%+ 业务使用自研中间件 | i18n 覆盖 0/8，Xss 覆盖 4/8 | 高 |
| **安全基线** | Sentinel 全局限流 + SOP 应急处置 | 限流覆盖良好但 Xss/脱敏有盲区 | 中 |
| **配置治理** | Nacos/MCC 统一配置 + 灰度发布 | ConfigChangeBridge 仅变更监听 | 低 |
| **可观测** | Watt 数据中心万亿级 | Sentry 基础 trace + metrics | 中 |
| **国际化** | 出海产品成熟的 i18n 骨架 | **0/8 引擎有骨架文件** | 严重 |

---

## 六、优化建议路线图

### 🔴 P0 阻断级 — 立即执行

#### P0-1：清理僵尸依赖（12 处 → 0）

**目标**：消除所有 pom 声明但代码零使用的子模块依赖。

| 操作 | 涉及引擎 | 工作量 |
|------|---------|--------|
| 移除 `ydsz-common-web` 依赖 | system, userinfo, message, workflow, cronjob, literule, agent (7 engine×子模块) | 低 |
| 移除 `ydsz-common-thread` 依赖 | system, userinfo, nextwiki | 低 |
| 移除 `ydsz-common-app` 依赖 | workflow | 极低 |
| 移除 `ydsz-common-netty` 依赖 | agent | 极低 |

**落地方式**：编写 ArchUnit 测试，自动化检测并阻止新增僵尸依赖：
```java
@ArchTest
static final ArchRule no_common_web_for_non_web_engines =
    noClasses().that().resideInAPackage("com.njydsz.system..")
        .should().dependOnClassesThat().resideInAPackage("com.njydsz.common.web..");
```

#### P0-2：国际化骨架强制落地（8 引擎 → 8 引擎）

**目标**：所有业务引擎必须创建 i18n 资源骨架（≥5 key）。

| 引擎 | 新增文件 | 建议最低 key |
|------|---------|------------|
| system | `messages_zh_CN.properties` + `messages_en_US.properties` | 参数操作成功/失败、租户不存在、字典缺失 |
| userinfo | 同上 | 用户不存在/已存在/锁定、密码强度不足、登录失败 |
| message | 同上 | 渠道不可用/超限、模板不存在、发送失败、幂等抑制 |
| workflow | 同上 | 流程不存在/已结束、节点不存在、权限不足、版本冲突 |
| cronjob | 同上 | 任务不存在/已禁用、执行超时、分片失败、并发重入拒绝 |
| nextwiki | 同上 | 文件不存在/已删除、空间超限、版本冲突、权限不足 |
| literule | 同上 | 规则表达式非法、表不存在、A/B 冲突、热加载失败 |
| agent | 同上 | 模型不可用、Token 超限、沙箱超时、MCP 工具未找到 |

**落地方式**：
1. 立即补齐 14 个 properties 文件（每引擎 zh_CN + en_US）
2. 或仅创建 key 占位文件 + 在 CI 中添加 YDIZ-I18N-003 检测逻辑
3. Controller 中所有 `Result.ok("中文")` 或 `throw new XxxException("中文")` 改为 `I18nMessages.get("error_code")`

#### P0-3：消除手写分布式锁（6 引擎 12 处 → 0）

**替换清单**：

| 引擎 | 文件 | 当前模式 | 目标模式 |
|------|------|---------|---------|
| userinfo | 3 files | `setIfAbsent("lock:")` + `expire()` | `@YdszDistributedLock(key = "...")` |
| message | 2 files | `tryLock()` + 手动 `release()` | ` DistributedLocker` |
| cronjob | 2 files | 手写 | LockTemplate |
| nextwiki | 2 files | 手写 | @YdszDistributedLock |
| literule | 2 files | 手写 | @YdszDistributedLock |
| agent | 1 file | 手写 | @YdszDistributedLock |
| workflow | 1 file | 手写 | @YdszDistributedLock |

**落地收益**：统一 WatchDog 续期、标准异常处理、降级回调、LockWaitStats 指标。

### 🟡 P1 严格级 — 本迭代内完成

#### P1-1：Spring Cache → YdszCacheable 升级

| 引擎 | Spring @Cacheable 数量 | 迁移优先级 |
|------|----------------------|----------|
| system | **8** | 🔴 最高（涉及系统参数/字典热点数据）|
| nextwiki | 3 | 🟡 中（文件元数据）|
| workflow | 2 | 🟡 中（流程定义缓存）|

#### P1-2：Resilience4j → SafeCircuitBreaker 统一

| 引擎 | 文件 | 操作 |
|------|------|------|
| message | 2 files using resilience4j | 替换为 `SafeCircuitBreaker` |
| literule | 1 file using resilience4j | 替换 |
| agent | 1 file using resilience4j | 替换 |

#### P1-3：密码哈希统一（userinfo 2 处 → PwdUtils）

将 `OAuth2ApplicationService` 和 `ProvisionOrchestrator` 中的 `BCryptPasswordEncoder` 替换为 `PwdUtils`。

#### P1-4：敏感数据脱敏补盲（message 引擎 — PII 高危险区）

message 引擎承载手机号/邮箱/身份信息的推送链路：
- 在 MessageLog DTO 导出/响应脱敏中使用 `@SensitiveData` + `SensitiveUtils`
- Controller 入参标记 `@SensitiveData(level = SensitiveLevel.HIGH)` 的日志脱敏

#### P1-5：隐式依赖显式化

| 引擎 | 操作 |
|------|------|
| system | pom 显式声明 `ydsz-common-locales` |
| cronjob | pom 显式声明 `ydsz-common-locales` |
| nextwiki | pom 显式声明 `ydsz-common-locales` |

### 🟢 P2 改进级 — 规划与渐进

#### P2-1：XSS 防护盲区补全

| 当前零 @Xss 引擎 | 操作 |
|----------------|------|
| cronjob | Controller 入参添加 `@Xss` |
| nextwiki | Controller 入参添加 `@Xss` |
| literule | 规则表达式输入点添加 `@Xss` |

#### P2-2：安全防护规则库建设

参考美团 Rhino 熔断限流体系的 SOP 策略：
- 建立 Sentinel/安全事件 → 自动限流降级策略映射
- common-safe 联动 Sentry 实现安全事件告警闭环

#### P2-3：依赖治理 CI 门禁

类似 Azure SDK 的依赖管理自动化：
1. ArchUnit 测试：禁止业务模块 import 不到任何 common 子模块 public API 但 pom 声明了该依赖（僵尸检测）
2. Maven Enforce Plugin：禁止直接 import Resilience4j / Guava RateLimiter / BCryptPasswordEncoder（强制走 common 封装）
3. 每次 PR 自动生成引用热图变化报告

#### P2-4：低热度模块推广评估

对被 ≤2 个引擎使用的模块评估推广价值：

| 子模块 | 当前使用引擎 | 潜在推广方向 |
|--------|------------|------------|
| `ydsz-common-netty` | message, nextwiki | 评估其他引擎 TCP 长连接需求 |
| `ydsz-common-file` | system, nextwiki | 推广到所有需文件上传的引擎 |
| `ydsz-common-docs` | nextwiki, agent | 各引擎 OpenAPI/文档导出统一 |
| `ydsz-common-config` | system, userinfo, literule | 推广到所有需热更新配置的引擎 |

#### P2-5：i18n 全量治理（硬编码中文 → I18n）

- 使用 MissingTranslationLogger 发现运行时缺失翻译
- CI 门禁：扫描 new/changed Java 文件中的硬编码中文字符串
- 工具化：IDE 插件自动提取 → 替换为 `I18nMessages.get("key")` 并自动写入 properties

---

## 七、量化目标与度量指标

| 指标 | 当前 | 30 天目标 | 90 天目标 |
|------|------|----------|----------|
| 僵尸依赖数 | **12** | 0 | 0 + CI 门禁 |
| i18n 骨架引擎数 | **0/8** | **8/8** | 8/8 + 硬编码中文清零 |
| 手写分布式锁 | **12** | ≤3 | 0 |
| @Xss 覆盖引擎 | **4/8** | **6/8** | **8/8** |
| @SensitiveData 使用引擎 | **2/8** | **4/8** | **8/8** |
| Spring @Cacheable | **13** | ≤5 | 0（全部迁移 ydszCacheable）|
| Resilience4j 直接使用 | **3** | 0 | 0 |
| BCrypt 直接使用 | **2** | 0 | 0 |

---

## 八、总结

ydsz-common 30 个子模块中 **16 个实现 100% 全量复用**，证明基础设施骨架已经成熟。当前面临的核心矛盾不是"能力缺失"，而是**"能力已建但推广不充分"**：

1. **i18n 8/8 引擎无骨架** — 最严重的既有规则违规
2. **僵尸依赖 12 处** — CI 门禁可自动化解决
3. **手写锁 + Spring Cache + Resilience4j 并行** — 标准封装推广不足
4. **安全防护存在盲区** — Xss/脱敏覆盖不均衡

对标行业：美团 OCTO 通过"策略下沉"思想将服务治理下沉到代理组件；Spring Cloud Alibaba 通过"一站式"依赖让开发者无感使用中间件。ydsz-common 已具备同等基础设施能力，下一步从 **"能力建设"** 转向 **"深度治理与全面覆盖"**。

核心原则：**先僵尸清理 → 再安全补盲 → 再 i18n 覆盖 → 再工具统一**。30 天内完成 P0 + P1，90 天达到全面合规。
