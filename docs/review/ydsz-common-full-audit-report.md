# ydsz-common 框架全面审计与优化建议报告

**审计日期**: 2026-08-09  
**审计范围**: ydsz-common 全部 29 个子模块（core/util/base/web/app/auth/safe/audit/tenant/jdbc/cache/redis/search/seata/feign/socket/netty/notify/queue/event/json/excel/file/config/lock/thread/sentry/exception/domain/docs）  
**对标对象**: RuoYi-Vue / Jeecg-Boot / Guns / Jmix / Spring Boot 最佳实践 / 阿里 Java 开发手册  
**分析方法**: 源代码静态分析 + 依赖树分析 + 业界规范对照 + 竞品对标

---

## 执行摘要

ydsz-common 框架整体代码质量在企业自研框架中处于**中上水平**，架构分层清晰，安全体系完善，API 风格一致性较好。29 个模块共覆盖了认证、鉴权、审计、多租户、数据访问、缓存、搜索、分布式事务、RPC、WebSocket、Netty、通知、消息队列、事件驱动、JSON、Excel、文件存储、分布式锁、线程池、监控、异常处理、领域模型等全链路能力。

**本轮审计发现**：
- P0（高危）: 4 项 — JSON DoS 栈溢出、父类字段丢失、Excel 窗口硬编码、文档安全风险
- P1（重要）: 28 项 — 双锁实现重复、多端互踢缺失、SSRF 防护缺失、堆栈脱敏缺失等
- P2（改进）: 35 项 — 配置文档缺失、测试覆盖不足、可观测性增强等

---

## 一、架构优化

### 1.1 模块职责收敛

**现状**: ydsz-common-docs 模块能力蔓延严重，集 PDF/Word/Excel/HTML/CSV/Markdown/PPT 解析、OCR、PII 检测、安全脱敏、水印、摘要、关键词提取等 10+ 项职责于一身。

**对标**: Jmix 框架将文档处理、报表、BPMN 分别作为独立 starter；Spring AI 将文档解析与 AI 能力解耦。

**建议**:
1. **拆分模块**: 将 OCR、水印、AI 摘要拆分为独立模块 `ydsz-common-ocr` / `ydsz-common-watermark` / `ydsz-common-ai`
2. **明确边界**: docs 模块只保留"解析 + 安全检查"两个核心能力
3. **SPI 优先**: 将现有 DocumentService 改为 Facade → SPI Provider 模式，避免 SPI 与实现同模块导致的耦合

### 1.2 消除重复实现

**现状**: ydsz-common-redis 的 `RedisCacheGuard` 与 ydsz-common-lock 的 `LockWatchDog` 存在以下重复：
- WatchDog 续期调度逻辑完全重复（续期间隔 = leaseTime/3，最大续期 100 次）
- 锁释放 Lua 脚本结构高度重复（compare-and-delete 模式）
- 续期失败处理策略重复

**对标**: Redisson 通过单一的 `org.redisson.RedissonLock` 统一处理锁持有、续期、释放，不存在重复。

**建议**:
1. 引入 `LockRenewalService` SPI 接口，由 lock 模块实现
2. 改造 `RedisCacheGuard` 通过适配器委托给 lock 模块
3. 提取公共 Lua 脚本常量 `LockLuaScripts`（类似 Spring 的 `RedisScript`）

### 1.3 异常体系瘦身

**现状**: `AbstractYdszException` 残留大量 `@Deprecated` 的 setter 方法（`setCode`/`setKey`/`setParams`），以及已废弃的 `MESSAGE_RESOLVER_HOLDER`（v2.0 已迁移到 Handler 层解析）。

**对标**: Spring 的 `NestedRuntimeException` 基于组合而非继承；Resilience4j 的异常体系严格遵循"一个根类 + 三个子类"。

**建议**:
1. 清理所有 `@Deprecated` setter，明确告知业务方使用 Builder 构造异常
2. 删除 `MESSAGE_RESOLVER_HOLDER`，统一走 Handler 层 i18n 解析
3. `BusinessException` 与 `SysException` 拆分为独立类（不再共享 AbstractYdszException），语义更清晰

### 1.4 领域模型补完

**现状**: `BaseEntity<T>` 使用裸类型主键，缺少 DDD 核心原语（TypedId、Specification、ValueObject、AggregateRoot）。

**对标**: Axon Framework 提供完整的 `Identifier` + `AggregateMarker` + `Specification` 体系。

**建议**:
1. 引入 `TypedId<T>` 值对象（`record TypedId<T>(T value)`），类型安全地包装主键
2. 增加 `Specification<T>` 接口 + 组合器（`and`/`or`/`not`），为规约模式提供基础
3. 增加 `DomainEventPublisher` SPI，支持同步/异步/延迟发布策略
4. `DomainEvent` 注解的 `delaySeconds` 和 `async` 属性需要配套调度器实现

### 1.5 数据源路由器完善

**现状**: `TenantDataSourceRouter.resolveDatasourceKey` 方法存在 TODO 注释，当前为约定模式（tenantId → datasource），未实现从 `ydsz_tenant` 表动态加载数据源映射。

**对标**: dynamic-datasource-spring-boot-starter 支持任意维度（租户/分片/读写）的动态注册。

**建议**:
1. 实现 `TenantDataSourceProvider` SPI，从数据库加载 tenant → datasource 映射
2. 增加租户数据源变更的热更新机制（监听 Nacos 配置变更）
3. 增加注册时的连接可用性探测

---

## 二、功能增强

### 2.1 多端登录互踢

**现状**: JWT 双令牌机制完善，但未实现 PC 端/移动端/单设备互踢、并发会话控制等功能。

**对标**: CAS 框架的 "maxSessionsPerUser" 策略；Spring Session 的并发会话管理。

**建议**:
1. 增加 `max-sessions-per-user` 配置，控制单用户最大并发会话数
2. 增加 `session-strategy` 配置（SINGLE_ANY / SINGLE_DEVICE / MULTI）
3. Session 控制通过 Redis Hash 存储 `user:{userId}:sessions` → `sessionId:deviceInfo`
4. 被互踢时推送 WebSocket 通知给被踢客户端

### 2.2 SSRF 防护

**现状**: 项目未见 URL 白名单校验代码，`RestTemplate`/`OkHttpClient` 可能被诱导访问内网 IP。

**对标**: Spring 的 `@Value` 注入不会做 URL 校验，需业务层自行防护；阿里 Java 开发手册明确要求 SSRF 防护。

**建议**:
1. 增加 `HttpConnectionValidator`，禁止访问以下目标:
   - 内网 IP 段（10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16）
   - 链路本地地址（169.254.0.0/16）
   - metadata 服务（169.254.169.254）
   - localhost / 127.0.0.1 / [::1]
2. 对外部 URL 域名做白名单正则匹配
3. Spring Boot 4.x 可通过 `RestTemplateBuilder.additionalCustomizers()` 注入自定义 `InterceptingClientHttpRequestFactory`

### 2.3 配置安全与版本管理

**现状**: 配置加密委托 Jasypt 合理，但缺少配置优先级矩阵文档、灰度发布能力、版本管理能力。

**对标**: Nacos 配置中心提供灰度/IP 发布；Apollo 提供版本对比、回滚、灰度规则。

**建议**:
1. 补充开源文档说明配置优先级矩阵:
   - 系统环境变量 > Nacos 远程配置 > 本地 `application-{profile}.yml` > 本地 `application.yml`
2. 封装 Nacos `GrayConfig` 支持按 IP/百分比分流
3. 增加 `ConfigVersionController` 暴露历史版本查询、版本回滚 API

### 2.4 文档水印自动化

**现状**: `WatermarkProvider` SPI 已存在但未在文档下载链路中自动注入。

**对标**: 金山 WPS 云文档在每份文档自动叠加下载者身份水印；微软 Office 365 支持 IRM（Information Rights Management）。

**建议**:
1. 在 `DocumentService.parseAndRedact` 完成后自动调用 `WatermarkProvider`
2. 默认实现 PDF/Excel/图片格式的水印叠加（用户名 + IP + 时间戳）
3. 支持不可见水印（隐式信息嵌入），用于泄露溯源

### 2.5 审计存储扩展

**现状**: Disruptor 异步审计仅支持 DB 存储，大数据量场景下 DB 写入可能成为瓶颈。

**对标**: 阿里云操作审计（ActionTrail）投递到 SLS + MaxCompute；AWS CloudTrail 投递到 S3 + Athena。

**建议**:
1. 增加 `AuditEventPublisher` SPI，支持 Kafka / RocketMQ / ES 作为审计数据的异步投递通道
2. DB 存储作为"最近 7 热数据"，过期后自动归档至 ES
3. 提供 `AuditQueryService` 支持跨存储的联合查询

---

## 三、性能提升

### 3.1 读写分离延迟感知

**现状**: `ReadWriteSplittingInterceptor` 仅根据 SQL 类型（SELECT → 从库，DML → 主库）路由，未检测从库复制延迟。

**对标**: ShardingSphere 提供 `read-write-splitting-static` 与 `read-write-splitting-dynamic` 两种策略，后者支持延迟感知。

**建议**:
1. 增加 `ReplicationLagAwareStrategy`，定时探测从库 `Seconds_Behind_Master`（MySQL）或 `pg_last_xact_replay_timestamp`（PostgreSQL）
2. 延迟超阈值时自动将"延迟敏感型"查询路由至主库
3. 对延迟标记（`@FreshQuery` 注解）的查询强制走主库

### 3.2 JSON 性能与安全加固

**现状**: 自研 JSON 库（零依赖，纯手写），存在 DoS 栈溢出、父类字段丢失等 P0 问题，且无 JMH 性能基线。

**对标**: Jackson 是当前事实标准；FastJSON2 已解决安全性和兼容性问题；DSL-JSON 在性能上领先 Jackson 5-10 倍。

**建议**:
1. **修复 DoS**: 在 `JSONReader.readObjectMap/readArray` 递归入口传入 `depth` 计数器，超 `maxDepth`（默认 32）立即抛 `JsonParseException`
2. **修复父类字段**: `FieldMetadataLoader` 沿 `getSuperclass()` 递归收集至 `Object.class`
3. **加 JMH 基线**: 与 Jackson / FastJSON2 / Gson 做 round-trip 性能对比，明确自研库的适用场景和劣势
4. **XSS 防护**: JSONWriter 增加 `ESCAPE_FOR_JS` 模式转义 U+2028/U+2029

### 3.3 多级缓存热 Key 优化

**现状**: 已有 `HotKeyTracker` 和 `DistributedRebuildLock`，但热 Key 的二级缓存回填无随机过期策略，可能集体过期导致雪崩。

**对标**: Squirrel（美团分布式缓存）有内部热 Key 探测和强制 L1 缓存能力；阿里云 Tair 提供热点 Key 自动发现。

**建议**:
1. 热 Key 检测后自动将其 TTL 延长至基准值的 2-3 倍
2. 增加 `local-cache-ttl-multiplier` 配置
3. 热 Key 更新时广播失效本地缓存时增加随机抖动（±10%）

### 3.4 ES 客户端降级策略精细化

**现状**: `ElasticsearchSearchStrategy` 通过 REST API 交互，客户端不在 classpath 时降级到内存搜索模式（最多 10000 条）。

**对标**: Spring Data Elasticsearch 提供 `RestClient` 和 `ReactiveClient` 双客户端，降级时抛异常而非降级到不可靠模式。

**建议**:
1. 降级策略从"内存搜索"改为"抛出 SearchServiceUnavailableException"
2. 增加 `ydsz.search.fallback-to-null: true` 配置，允许搜索失败时返回 null 而非抛异常
3. 内存搜索上限 10000 偏死板，建议可配置
4. 增加搜索结果的缓存时间配置（搜索结果的短 TTL 缓存）

### 3.5 TCC 事务日志持久化

**现状**: TCC 事务日志默认 `memory`（`InMemoryTccTransactionLogStore`），生产推荐 `redis`。

**对标**: Seata 官方支持将 TCC 日志存储到数据库；阿里 TXC 模式将事务日志存储在本地文件。

**建议**:
1. 增加 `DbTccTransactionLogStore` 实现（基于 PostgreSQL / MySQL）
2. 支持事务日志的分布式锁（Redis）与本地持久化（DB）双写
3. `TccTransactionRecoveryScanner` 的扫描间隔可配置

---

## 四、体验改善

### 4.1 编码过滤器补充

**现状**: 未发现 `CharacterEncodingFilter`，依赖 Spring Boot 默认配置。

**对标**: Spring Boot 2.7+ 默认已设置 UTF-8，但显式配置可避免歧义。

**建议**:
1. 增加 `CharacterEncodingFilter`，强制 `request/response` 均为 UTF-8
2. 放在过滤器链最前端（`@Order(Ordered.HIGHEST_PRECEDENCE)`）

### 4.2 异常堆栈脱敏

**现状**: 全局异常处理中未见对 `cause` 链中敏感信息（SQL 语句、密码字段、Token）的脱敏处理。

**对标**: 阿里 Java 开发手册明确要求"返回给用户的异常信息需经过脱敏"。

**建议**:
1. 增加 `ExceptionDesensitizer` 工具类，自动过滤异常堆栈中的:
   - `password`/`passwd`/`secret`/`token`/`apikey`/`accessKey` 等敏感字段
   - SQL 语句中的具体参数值（保留表名和字段名）
   - Redis/memcached 连接地址
2. 暴露给调用的异常信息统一采用 `异常码 + 用户消息 +（debug 堆栈）` 三段式

### 4.3 错误码前端可查询

**现状**: 错误码注册中心通过 `/actuator/ydsz-error-codes` 端点暴露，但缺少主动推送机制。

**对标**: 阿里云 API 网关在 4xx/5xx 响应中直接携带 `Recommend` 字段链接到错误码文档。

**建议**:
1. 鉴权 API 增加响应 header `X-Recommend-Doc: https://docs.njydsz.com/error/A00001`
2. 错误码文档工具自动生成 Markdown/HTML，附在每个错误码
3. 错误码变更时通过 WebSocket 推送到前端 IDE 插件

### 4.4 API 版本注解实际落地

**现状**: `@ApiVersion` 注解的 `since`、`deprecatedAt`、`sunsetAt`、`migrateTo` 属性仅文档声明，未见自动校验逻辑。

**对标**: Microsoft REST API Guidelines 要求 API 版本过期后返回 `410 Gone` 配合 `Sunset` 响应头。

**建议**:
1. 增加 `ApiVersionFilter`，在请求头中校验当前时间与 `deprecatedAt` 的关系
2. 超过 `sunsetAt` 返回 `410 Gone`，并在响应体中携带迁移路径
3. 集成到 Knife4j/SpringDoc，自动在 Swagger UI 中标记废弃接口

### 4.5 健康检查完善

**现状**: `CoreHealthIndicator` 只覆盖 JVM + 模块状态，未覆盖业务依赖探测。

**对标**: Spring Boot Actuator 提供 `CompositeHealthContributor`，可组合 Redis/DB/MQ 等多个健康探针。

**建议**:
1. 增加 `RedisHealthIndicator`、`PostgresHealthIndicator`、`NacosHealthIndicator` 等组合探针
2. 增加 liveness/readiness 分离（Kubernetes 部署需要 `health/liveness` 和 `health/readiness` 两个端点）
3. 业务模块可通过 `HealthContributorRegistry` 动态注册自定义探针

### 4.6 线程池指标完善

**现状**: 仅提供 active/queueSize/completed/poolSize 四个 Gauge 指标，缺少拒绝次数 Counter 和线程水位告警。

**对标**: Micrometer 官方文档建议 ThreadPoolTaskExecutor 暴露 `executor.rejected`、`executor.queue.remaining` 指标。

**建议**:
1. 增加 `executor.rejected` Counter 指标，按线程池名称打标
2. 增加 `executor.queue.usage.ratio` Gauge（队列使用率）
3. 超过阈值自动 warn 日志告警

---

## 五、过度设计识别与回调

### 5.1 自研熔断器 vs Resilience4j

**现状**: `ydsz-common-sentry/resilience/CircuitBreaker` 自研实现，滑动窗口实现精度未经行业验证。

**评估**: 对于中小规模项目，使用 `resilience4j-spring-boot3` 等成熟组件可大幅降低维护成本。

**建议**:
1. 将自研熔断器标记为 `@Deprecated`，提供迁移指南
2. 引入 Resilience4j 替代自研实现
3. 若必须保留自研，至少补充与 Resilience4j 的 benchmark 对比数据

### 5.2 双 JSON 序列化器体系

**现状**: 存在 `@JsonClass` (Jackson) 与自研 `YdszJson` 两套序列化体系，可能导致行为不一致。

**评估**: 建议收敛数据链路，统一为 Jackson；自研 JSON 库作为性能关键路径的"可选加速通道"。

**建议**:
1. 明确规则: Web API 用 Jackson、内部 RPC 用自研 JSON
2. 配置 `ydsz.json.web-engine=jackson` / `ydsz.json.rpc-engine=native`
3. 方法级别 `@JsonRender(engine="jackson")` 可覆盖

### 5.3 notify 构造器参数过多

**现状**: `NotifyServiceImpl` 有多个构造器重载，全参构造器注册 Bean 时所有可选依赖为 null 的风险在测试模式下存在。

**评估**: 建议通过 Builder 或 Options 模式重构，降低认知负担。

**建议**:
1. 统一为 `NotifyOptions` 配置对象 + Builder 模式
2. 减少 @Autowired 字段数量（目前分散在多个方法参数）

### 5.4 Socket.IO 与 Spring WebSocket 的选型

**现状**: 既有 STOMP over Spring WebSocket，又有 Socket.IO 协议实现，两套并存。

**评估**: 除非有特殊业务场景（如 Socket.IO 协议的硬件设备对接），否则建议统一为 Spring WebSocket。

**建议**:
1. 评估 Socket.IO 的实际调用量，若低频则考虑废弃
2. 统一为原生 WebSocket + 自定义消息协议（更安全可控）

---

## 六、最优先实施清单（Top 10）

| 序号 | 优先级 | 模块 | 问题 | 工作量 | 收益 |
|------|--------|------|------|--------|------|
| 1 | P0 | json | DoS 栈溢出（无深度限制） | 2h | 安全红线 |
| 2 | P0 | json | 父类字段丢失（不遍历 superclass） | 2h | 数据正确性 |
| 3 | P0 | docs | 临时文件泄漏风险 | 4h | 稳定性 |
| 4 | P0 | excel | 窗口大小硬编码 | 1h | 灵活性 |
| 5 | P1 | auth | 多端登录互踢缺失 | 16h | 安全提升 |
| 6 | P1 | safe | SSRF 防护缺失 | 8h | 安全红线 |
| 7 | P1 | exception | 异常堆栈脱敏 | 4h | 安全合规 |
| 8 | P1 | lock | 双锁实现重复（RedisCacheGuard vs LockWatchDog） | 16h | 维护性 |
| 9 | P2 | cache | 读写分离延迟感知 | 24h | 数据正确性 |
| 10 | P2 | thread | BeanFactory 动态注册 → BeanDefinitionRegistry | 4h | Spring 兼容性 |

---

## 七、总结

ydsz-common 框架作为企业内部快速开发框架，整体设计成熟度在同规模企业中处于领先位置。安全体系（JWT + Bloom Filter + SQL 注入防护 + XSS + CSRF + 双重数据权限）的完善度甚至超过部分开源 RBAC 框架；DDD 六层分层架构和 SPI 扩展点设计体现了良好的工程素养。

主要改进空间集中在：
1. **安全性修补**: JSON DoS、SSRF、堆栈脱敏属于安全红线，建议立即修复
2. **消除重复**: lock 与 cache-guard 的双锁实现是典型的"抽象不足"案例，应尽早抽取公共模块
3. **职责收敛**: docs 模块的能力蔓延是隐性技术债，越早拆分迁移成本越低
4. **可观测性增强**: 从指标到链路再到日志的三位一体已初具雏形，但缺少 end-to-end 的业务链路追踪

对标互联网大厂（阿里、美团、字节跳动）的内部框架规范，ydsz-common 在以下维度还需持续投入：
- 高基数问题规避（Prometheus tag 数量控制）
- 全链路压测友好（压测流量标记透传）
- 混沌工程集成（故障注入、降级演练）
- 安全红蓝对抗（渗透测试、漏洞赏金）

---

**报告结束**
