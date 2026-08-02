# YDSZ 项目能力现状模型

> 生成日期：2026-08-02
> 文档版本：v2.0.0（基于代码审计）
> 适用范围：ydsz-pmis 全仓库（后端 10 部署单元 + common 公共库 30 子模块 + 前端 1 主应用 + 9 子应用 + comm 共享包 30 子包 + conf 构建配置 8 子包）
>
> **审计说明**：
> - 本版本基于实际代码阅读审计生成，非仅依赖 README 文档
> - 审计范围：ydsz-common 全部 30 个子模块 + 10 个业务部署单元 + 前端 10 个应用 + comm/conf 共享包
> - 审计方法：读取每个模块的核心类、接口、配置类和注解定义

---

## 一、项目整体架构概览

### 1.1 技术栈总览

| 维度 | 技术选型 | 版本 |
|---|---|---|
| **后端框架** | Spring Boot + Spring Cloud + Spring Cloud Alibaba | 4.1.0 / 2025.1.2 / 2025.1.0.0 |
| **ORM** | MyBatis-Plus | 3.5.16 |
| **数据库** | PostgreSQL | 18 |
| **缓存** | Redis + Redisson | 8 / 4.6.1 |
| **消息队列** | RocketMQ | 5.x |
| **服务注册/配置** | Nacos | 2.3.2 |
| **限流熔断** | Sentinel + Resilience4j | 1.8.9 / 2.4.0 |
| **分布式事务** | Seata | 2.5.0 |
| **任务调度** | 自研分布式调度引擎 | - |
| **前端框架** | Vue + TypeScript + Vite | 3.5 / 5.x / 5.4 |
| **UI 组件** | Element Plus + vxe-table + ECharts | 2.8 / 4.x / 5.5 |
| **流程引擎** | bpmn-js (前端) + 自研引擎 (后端) | 17.x |
| **规则引擎** | 自研 LiteExpr 表达式引擎 | - |

### 1.2 微服务拓扑（10 个部署单元）

```
                          ┌─────────────────┐
                          │  ydsz-gateway   │
                          │     (9000)      │
                          └────────┬────────┘
                                   │
       ┌───────────────────────────┼───────────────────────────┐
       │                           │                           │
┌──────┴──────┐         ┌──────────┴──────────┐       ┌────────┴────────┐
│ydsz-userinfo│         │     ydsz-system      │       │  ydsz-project   │
│   (9001)    │         │      (9002)          │       │    (9003)       │
└──────┬──────┘         └──────────────────────┘       └────────┬────────┘
       │                                                        │
       │   ┌────────────────────┐       ┌────────────────┐      │
       │   │   ydsz-message     │       │  ydsz-cronjob  │      │
       │   │     (9004)         │       │    (9005)      │      │
       │   └────────────────────┘       └────────────────┘      │
       │                                                           │
       │   ┌────────────────────┐       ┌────────────────┐      │
       └──→│  ydsz-workflow     │←──────│  ydsz-nextwiki │←─────┘
           │    (9006)          │       │    (9007)      │
           └────────────────────┘       └────────────────┘

  ┌────────────────────┐       ┌────────────────────┐
  │  ydsz-literule     │       │    ydsz-agent      │
  │    (9008)          │       │    (9010)          │
  └────────────────────┘       └────────────────────┘

  ┌────────────────────────────────────────────────────────────┐
  │                  ydsz-common (公共依赖库)                  │
  │  30 个子模块 L1-L6 分层，被所有业务模块以 jar 依赖引入    │
  │  L1: json, core → L2: util → L3: domain, exception        │
  │  L4: jdbc, redis, cache, lock, thread, tenant             │
  │  L5: auth, safe, feign, audit, notify, queue, event, ...  │
  │  L6: base, web, app                                        │
  └────────────────────────────────────────────────────────────┘
```

> **端口分配**（按构建顺序）：
> 1. ydsz-gateway (9000) → 2. ydsz-userinfo (9001) → 3. ydsz-system (9002) →
> 4. ydsz-project (9003) → 5. ydsz-message (9004) → 6. ydsz-cronjob (9005) →
> 7. ydsz-workflow (9006) → 8. ydsz-nextwiki (9007) → 9. ydsz-literule (9008) →
> 10. ydsz-agent (9010)

> **注意**：不存在独立的 ydsz-thirdparty 模块。钉钉/飞书/企微的签名工具类
> （`DingTalkSignatureUtil`/`FeishuSignatureUtil`/`WeComSignatureUtil`）
> 及第三方审批同步逻辑位于 `ydsz-workflow-server/.../thirdparty/` 目录下。

---

## 二、ydsz-common 公共基础库（30 子模块）— 基于代码审计

### 2.1 模块分层总览

| 层级 | 模块数 | 模块列表 |
|---|---|---|
| **L1 基础设施层** | 1 | common-core |
| **L2 工具模块层** | 2 | common-util, common-json |
| **L3 基础服务层** | 2 | common-domain, common-exception |
| **L4 基础数据层** | 6 | common-jdbc, common-redis, common-cache, common-lock, common-thread, common-tenant |
| **L5 业务服务层** | 16 | common-auth, common-safe, common-feign, common-audit, common-notify, common-queue, common-event, common-config, common-seata, common-socket, common-netty, common-file, common-docs, common-excel, common-search, common-sentry |
| **L6 应用层** | 3 | common-base, common-web, common-app |
| **合计** | **30** | — |

### 2.2 L1 基础设施层

#### ydsz-common-core
- **统一响应模型**: `BaseResponse<T>` (code/msg/data/traceId/timestamp), `PageResponse`, `ProblemDetail` (RFC 7807)
- **链路追踪**: `TraceIdSupplier`, `SnowflakeTraceIdSupplier`, `TraceIdGenerator`
- **请求上下文**: `RequestContext`, `TenantContextHolder`, `TenantMdcFilter`
- **常量定义**: `CacheConstants`, `HeaderConstants`, `PageConstants`, `SecurityConstants`, `TokenConstants`, `TraceConstants`, `YdszMessageTopics`
- **枚举**: `DataScopeType`(6种), `IdentityType`, `ServiceType`, `TypeEnum`
- **健康检查**: `CoreHealthIndicator`
- **指标**: `CoreMetrics`, `CoreMetricsCallback`
- **配置**: `CoreAutoConfiguration`, `TraceAutoConfiguration`, `CoreProperties`, `FilterIgnoreProperties`, `SpringMessageResolver`

### 2.3 L2 工具模块层

#### ydsz-common-util（99+ 工具类）
- **数组**: `ArrayUtils`, `SortUtils`
- **Bean**: `BeanCopyUtils`, `BeanCopyOptions`, `PropertyConverter`, `Converters`
- **字节**: `ByteUtils`, `HexUtils`
- **类加载**: `ClassUtils`
- **集合**: `CollectionUtils`, `ListUtils`, `MapUtils`, `SetUtils`
- **并发**: `ContextPropagationUtils`, `ExecutorUtils`
- **日期**: `LocalDateTimeUtils`
- **异常**: `ExceptionUtils`
- **文件**: `FileTypeUtils`, `FileUtils`, `FileValidator`, `ImageUtils`, `MediaType`
- **哈希**: `HashUtils`
- **HTTP**: `CookieUtils`, `HttpClientFactory`, `OkHttpUtils`, `ResponseUtils`, `ServletUtils`, `UrlUtils`, `WebFluxUtils`
- **ID**: `SnowflakeUtils`, `SnowflakeProperties`, `SnowflakeAutoConfiguration`, `WorkerIdRegistry`, `UUIDUtils`, `RandomUtils`, `TracerUtils`
- **IO**: `IOUtils`
- **IP**: `IpAddrUtils`, `IpInfoUtils`
- **消息**: `MessageUtils`
- **数字**: `BigDecimalUtils`, `NumberUtils`
- **对象**: `ObjectUtils`
- **正则**: `RegexUtils`
- **安全**: `AesGcmCrypto`, `AesUtils`, `DigestUtils`, `PwdUtils`, `Rsa2Utils`
- **Spring**: `SpringBeanUtils`, `SpringContextHolder`, `SpringPropertyUtils`
- **字符串**: `StringUtils`, `CharsetUtils`
- **YAML**: `YamlUtils`
- **认证**: `AuthInfo`, `AuthInfoUtils`, `RequestHolder`, `YdszAuthInfo`
- **健康**: `UtilHealthIndicator`
- **配置**: `UtilAutoConfiguration`, `ThreadPoolMonitorAutoConfiguration`

#### ydsz-common-json（超高性能 JSON 引擎）
- **核心API**: `YdszJson.toJson/toObject/parseArray/parseMap/format`, `YdszJsonMapper`, `YdszJsonReader`, `YdszJsonWriter`
- **ASM 字节码**: `AsmBeanCodecGenerator`, `AsmSerializer`, `AsmDeserializer`, `AsmCodecCache`
- **JSONPath**: `YdszJsonPath`
- **JSON Pointer (RFC 6901)**: `JsonPointer`
- **JSON Merge Patch (RFC 7396)**: `JsonMergePatch` (merge/diff)
- **Schema 校验**: `SchemaValidator`, `YdszJsonSchema`, `ValidationResult`
- **树模型**: `JsonNode`, `TreeConverter`, `YdszJsonObject`, `YdszJsonArray`
- **流式API**: `JsonGenerator`, `JSONReader`, `JSONWriter`
- **安全机制**: `AutoTypeChecker`, `AutoTypeWhitelistScanner`, `SafeObjectInputFilter`
- **自定义注解**: 30+ (`@YdszJsonField`, `@YdszJsonView`, `@YdszJsonPropertyOrder`, `@YdszJsonFormat`, `@YdszJsonTypeInfo`, `@YdszJsonSubTypes` 等)
- **命名策略**: `PropertyNamingStrategy`
- **模块注册**: `JsonModuleRegistry`, `YdszJsonModule`
- **多态类型**: `PolymorphicTypeResolver`
- **GraalVM**: `GraalVmDetector`
- **SIMD**: `VectorSimdUtil`
- **零拷贝**: `ZeroCopyDeserializer`
- **缓存**: `BeanSerializerCache`, `SerializerCache`, `YdszJsonCacheStats`
- **指标**: `JsonCacheMetrics`, `YdszJsonMetrics`, `JsonMetricsCallback`
- **配置**: `YdszJsonConfig`
- **Spring**: 5个配置类

### 2.4 L3 基础服务层

#### ydsz-common-domain
- **实体基类**: `BaseEntity<T>`, `BaseAuditEntity<T>`, `BaseIdEntity<T>`, `BaseLong<T>`, `Persistable`, `SoftDeletable`, `Versionable`
- **审计**: `Auditable`, `LogBase`
- **领域事件**: `DomainEvent`, `ModuleEventTypes`
- **注解**: `@CreateAt`, `@CreatedBy`, `@DomainService`, `@SoftDelete`, `@TenantId`, `@UpdateAt`, `@UpdatedBy`, `@Version`
- **DAG**: `DagInstanceStatus`, `DagNodeStatus`, `SpELConditionEvaluator`
- **查询**: `BaseQuery`, `PageQuery`, `PageResult`, `BaseDTO`
- **树**: `TreeBuilder`, `TreeNode`
- **作业**: `JobHandler`, `JobLogger`, `JobContextHolder`, `JobLoggerHolder`, `MapContext`, `MapProcessor`, `MapReduceProcessor`, `MapTask`, `ProcessResult`, `ShardingContext`
- **枚举**: `BaseStatusEnum`
- **配置**: `DomainAutoConfiguration`, `DomainProperties`
- **健康**: `DomainHealthIndicator`

#### ydsz-common-exception
- **异常基类**: `AbstractYdszException` (code/key/params/httpStatus/level/category/path/timestamp, 懒加载 i18n)
- **业务异常**: `BusinessException`, `YdszExceptionBuilder`
- **系统异常**: `SysException`
- **异常码**: `ExceptionCode`, `ExceptionCodeRegistry`, `UnifiedExceptionCode`
- **分类**: `ExceptionCategory` (BUSINESS/SYSTEM/VALIDATION/AUTH/THIRD_PARTY)
- **级别**: `ExceptionLevel` (INFO/WARN/ERROR/FATAL)
- **处理器**: `MvcExceptionHandler`, `WebFluxExceptionHandler`, `JdbcExceptionHandler`, `ValidationExceptionHandler`, `BaseExceptionHandler`
- **ProblemDetail**: RFC 7807
- **国际化**: `I18nConfiguration`, `WebI18nConfiguration`, `I18nProperties`, `SpringMessageResolver`
- **自动配置**: 6个 AutoConfiguration
- **错误码文档**: `ExceptionCodeDocEndpoint` (`/actuator/exception-codes`)
- **结果码**: `ResultCode`, `ResultCodeRegistry`, `ResultCodeScanner`, `YdszResultCode`
- **健康**: `ExceptionHealthIndicator`
- **指标**: `ExceptionMetrics`
- **模型**: `ProblemDetail`, `ExceptionInfo`

### 2.5 L4 基础数据层

#### ydsz-common-jdbc
- **MyBatis-Plus**: `MybatisPlusConfiguration`, `MapperScanConfiguration`
- **动态数据源**: `DynamicRoutingDataSource`, `DynamicDataSourceContextHolder`, `DsAnnotationInterceptor`, `@DS`, `DynamicDataSourceAutoConfiguration`
- **读写分离**: `ReadWriteSplittingAutoConfiguration`, `ReadWriteSplittingProperties`, `ReadWriteSplittingInterceptor`, 4种负载策略
- **字段填充**: `MyMetaObjectHandler`, `FieldFillHandler`, `CreatedAtHandler`, `CreatedByHandler`, `UpdatedAtHandler`, `UpdatedByHandler`, `AbstractFieldFillHandler`
- **逻辑删除**: `LogicalDeleteConfiguration`, `LogicalDeleteInterceptor`
- **乐观锁**: `OptimisticLockConfiguration`, `OptimisticLockInterceptor`
- **分页**: `PaginationProperties`
- **数据权限**: `DataPermissionConfiguration`, `DataPermissionContext`, `DataPermissionContextResolver`, `DataScopeIdExpander`, `DataPermissionIgnore`, `RowPermissionInnerInterceptor`, `ColPermissionInnerInterceptor`, `DataPermissionHelper`
- **SQL防火墙**: `SqlFirewallProperties`, `SqlFirewallInnerInterceptor`
- **SQL追踪**: `SqlTraceAutoConfiguration`, `SqlTraceInnerInterceptor`
- **熔断器**: `DatabaseCircuitBreaker`, `DatabaseCircuitBreakerAutoConfiguration`, `CircuitBreakerProperties`, `CircuitBreakerInterceptor`
- **连接池**: `HikariCPConfiguration`, `HikariCPProperties`
- **租户隔离**: `TenantIsolationProperties`, `TenantIsolationException`
- **SPI**: `InnerInterceptorProvider`, `OrderedInnerInterceptor`
- **类型处理**: `IntegerStringTypeHandler`, `JsonTypeHandler`, `ListTypeHandler`, `MapTypeHandler`
- **健康**: `DataSourceHealthIndicator`, `DynamicDataSourceHealthIndicator`
- **监控**: `SqlFingerprint`
- **配置**: `JdbcProperties` + 18个配置类
- **实体**: `MpBaseEntity`, `MpBaseAuditEntity`, `MpBaseIdEntity`
- **枚举**: `DataSourceType`, `FieldFillStrategyEnum`, `InterceptTableStrategy`

#### ydsz-common-redis
- **门面**: `RedisService` (实现 `BatchRedisOperations`, 委托9个子组件)
- **String**: `RedisStringOps` (set/get/setIfAbsent/incr/decr/mget/mset)
- **Hash**: `RedisHashOps` (hGet/hSet/hDel/hKeys/hValues/hIncr)
- **Set/List/ZSet**: `RedisCollectionOps`
- **Geo/HyperLogLog**: `RedisGeoOps`
- **Pipeline**: `RedisPipelineOps`, `RedisPipelineOpsImpl`
- **Lua**: `RedisAdvancedOps`
- **Pub/Sub**: `RedisPubSubOps`
- **Stream**: `RedisStreamOps`
- **事务**: `RedisTransactionOps`
- **布隆过滤器**: `BloomFilterService`, `RedisBloomFilter`
- **延迟队列**: `RedisDelayedQueue`, `DelayedTask`
- **限流器**: `RedisRateLimiter`
- **缓存击穿防护**: `RedisCacheGuard`, `NullValueCacheHelper`
- **雪花ID**: `RedisSnowflakeIdGenerator`, `RedisWorkerIdRegistry`
- **Key前缀**: `RedisKeyPrefixProvider`, `TenantRedisKeyPrefixer`
- **序列化**: `YdszJsonRedisSerializer`
- **重试**: `RedisRetryInterceptor`
- **集群**: `ClusterSlotUtil`
- **缓存注解**: `@YdszCacheable`, `@YdszCacheEvict`, `@YdszCachePut`
- **配置**: `RedisConfiguration`, `RedisConnectionFactoryConfigurer`, `RedisProperties`, `RedisClientProperties`, `SnowflakeRedisAutoConfiguration`
- **健康**: `RedisHealthIndicator`
- **指标**: `RedisMetricsCollector`, `RedisMetricsConfiguration`
- **枚举**: `RedisKeysEnum`, `FailOpenPolicy`, `RedisOperationException`
- **批处理**: `BatchRedisOperations`

#### ydsz-common-cache（高性能本地缓存）
- **11种类型**: `TINYLFU`, `LRU`, `LFU`, `WEIGHTED`, `CONCURRENT`, `STRIPED`, `ENHANCED_LOADING`, `SOFT_VALUE`, `WEAK_VALUE`, `WEAK_KEY`, `TIMED`
- **Builder**: `CacheBuilder`
- **Window-TinyLFU**: `WindowTinyLFUCache`, `FrequencySketch`
- **LRU**: `LRUCache`
- **LFU**: `LFUCache`
- **加权**: `WeightedCache`, `Weigher`
- **并发**: `ConcurrentCache`, `StripedConcurrentCache`, `StripedLock`
- **增强加载**: `EnhancedLoadingCache`, `AsyncLoadingCacheImpl`, `AsyncCache`, `AsyncCacheAdapter`
- **TTL**: `Expiry`, `TTLMode`, `TimedCacheDecorator`
- **装饰器**: `ConditionalCacheDecorator`, `ExpirableCache`, `MemoryAwareEvictionCache`, `SwrCacheDecorator`, `TimedCacheDecorator`, `WriteBehindCache`, `WriteThroughCache`
- **多级缓存**: `MultiLevelCache`, `MultiLevelCacheBuilder`, `RedisCacheAdapter`, `CacheInvalidationBroadcaster`, `DistributedRebuildLock`, `RedisCacheInvalidationBroadcaster`
- **熔断降级**: `Resilience4jCacheDecorator`
- **空值防护**: `NullValueGuard`, `CacheProtectionGuard`
- **监听**: `RemovalListener`, `RemovalCause`
- **统计**: `CacheStats`, `PaddedStatsCounter`
- **预热**: `CacheWarmer`
- **Key生成**: `CacheKeyGenerator`
- **导出导入**: `CacheExportImport`
- **Spring**: `SpringYdszCache`, `YdszCacheManager`, `YdszCacheAutoConfiguration`, `YdszCacheProperties`
- **指标**: `CacheMeterBinder`, `CacheMetricsCollector`, `CacheMetricsAutoConfiguration`
- **健康**: `CacheHealthIndicator`, `SpringCacheHealthIndicator`
- **基准**: `CacheBenchmark`
- **缓存注解**: `@Cached`, `@CacheInvalidate`, `@CacheRefresh`, `CacheAnnotationAspect`
- **线程池**: `CacheThreadPoolManager`
- **调度**: `CacheScheduler`
- **支持**: `AsyncFunction`, `CacheLoader`, `CacheWriter`, `CacheKeyGenerator`, `Expiry`, `TTLMode`, `Weigher`
- **API**: `Cache`, `LoadingCache`, `AsyncCache`, `CacheAsMapView`, `CachePolicy`, `CacheProtectionGuard`

#### ydsz-common-lock（分布式锁）
- **核心接口**: `DistributedLocker` (tryLock/unlock/isLocked/getRemainTime/pexpire + 可重入/公平扩展)
- **实现**: `AbstractRedisDistributedLock`, `RedisReentrantLock`, `RedisFairLock`, `RedisMultiLock`, `FallbackDistributedLock`
- **读写锁**: `RedisReadWriteLock`
- **信号量**: `RedisSemaphore`
- **幂等性**: `@Idempotent`, `@IdempotentExempt`, `IdempotentAspect`, `IdempotentStrategy`, `RedisIdempotentStrategy`, `IdempotentException`
- **防重提交**: `@RepeatSubmit`, `RepeatSubmitAspect`, `RepeatSubmitTokenService`, `RepeatSubmitTokenController`
- **分布式调度**: `@DistributedScheduled`, `DistributedScheduledAspect`
- **WatchDog**: `LockWatchDog`, `LockLeakDetector`
- **策略**: `LockStrategy`, `DefaultLockStrategy`
- **校验**: `LockKeyValidator`
- **指标**: `LockMetrics`, `LockMetricsConfiguration`, `LockMetricsExporter`, `LockMicrometerCollector`
- **配置**: `DistributedLockAutoConfiguration`, `LockProperties`
- **健康**: `LockHealthIndicator`
- **注解**: `@YdszDistributedLock`, `LockType`

#### ydsz-common-thread
- **自动配置**: `ThreadPoolAutoConfiguration`, `ThreadPoolProperties`
- **监控**: `ThreadPoolMonitorAutoConfiguration`
- **健康**: `ThreadHealthIndicator`

#### ydsz-common-tenant（多租户）
- **上下文**: `TenantContext`, `TenantContextHolder`, `SystemTenantContextRunner`
- **配置**: `TenantConfigProvider`, `TenantProperties`, `TenantAutoConfiguration`
- **数据源**: `TenantDataSourceFilter`, `TenantDataSourceRouter`
- **Web**: `TenantContextWebFilter`
- **Feign**: `TenantContextFeignInterceptor`
- **异步**: `TenantContextTaskDecorator`
- **缓存**: `CacheIsolationStrategy`, `TenantAwareRedisKey`
- **限流**: `TenantRateLimiter`
- **生命周期**: `TenantLifecycleManager`, `TenantStatus`
- **审计**: `TenantAuditLogger`
- **指标**: `TenantMetrics`
- **注解**: `@TenantColumn`
- **健康**: `TenantHealthIndicator`
- **拦截器**: `TenantInterceptorProvider`, `TenantIsolationInterceptor`

### 2.6 L5 业务服务层（16个模块）

#### ydsz-common-auth（认证鉴权）
- **RBAC**: `RbacPermissionEvaluator` (loadUserInfo/loadCurrentUserInfo/validateMenu/validateApi/hasPermission)
- **注解**: `@AuthApiPermission`, `@AuthColPermission`, `@AuthMenuPermission`, `@AuthRowPermission`, `@DataScope`, `@PermissionMode`, `@EnableYdszAuth`
- **切面**: `AuthPermissionAspect`, `AuthColPermissionAspect`, `AuthRowPermissionAspect`
- **JWT**: `JwtTokenService`, `TokenService`, `TokenProperties`
- **用户**: `RbacUserInfoService`, `RedisRbacUserInfoService`
- **角色权限**: `RolePermissionLoader`, `RedisRolePermissionLoader`
- **数据权限**: `DataPermissionResolver`, `RedisRoleDataPermissionResolver`, `DataPermissionCustomSqlProvider`
- **列权限**: `ColumnPermissionResolver`, `RedisRoleColumnPermissionResolver`, `ColumnPermission`, `ColumnPermissionInfo`, `ColumnScopeInfo`
- **缓存**: `LocalPermissionCache`, `CacheKeyStrategy`, `DefaultCacheKeyStrategy`
- **事件**: `PermissionChangedEvent`, `PermissionChangeListener`, `PermissionChangePublisher`, `PermissionChangeNotifier`, `PermissionChangeCacheInvalidator`, `PermissionCacheInvalidationListener`
- **黑名单**: `TokenBlacklistService`, `ReactiveTokenBlacklistService`, `TokenBlacklistBloomFilter`
- **层级**: `PermissionHierarchy`
- **预检**: `PermissionPreCheck`, `PermissionPreChecker`, `PermissionCheckResult`
- **CSRF**: `CsrfTokenValidator`
- **限流**: `RateLimiter`
- **上下文**: `AuthContext`, `TenantContextHolderImpl`
- **处理器**: `AuthHandler`, `AbstractAuthHandler`, `ParsedAuthHeaders`
- **提供者**: `AuthenticationProvider`
- **工具**: `AccessTokenUtils`, `AuthColPermissionSigner`, `AuthDigestUtils`, `PermissionMerger`, `PermissionUtils`
- **国际化**: `PermissionMessageResolver`
- **预热**: `PermissionWarmUpInitializer`
- **配置**: `AuthConfiguration`, `AuthFilterConfiguration`, `AuthFilterProperties`, `AuthProperties`, `KeyspaceNotificationProperties`, `TenantContextHolderConfiguration`
- **健康**: `AuthHealthIndicator`
- **指标**: `AuthMetrics`, `AuthMetricsCollector`, `PermissionMetrics`
- **模型**: `UserInfo`, `RolePermissions`, `DataScopeAware`, `ColumnScopeAware`

#### ydsz-common-safe（安全防护）
- **XSS**: `XssFilter`, `XssHttpServletRequestWrapper`, `XssRequestBodyAdvice`, `JsonBodyXssCleaner`, `XssJsonMessageConverter`, `XssAutoConfiguration`, `XssJsonConfig`, `XssPolicyFactory`, `XssStringDeserializer`, `@Xss`, `@EnableYdszSafe`
- **SQL注入**: `SqlInjectionFilter`, `SqlInjectionProperties`
- **CSRF**: `CsrfFilter`, `CsrfToken`, `CsrfTokenGenerator`, `CsrfTokenRepository`, `DefaultCsrfTokenGenerator`, `InMemoryCsrfTokenRepository`, `RedisCsrfTokenRepository`, `CsrfProperties`
- **安全头**: `SecurityHeaderFilter`, `BaseSecurityHeaderFilter`, `SecurityHeaderProperties`
- **限流**: `RateLimitAutoConfiguration`, 7种算法 (SlidingWindow/TokenBucket/LeakyBucket/FixedWindow/Counter/SlidingLog/Guava), `@RateLimit` AOP, `CircuitBreaker`, 集群限流
- **IP控制**: `IpAccessFilter`, `IpAccessService`, `IpAccessProperties`
- **API签名**: `ApiSignatureFilter`, `ApiSignatureProperties`
- **自动封禁**: `SecurityEventAggregator`, `AutoBlockProperties`
- **脱敏**: `SensitiveDataAdvice`, `SensitiveData`, `SensitiveDataProcessor`, `SensitiveDataSerializer`, `SensitiveDataProperties`, `SensitiveType`, `SensitiveUtil`
- **验证码**: `CaptchaAutoConfiguration`, `CaptchaGenerator`, `CaptchaRateLimiter`, `CaptchaResult`, `CaptchaStore`, `CaptchaType`, `ArithmeticCaptchaGenerator`, `CaptchaValidator`
- **加密**: `AesGcmCrypto`, `NonceCache`
- **字段加密**: `FieldEncryptionAutoConfiguration`, `EncryptField`, `EncryptFieldProperties`, `EncryptTypeHandler`, `FieldEncryptionService`, `DecryptFailureStrategy`
- **密码**: `PasswordStrengthValidator`
- **安全事件**: `SecurityEvent`, `SecurityEventPublisher`, `SecurityEventListener`, `SecurityAlertListener`, `SecurityEventType`, `SecurityEventAggregator`
- **审计**: `SecurityAuditLogger`, `DefaultSecurityAlertLogger`
- **指标**: `SafeMetrics`
- **配置**: `SafeConfiguration` + 12个Properties
- **健康**: `SafeHealthIndicator`
- **工具**: `ClientIpResolver`

#### ydsz-common-feign（Feign增强）
- **拦截器**: `FeignRequestInterceptor`, `TraceRequestInterceptor`, `GzipRequestCompressInterceptor`, `BulkheadRequestInterceptor`
- **响应**: `FeignResponseInterceptor`, `FeignResponseMetricsAdapter`
- **错误**: `YdszFeignErrorDecoder`, `BadRequestException`, `NotFoundException`, `OpenFeignException`
- **日志**: `YdszFeignLogger`
- **重试**: `MethodAwareRetryer`
- **编解码**: `JsonEncoder`, `JsonDecoder`, `ResponseUnwrapDecoder`
- **熔断**: `FeignCircuitBreakerStrategy`, `Resilience4jCircuitBreakerAdapter`, `CircuitBreakerStatePersistence`
- **Bulkhead**: `BulkheadRequestInterceptor`
- **动态**: `DynamicFeignClientFactory`, `FeignConfigRefresher`
- **链路**: `FeignTraceHandler`, `SkyWalkingTraceHandler`, `TraceRequestInterceptor`
- **连接池**: `PoolingHttpClientConnectionManager`, `CloseableHttpClient`
- **组装**: `NameAssembler`, `NameAssemblerAutoConfiguration`, `NameAssemblerProperties`
- **健康**: `FeignHealthIndicator`
- **指标**: `FeignMetricsConfiguration`, `FeignMicrometerCollector`
- **配置**: `FeignConfiguration`, `FeignProperties`, `EnableYdszFeign`
- **常量**: `FeignClientConstants`
- **DTO**: `NotificationFeignDTO`, `RealtimePushDTO`
- **通知客户端**: `NotificationClient`, `NotificationClientFallbackFactory`
- **异常**: `BadRequestException`, `NotFoundException`, `OpenFeignException`

#### ydsz-common-audit（审计日志）
- **接口**: `AuditRecorder` (同步/异步/批量), `AuditStorage`
- **Disruptor**: `DisruptorAuditRecorder`
- **实现**: `DefaultAuditRecorder`, `AsyncAuditRecorder`, `AuditFallbackWriter`
- **注解**: `@Audit`, `@EnableYdszAudit`
- **切面**: `AuditAspect`
- **事件**: `AuditEvent`, `OperationLogEvent`, `DataExportAuditEvent`
- **实体**: `AuditLog`, `AuditAction`, `AuditStatus`, `AuditType`
- **Diff**: `DiffCalculator`, `DiffField`, `DiffReport`, `FieldDiff`, `DiffValueFormatter`
- **分片**: `DailyShardingStrategy`, `MonthlyShardingStrategy`, `YearlyShardingStrategy`, `TableShardingStrategy`
- **脱敏**: `SensitiveFieldMask`, `@MaskField`
- **查询**: `AuditQueryService`, `DefaultAuditQueryService`
- **存储**: `JdbcAuditStorage`, `DefaultAuditStorage`
- **模板**: `AuditTemplateProcessor`
- **指标**: `AuditMetricsBinder`
- **健康**: `AuditHealthIndicator`
- **配置**: `AuditAutoConfiguration`, `AuditProperties`, `AuditEventListener`
- **上下文**: `AuditContext`

#### ydsz-common-notify（通知引擎）
- **接口**: `NotifyService`, `NotifyServiceImpl`, `NotifyRequest`, `NotifySendResult`, `NotifyResult`
- **渠道**: `NotifyChannelStrategy`, `EmailNotifySender`, `SmsNotifySender`, `DingTalkNotifySender`, `WeComNotifySender`, `FeishuNotifySender`
- **类型**: `NotifyChannel`, `NotifyPriority`, `NotifyType`
- **模板**: `TemplateEngine`, `SpelTemplateEngine`, `NotifyTemplate`, `HtmlTemplateRegistry`, `NotifyTemplateProperties`
- **重试**: `NotifyRetryQueue`, `RedisNotifyRetryQueue`, `PersistentNotifyRetryQueue`, `DeadLetterHandler`, `InMemoryDeadLetterHandler`
- **熔断**: `NotifyCircuitBreaker`, `NotifyCircuitBreakerRegistry`
- **去重**: `NotifyDedupService`
- **聚合**: `NotificationAggregator`, `TimeWindowAggregator`
- **限流**: `NotifyRateLimiterManager`
- **降级**: `NotifyFallbackManager`
- **偏好**: `NotifyPreference`, `NotifyPreferenceManager`
- **国际化**: `NotifyI18nResolver`, `NotifyI18nService`
- **邮件**: `DkimSigner`, `EmailContentSanitizer`, `EmailSmtpHealthChecker`, `NotifyPasswordResolver`
- **签名**: `DingTalkSignatureUtil`, `FeishuSignatureUtil`, `WeComSignatureUtil`
- **Provider**: `AliyunSmsProvider`, `EmailProvider`, `SmsProvider`
- **追踪**: `EmailTrackingService`
- **事务**: `TransactionalNotifyPublisher`
- **异步**: `AsyncNotifyService`
- **配置**: `NotifyConfiguration`, `NotifyProperties`, `NotifyTemplateAutoConfiguration`
- **健康**: `NotifyHealthIndicator`
- **指标**: `NotifyMetrics`
- **事件**: `UnifiedAlertEvent`
- **审计**: `NotifyAuditService`
- **助手**: `NotifyHelper`
- **安全**: `DkimSigner`, `EmailContentSanitizer`, `EmailSmtpHealthChecker`, `NotifyPasswordResolver`

#### ydsz-common-queue（消息队列）
- **接口**: `IMessageQueue`, `AbstractMessageQueue`, `MessageQueueFactory`, `IMessageQueueProvider`, `IMessageHandler`, `IMessagePublisher`, `IMessageSubscriber`
- **实现**: `RedisListMQ`, `RedisPubSubMQ`, `RedisStreamMQ`
- **ActiveMQ**: `ActiveMQ`, `ActiveMQPublisher`, `ActiveMQSubscriber`, `ActiveMQProperties`
- **Kafka**: `KafkaMQ`, `KafkaMessagePublisher`, `KafkaMessageSubscriber`, `KafkaQueueProperties`
- **RabbitMQ**: `RabbitMQ`, `RabbitMQPublisher`, `RabbitMQSubscriber`, `RabbitMQProperties`
- **RocketMQ**: `RocketMQ`, `RocketMQPublisher`, `RocketMQSubscriber`, `RocketMQProperties`
- **死信**: `DeadLetterQueueService`, `DeadLetterQueueServiceImpl`, `DeadLetterRetryScheduler`, `DeadLetterQueueController`
- **去重**: `MessageDeduplicator`, `RedisMessageDeduplicator`, `DedupAwareSubscriber`, `DedupCleanupScheduler`
- **轨迹**: `MessageTrace`, `MessageTracer`, `MessageTraceAspect`, `MessageTraceInterceptor`, `DefaultMessageTraceRecorder`, `RedisMessageTraceRecorder`
- **重试**: `RetryPolicy`
- **限流**: `ConsumerRateLimiter`
- **熔断**: `QueueCircuitBreaker`, `CircuitBreakerPublisher`
- **守卫**: `ConsumerThreadGuard`
- **压缩**: `MessageCompressor`
- **配置**: `QueueConfiguration`, `QueueProperties`, `EnableQueue`
- **健康**: `QueueHealthIndicator`
- **指标**: `MessageMetrics`, `QueueMetricsBinder`
- **枚举**: `QueueType`
- **领域**: `QueueMessage`
- **服务**: `NoOpDeadLetterQueueService`, `RedisListPublisher/Subscriber`, `RedisPubSubPublisher/Subscriber`, `RedisStreamPublisher/Subscriber`

#### ydsz-common-event（事件 / Outbox 模式）
- **Outbox**: `OutboxService`, `OutboxEventStore`, `OutboxProcessor`, `OutboxRepository`, `OutboxMessage`, `OutboxStatus`
- **网关**: `EventPublishGateway`, `NoopEventPublishGateway`, `RocketMqEventPublishGateway`
- **配置**: `EventAutoConfiguration`, `EventProperties`, `RocketMqGatewayConfiguration`
- **健康**: `OutboxHealthIndicator`
- **模型**: `OutboxMessage`, `OutboxStatus`, `StandardEventTypes`, `DatabaseDialect`

#### ydsz-common-config（配置加密）
- **配置**: `ConfigAutoConfiguration`, `ConfigProperties`
- **加密健康**: `ConfigEncryptHealthIndicator`
- **热加载**: `ConfigChangeBridge`, `ConfigChangeEvent`, `ConfigChangeListener`
- **CLI 工具**: `ConfigCliTool`

#### ydsz-common-seata（分布式事务）
- **接口**: `DistributedTransactionManager`, `TccAction`, `SagaStep`, `XidPropagator`, `TccContext`, `TccBranchStatus`, `TccTransactionLog`, `TccTransactionLogStore`, `TransactionType`
- **实现**: `SeataTransactionManager`, `LocalTransactionManager`, `TccTransactionManager`, `SagaOrchestrator`, `SeataGlobalTransactionExecutor`, `AbstractTransactionManager`, `DefaultXidPropagator`, `InMemoryTccTransactionLogStore`, `RedisTccTransactionLogStore`, `TccTransactionRecoveryScanner`
- **拦截器**: `FeignXidRequestInterceptor`, `XidServletFilter`
- **审计**: `TransactionAuditLogger`
- **配置**: `SeataAutoConfiguration`, `SeataProperties`
- **健康**: `SeataHealthIndicator`
- **指标**: `SeataMetrics`

#### ydsz-common-socket（WebSocket）
- **会话**: `WebSocketSessionEventListener`, `MultiDevicePolicy`, `OnlineUserService`
- **心跳**: `WebSocketHeartbeatHandler`
- **认证**: `WebSocketAuthInterceptor`, `StompMessageInterceptor`
- **集群**: `WebSocketClusterMessage`, `WebSocketClusterPublisher`, `WebSocketClusterSubscriber`
- **压缩**: `MessageCompressor`
- **配置**: `WebSocketAutoConfiguration`, `WebSocketClusterAutoConfiguration`, `WebSocketConfigurer`, `WebSocketProperties`
- **常量**: `WebSocketConstants`
- **枚举**: `MessagePriority`
- **过滤**: `MessageFilter`
- **健康**: `WebSocketHealthIndicator`
- **指标**: `WebSocketMetrics`
- **监控**: `SlowConnectionDetector`
- **离线**: `OfflineMessageStore`, `RedisOfflineMessageStore`, `MessageRetryQueue`, `RedisMessageRetryQueue`, `DeadLetterQueue`, `RedisDeadLetterQueue`, `RetryableMessage`
- **ACK**: `MessageAckService`
- **推送**: `RealtimePushTemplate`, `DefaultRealtimePushTemplate`
- **限流**: `ConnectionLimiter`, `WebSocketRateLimiter`
- **熔断**: `WebSocketCircuitBreaker`
- **审计**: `WebSocketAuditService`
- **追踪**: `WebSocketTraceContext`
- **序列化**: `MessageSerializer`, `JsonMessageSerializer`

#### ydsz-common-netty（Netty TCP）
- **服务器**: `AbstractNettyServer`, `NettyServerLifecycle`
- **客户端**: `AbstractNettyClient`, `ReconnectHandler`
- **编解码**: `LengthFieldFrameDecoder`, `LengthFieldCodec`, `JsonMessageCodec`
- **事件分发**: `ChannelEventListener`, `ChannelEventDispatcher`, `MessageHandler`, `MessageDispatcher`
- **连接管理**: `ChannelGroupManager`, `ConnectionEventHandler`, `IdleStateHandlerFactory`, `TrafficMonitoringHandler`
- **EventLoop**: `NettyEventLoopPool`
- **SSL/TLS**: `SslContextFactory`, `NettySslException`
- **原生传输**: `NativeTransportDetector`
- **配置**: `NettyAutoConfiguration`, `NettyProperties`
- **指标**: `NettyChannelMetrics`
- **健康**: `NettyHealthIndicator`

#### ydsz-common-file（文件存储）
- **启用注解**: `@EnableYdszFile`
- **存储接口**: `IFileStorage`, `FileUploader`, `FileDownloader`, `FileManager`, `PartInfo`
- **存储平台**: `AbstractFileStorage`, `LocalStorage`, `MinioStorage`, `S3Storage`, `OssStorage`, `CosStorage`, `QiniuStorage`, `ObsStorage`, `StorageType`, `DefaultStorageFactory`, `IFileStorageProvider`
- **分片续传**: `CheckpointService`, `CheckpointStore`, `LocalCheckpointStore`, `RedisCheckpointStore`, `UploadCheckpoint`, `MultipartContextStore`, `UploadConcurrencyGuard`
- **去重**: `FileDedupService`
- **病毒扫描**: `VirusScanner`, `NoOpVirusScanner`
- **类型校验**: `FileTypeDetector`, `MagicNumberRegistry`, `FileTypeValidator`
- **生命周期**: `FileLifecycleManager`, `FileLifecycleProperties`
- **重试**: `StorageRetryHelper`, `UploadProgressListener`
- **配置**: `FileConfiguration`, `FileProperties`, `FileUploadProperties`
- **健康**: `FileHealthIndicator`
- **指标**: `FileMetrics`

#### ydsz-common-docs（文档解析）
- **解析 SPI**: `DocumentParser`, `DocumentParserRegistry`
- **8 种格式**: `WordDocumentParser`, `ExcelDocumentParser`, `PdfDocumentParser`, `PptDocumentParser`, `CsvDocumentParser`, `HtmlDocumentParser`, `MarkdownDocumentParser`, `TxtDocumentParser`, `DocumentFormat`
- **解析模式**: `ParseMode`, `ParseOptions`
- **领域模型**: `DocumentContent`, `DocumentSection`, `DocumentTable`, `DocumentImage`, `DocumentMetadata`, `DocumentParseResult`
- **OCR**: `OcrEngine`, `OcrProvider`
- **PII 检测**: `PiiDetector`, `PiiDetectorComposite`, `EmailDetector`, `PhoneDetector`, `IdCardDetector`, `BankCardDetector`, `ApiKeyDetector`, `PiiType`, `PiiFinding`
- **安全扫描**: `DocumentSecurityScanner`, `DocumentSecurityScannerComposite`, `MacroDetector`, `PdfJsDetector`, `SecurityLevel`, `SecurityScanResult`
- **脱敏**: `DocumentRedactor`, `TextRedactor`
- **水印**: `WatermarkProvider`, `TextWatermarkProvider`
- **转换**: `DocumentConverter`
- **预处理**: `PreprocessPipeline`, `TextCleaner`, `TextNormalizer`, `TextChunker`, `DocumentPreprocessor`
- **服务**: `DocumentService`, `AsyncDocumentParser`, `DocumentSummarizer`
- **配置**: `DocsAutoConfiguration`, `DocsProperties`
- **健康**: `DocsHealthIndicator`
- **指标**: `DocsMetrics`

#### ydsz-common-excel（Excel 读写）
- **门面**: `ExcelFacade`, `ExcelReader`, `ExcelWriter`, `ExcelSheetInfo`, `ExcelTemplateWriter`, `ConcurrentExcelWriter`
- **注解**: `@ExcelProperty`, `@ExcelHead`, `@ExcelSheet`, `@ExcelIgnore`, `@ExcelStyle`, `@ContentStyle`, `@ContentFont`
- **SAX 流式读**: `SuperFastExcelReader`, `SheetXmlReader`, `SharedStringsReader`, `ChunkedSSTTable`, `HeaderAnalyzer`, `RowParser`, `ExcelXmlParser`
- **流式写**: `SuperFastExcelWriter`, `UltraFastCellWriter`, `StyleManager`, `WriteStyleHandler`, `ValueFormatter`, `WorkbookFactory`
- **类型转换**: `CellValueConverter`, `ConverterRegistry`, `ConverterChain`, `BigDecimalConverter`, `DateConverter`, `LocalDateTimeConverter`, `EnumConverter`
- **事件**: `ReadListener`, `WriteHandler`, `ReadHandler`, `AnalysisContext`, `WriteContext`
- **公式注入防护**: `FormulaInjectionGuard`
- **列式存储**: `ColumnarType`, `ColumnarSchema`, `ColumnarField`, `ColumnarRowMapper`, `ParquetConfig`, `OrcConfig`, `ColumnarCompression`
- **表格统一**: `TabularFormat`, `TabularReader`, `TabularWriter`, `TabularRowMapper`, `TabularReadContext`, `TabularWriteContext`
- **性能**: `ClassMetadataCache`, `ReflectCache`, `LRUCache`, `ASMFieldAccessor`, `ObjectPool`, `StylePool`, `GlobalObjectPool`
- **Spring**: `ExcelAutoConfiguration`, `ExcelProperties`, `ExcelConfig`, `ExcelTemplate`, `ExcelWebSupport`, `DownloadContext`
- **健康**: `ExcelHealthIndicator`
- **指标**: `ExcelMetrics`

#### ydsz-common-search（搜索引擎）
- **统一服务**: `UnifiedSearchService`, `SearchRequest`, `SearchResponse`, `SearchHit`, `SearchFilter`, `SearchAggregation`
- **策略 SPI**: `SearchStrategy`, `SearchEngineRegistry`, `ElasticsearchSearchStrategy`, `OpenSearchStrategy`, `SolrSearchStrategy`, `RediSearchStrategy`, `PgSearchStrategy`, `InMemorySearchStrategy`, `EngineCapability`
- **索引**: `IndexStrategy`, `IndexDocument`, `IndexOperation`, `SearchField`
- **Provider**: `SearchProvider`, `SearchProviderRegistry`, `SearchProviderContext`, `ProviderTypeBridge`
- **索引同步**: `IndexSyncService`, `IndexRebuildService`, `IndexSyncListener`, `SearchIndexEventBridge`, `IndexConsistencyChecker`
- **辅助**: `QueryParser`, `SearchTextProcessor`, `SearchCacheService`, `BusinessRanker`, `SuggestionService`, `SuggestStrategy`, `SearchSuggestion`
- **索引器**: `ContentIndexer`, `ContentExtractor`
- **分析**: `SearchAnalyticsService`, `SearchQualityTracker`
- **配置**: `SearchAutoConfiguration`, `SearchProperties`
- **健康**: `SearchHealthIndicator`
- **指标**: `SearchMetrics`

#### ydsz-common-sentry（监控告警）
- **SPI**: `AlertPublisher`, `LogPublisher`, `MetricsCollector`, `SlaCollector`, `TraceContext`
- **分布式追踪**: `DefaultTraceContext`, `OpenTelemetryTraceContext`, `SkyWalkingTraceContext`, `YdszOpenTelemetry`, `YdszSpan`, `OtelSdkBuilder`, `OtelExporterFactory`, `OtelSamplers`, `TailSamplingSpanProcessor`, `ErrorEventSpanProcessor`, `YdszSpanEnrichmentProcessor`, `OtelResources`, `OtelSemConv`, `SlowTraceDetector`
- **SLA**: `SlaDefinition`, `SlaMetric`, `SlaStep`, `SlaMetricAspect`, `DefaultSlaCollector`
- **指标采集**: `InMemoryMetricsCollector`, `MicrometerMetricsCollector`, `SystemMetricsCollector`
- **日志发布**: `AsyncLogPublisher`, `DualLogPublisher`, `ElkLogPublisher`, `LokiLogPublisher`, `LogEventSerializer`, `SentryLogbackLayout`, `LogEvent`, `LogLevel`
- **告警**: `AlertEvent`, `AlertSeverity`, `DefaultAlertPublisher`, `AlertConverger`, `NotifyAlertHandler`
- **熔断**: `CircuitBreaker`
- **配置**: `SentryAutoConfiguration`, `OtelAutoConfiguration`, `SentryProperties`
- **健康**: `SentryHealthIndicator`, `SystemResourceHealthIndicator`

### 2.7 L6 应用层（3个模块）

#### ydsz-common-base（HTTP 基座）
- **自动配置**: `BaseAutoConfiguration`, `BaseMvcConfiguration`, `BaseCorsProperties`, `BaseI18nConfiguration`, `BaseSecurityHeadersProperties`, `BaseTimezoneConfiguration`, `BaseTraceProperties`
- **全局响应**: `BaseGlobalResponseAdvice` (抽象基类，模板方法)
- **过滤器**: `BaseFilterOrders`, `TraceFilter`, `BaseRequestIdResponseFilter`, `SecurityHeadersFilter`, `AbstractContentCachingFilter`, `RequestContextCleanupFilter`
- **拦截器**: `BaseHttpInterceptor`, `BaseRequestLogInterceptor`, `RequestIdResolver`
- **认证**: `BaseAuthInfo` (基类，供 web/app 扩展)
- **OpenAPI**: `OpenApiAutoConfiguration`, `BaseOpenApiConfiguration`, `Knife4jAutoConfiguration`, `DocAutoConfiguration`, `DocProperties`, `DocSecurityConfiguration`, `DocConstants` (含 `OPENAPI_VERSION="3.0.3"`)
- **文档导出**: `DocExporter`, `AbstractDocExporter`, `DefaultDocExporter`, `MarkdownDocExporter`
- **Actuator**: `ConfigRegistryEndpoint`
- **国际化**: `I18nAutoConfiguration`, `messages_zh_CN.properties`, `messages_en_US.properties`
- **健康**: `BaseHealthIndicator`
- **指标**: `AbstractModuleMetrics`

#### ydsz-common-web（PC Web 基座）
- **MVC**: `WebMvcConfiguration`, `UserAgentConfiguration`
- **全局响应**: `GlobalResponseAdvice` (继承 `BaseGlobalResponseAdvice`)
- **认证**: `WebAuthInfo`, `WebAuthHandler`, `AuthHandlerFactory`, `WebAuthFilter`, `WebAccessDeniedHandler`, `WebAuthenticationEntryPoint`, `WebSecurityConfiguration`
- **过滤器**: `ContentCachingFilter`, `TraceIdResponseFilter`, `SecurityHeaderFilter`, `ResponseCompressionFilter`
- **会话**: `WebSessionAutoConfiguration`, `RedisHttpSessionImportSelector`
- **优雅停机**: `WebGracefulShutdownAutoConfiguration`
- **API 版本**: `@ApiVersion`, `ApiVersionCondition`, `ApiVersionRequestMappingHandlerMapping`, `ApiVersionAutoConfiguration`, `ApiVersionProperties`, `VersionStrategy`
- **Multipart**: `WebMultipartAutoConfiguration`, `WebMultipartProperties`
- **压缩**: `ResponseCompressionConfiguration`, `ResponseCompressionProperties`, `WebContentCacheProperties`
- **Webhook**: `WebhookDispatcher`, `DefaultWebhookDispatcher`, `WebhookSubscription`
- **配置族**: `WebCorsProperties`, `WebI18nConfiguration`, `WebTimezoneConfiguration`, `WebTraceProperties`, `WebOpenApiConfiguration`
- **健康**: `AbstractModuleHealthIndicator`, `WebHealthIndicator`
- **指标**: `WebMetrics`, `RequestLogInterceptor`

#### ydsz-common-app（移动端基座）
- **MVC**: `AppMvcConfiguration`
- **注解**: `@AppApi` (标识移动端专用接口)
- **全局响应**: `AppGlobalResponseAdvice` (继承 `BaseGlobalResponseAdvice`), `AppExceptionHandler`
- **认证**: `AppAuthInfo`, `AppAuthHandler`, `AppAuthFilter`
- **过滤器**: `AppContentCachingFilter`, `AppRequestIdResponseFilter`
- **拦截器**: `AppRequestLogInterceptor`, `RequestIdGenerator`
- **配置族**: `AppCorsProperties`, `AppI18nConfiguration`, `AppOpenApiConfiguration`, `AppTimezoneConfiguration`, `AppTraceProperties`, `AppContentCacheProperties`
- **健康**: `AppHealthIndicator`
- **指标**: `AppMetrics`

---

## 三、后端业务部署单元能力现状（10 个）

### 3.1 ydsz-gateway 网关 (端口 9000)

**模块类型**：部署单元（独立启动，WebFlux reactive 栈）

| 能力域 | 功能描述 | 关键类 |
|---|---|---|
| **路由分发** | 基于 Nacos 服务发现动态路由 | `NacosRouteDefinitionRepository`、`RouteConfig` |
| **鉴权拦截** | JWT 解析、X-User-Id/X-Tenant-Id/X-Trace-Id 内部头注入 | `AuthGlobalFilter`、`InternalHeaderSigner` |
| **限流熔断** | Sentinel Dashboard 对接、Redis 令牌桶多维限流（用户/IP/租户） | `RateLimitFilter`、`GatewaySentinelConfig` |
| **CORS** | 按环境白名单放行 | `SecurityHeadersProperties` |
| **IP 白名单** | 可配置 CIDR/单 IP 白名单 | `IpWhitelistFilter`、`IpBlacklistFilter` |
| **灰度路由** | 基于 X-Gray-Tag 头 + Nacos metadata.version | `GrayLoadBalancer`、`GrayLoadBalancerRequestFilter` |
| **WebSocket** | 转发到 message 服务通知通道 | `WebSocketAuthFilter` |
| **安全防护** | CSP/HSTS/COOP/COEP/CORP 安全响应头、请求体安全校验 | `PayloadValidationFilter`、`PathGuard` |
| **API Key** | API Key 认证（受保护路径） | `ApiKeyAuthFilter` |
| **链路追踪** | W3C Trace Context 传播 | `W3CTraceContextFilter` |

**依赖的 common 子模块**：common-core / common-exception / common-auth（细粒度依赖，不依赖 servlet 栈）

### 3.2 ydsz-userinfo 用户信息中心 (端口 9001)

| 能力域 | 功能描述 | 数据库表 | 关键 Controller |
|---|---|---|---|
| **登录认证** | 账号密码+图形验证码+LDAP/ADFS 域认证 | `ydsz_user_account` | `/api/v1/auth/login` `/logout` `/refresh` |
| **Token 管理** | JWT 签发/刷新/失效 | `ydsz_user_session` | `AuthController` |
| **RBAC** | 用户/角色/权限 6 要素 | `ydsz_user_role`、`ydsz_role_permission` | `/api/v1/user`、`/api/v1/role` |
| **组织架构** | 部门树形结构+公司+岗位 | `ydsz_department`、`ydsz_company`、`ydsz_post` | `/api/v1/dept`、`/api/v1/company`、`/api/v1/post` |
| **菜单权限** | 菜单树+按钮/API 权限码分类 | `ydsz_menu` | `/api/v1/menu` |
| **OAuth2** | 授权码模式 | `ydsz_user_2fa` | `/api/v1/oauth2/authorize` `/token` |
| **双因子认证** | TOTP 2FA | `ydsz_user_2fa` | `TwoFactorController` |
| **登录审计** | 登录失败计数+自动锁定（5 次失败锁 30 分钟） | `ydsz_login_audit` | - |
| **Feign 接口** | 内部 Feign 调用接口 | - | `/api/internal/user/query`、`/api/internal/dept/tree` |

**Feign 客户端**：`UserServiceClient`、`OrgQueryClient`

### 3.3 ydsz-system 系统基础服务 (端口 9002)

| 能力域 | 功能描述 | 数据库表 | 关键 Controller |
|---|---|---|---|
| **系统配置** | 参数配置 CRUD、按 key/group 查询、Redis 缓存、缓存穿透防护 | `ydsz_config` | `/api/v1/config` |
| **数据字典** | 字典类型+字典项、树形字典、缓存、版本管理 | `ydsz_dict_type`、`ydsz_dict_item`、`ydsz_dict_version` | `/api/v1/dict/type`、`/api/v1/dict/item` |
| **应用注册** | OAuth2 应用注册、BCrypt 密钥校验 | `ydsz_app_info` | `/api/v1/app` |
| **系统变量** | 业务级变量管理、Redis 缓存 | `ydsz_variable` | `/api/v1/variable` |
| **内部 API** | Feign 内部调用（POST body 传输，不暴露密钥） | - | `/api/internal/config/get`、`/api/internal/dict/item`、`/api/internal/app/validate` |

**Feign 客户端**：`ConfigClient`、`AppInfoClient`

### 3.4 ydsz-project 项目管理 (端口 9003)

**模块类型**：部署单元（独立启动，项目全生命周期核心）

| 业务域 | 数据库表 | 关键 Controller | 功能描述 |
|---|---|---|---|
| **商机管理** | `ydsz_project_opportunity`、`ydsz_project_opportunity_follow` | `ProjectOpportunityController` | A/B/C 分级、跟进、赢单/丢单 |
| **立项管理** | `ydsz_project_initiation`、`ydsz_project_budget_item` | `ProjectInitiationController` | WBS 预算、立项审批 |
| **合同管理** | `ydsz_project_contract`、`ydsz_project_contract_supplement`、`ydsz_project_contract_change`、`ydsz_project_contract_template` | `ProjectContractController` | 模板/补充/变更/风险 |
| **变更管理** | `ydsz_project_change` | `ProjectChangeController` | 5 类变更（范围/成本/合同/人员/进度） |
| **WBS 执行** | `ydsz_project_execution_wbs_task`、`ydsz_project_execution_time_entry` | `ExecutionWbsTaskController`、`ExecutionTimeEntryController` | 任务/工时/采购/费用 |
| **EVM 挣值** | `ydsz_project_evm_measure` | `EvmMeasureController` | PV/EV/AC 三量 + CPI/SPI + EAC/VAC 预测 |
| **成本管理** | `ydsz_project_cost_allocation`、`ydsz_project_cost_purchase` | `CostAllocationController`、`CostPurchaseController` | 成本归集 |
| **收入确认** | `ydsz_project_revenue`、`ydsz_project_invoice`、`ydsz_project_payment` | `ProjectRevenueController`、`ProjectInvoiceController`、`ProjectPaymentController` | 终验/里程碑/月结、开票/回款 |
| **风险管理** | `ydsz_project_execution_risk` | `ExecutionRiskController` | 风险登记、评估、应对 |
| **费率管理** | `ydsz_project_rate_card`、`ydsz_project_rate_internal` | `RateCardController`、`RateInternalController` | 对外报价 + 对内成本双费率 |
| **利润核算** | `ydsz_project_profit_simulation`、`ydsz_project_profit_snapshot` | `ProjectProfitSimulationController` | 多版本模拟、快照 |
| **客户信用** | `ydsz_project_customer_credit` | `ProjectCustomerCreditController` | A/B/C/D 评级 |
| **交付管理** | `ydsz_project_execution_delivery_item`、`ydsz_project_execution_delivery_standard`、`ydsz_project_gate_review` | `ExecutionDeliveryItemController` | CD1-CD5 门径、8 类交付物 |
| **项目结项** | `ydsz_project_execution_closure` | `ExecutionClosureController` | 正式/预/强制结项 |
| **售后管理** | `ydsz_project_warranty`、`ydsz_project_ops_ticket`、`ydsz_project_satisfaction` | `WarrantyController`、`OpsTicketController`、`SatisfactionController` | 质保/运维工单/满意度 |
| **资源管理** | `ydsz_project_billable_utilization_snapshot` | `BillableUtilizationSnapshotController` | 资源池/Bench 管理 |
| **对账管理** | `ydsz_project_reconcile_daily` | `ProjectReconcileDailyController` | 每日对账 |
| **费用报销** | `ydsz_project_expense` | `ProjectExpenseController` | 费用报销 |
| **预警管理** | `ydsz_project_alert_dispatch` | `AlertDispatchController` | 红/黄/绿阈值告警 |
| **报表分析** | - | `ProjectSearchController`、`ProjectExcelController` | 高级报表、Dashboard、数据导出 |

### 3.5 ydsz-workflow 工作流引擎 (端口 9006)

**模块类型**：部署单元（独立启动）
**平台约束**：⚠️ **仅 PC Web 端**（不支持移动端，详见 `.trae/rules/workflow-pc-only.md`）

| 能力域 | 功能描述 | 数据库表 |
|---|---|---|
| **流程定义** | YDSZ-Flow XML/JSON/BPMN 2.0 解析 | `ydsz_flow_definition`、`ydsz_flow_template`、`ydsz_flow_category`、`ydsz_flow_node` |
| **节点类型** | 开始/审批/加签/减签/转交/抄送/委派/代理 | `ydsz_flow_skip`、`ydsz_flow_timer` |
| **流程实例** | 发起/审批/驳回/撤回/催办 | `ydsz_flow_instance`、`ydsz_flow_his_instance` |
| **任务管理** | 待办/已办/抄送/委托 | `ydsz_flow_run_task`、`ydsz_flow_his_task` |
| **流程模板** | 模板库+版本管理+复制/导入/导出 | `ydsz_flow_template` |
| **DMN 决策** | 决策表引擎 | `ydsz_flow_dmn_table` |
| **表单引擎** | 表单设计、字段类型、校验规则 | - |
| **委托授权** | 代理人/时间窗 | `ydsz_flow_delegate_auth`、`ydsz_flow_delegate_log` |
| **流程监控** | 实例状态/节点耗时/拥堵分析 | `ydsz_flow_audit_log` |
| **SLA 管理** | P1-P4 SLA 倒计时+飞书/钉钉告警 | - |
| **事件订阅** | 流程开始/结束/节点进入/离开 | `ydsz_flow_event_subscription` |
| **自动触发** | 业务事件→发起流程 | `ydsz_flow_auto_trigger` |
| **灰度发布** | 流程模板 canary 发布 | - |
| **50 步模拟** | 流程图模拟运行 | - |
| **AI 辅助** | AI 辅助审批反馈 | `ydsz_flow_ai_feedback` |
| **第三方集成** | 企业微信/钉钉/飞书签名（位于 `thirdparty/` 目录，非独立模块） | `ydsz_flow_third_party_account` |

### 3.6 ydsz-message 消息通知引擎 (端口 9004)

#### 12 大渠道

| 渠道 | 协议 | Provider |
|---|---|---|
| SMS 短信 | 阿里云/腾讯云/华为云 | `mock`/`aliyun` |
| EMAIL 邮件 | SMTP (SSL/STARTTLS) | 内置 |
| PUSH 推送 | 个推/极光/友盟 | `mock`/`getui` |
| INAPP 站内 | WebSocket | 内置 |
| WEBHOOK 通用 | HTTP/HTTPS | 内置 |
| DINGTALK 钉钉 | 群机器人+加签 | 内置 |
| WECOM 企业微信 | 群机器人 | 内置 |
| FEISHU 飞书 | 群机器人+加签 | 内置 |
| WX_MINI 微信小程序 | 订阅消息 | `mock`/`wechat` |
| ALIPAY_MINI 支付宝小程序 | 模板消息 | `mock`/`alipay` |
| TCP TCP 推送 | 长连接 | 内置 |
| WECHAT_WORK 企业微信应用 | 应用消息 | 内置 |

#### 核心能力

| 能力 | 描述 |
|---|---|
| 模板管理 | i18n/版本/审核/场景/`${var}`嵌套变量+`{{#if}}`条件+`{{#each}}`循环 |
| 站内通知 | 优先级/聚合/撤回/跳转 |
| 用户偏好 | 免打扰/频率上限/聚合/语言 |
| 订阅管理 | 主题级订阅/退订 |
| 消息路由 | 条件路由/通道降级/多级降级链 |
| 限流 | Redisson 令牌桶 + Resilience4j 多维限流 |
| 灰度 | 按用户标签/比例灰度/A/B 自动胜出 |
| 异步 | RocketMQ 生产/消费/死信 + Redis SET NX EX 幂等 |
| 回执 | 送达/已读/点击/失败/超时（5min 主动拉取+30min 超时补偿） |
| 消息编排 | DAG 拓扑排序/条件分支/失败策略/流程级超时控制 |
| 智能定时 | 用户活跃度画像+DND 免打扰+时区感知 |
| 批量发送 | ParallelBatchSender 通道级线程池+Semaphore 流控 |
| 敏感词过滤 | DFA 字典树算法 O(n) |

**数据库表**：`ydsz_msg_log`、`ydsz_msg_template`、`ydsz_msg_notification`、`ydsz_msg_receipt`、`ydsz_msg_preference`、`ydsz_msg_subscription`、`ydsz_msg_route_rule`、`ydsz_msg_canary` 等 24 张表

### 3.7 ydsz-cronjob 分布式任务调度引擎 (端口 9005)

| 能力 | 描述 |
|---|---|
| Leader 选举 | Redis 分布式锁+租约续期，多实例保证任务不重复执行 |
| 多分区调度 | 多 Active Leader 分区调度，提升吞吐量 |
| 节点发现 | Nacos 服务发现 / DB 心跳表 |
| 任务调度 | Cron 表达式+固定频率+固定延迟+精准调度（时间轮预加载） |
| 分片广播 | 单机串行/广播并行/分片 MapReduce/加权分片 |
| 故障转移 | FailoverScanner 定时扫描，节点宕机自动转移 |
| 自愈系统 | SelfHealingScanner 检测卡死任务自动修复+重新派发 |
| 租户隔离 | tenant/job_group 隔离策略 |
| 告警通道 | 消息中心 Feign + common-notify IM 直推 |
| 告警降噪 | 时间窗口聚合+自动升降级通知渠道 |
| 日志归档 | 每天凌晨 3 点清理 30 天前日志 |
| 配额管理 | 租户级任务数/并发/日执行量 |
| HTTP 任务 | 内置 HTTP 调用处理器 |
| 脚本沙箱 | 进程级/Docker 容器级隔离执行 Shell/Python |
| 调度器-执行器分离 | Leader 仅调度，Worker 执行 |
| DAG 任务 | DAG 定义/实例/节点实例 |

**数据库表**：`ydsz_job`、`ydsz_job_glue`、`ydsz_job_task`、`ydsz_job_history`、`ydsz_job_dag`、`ydsz_job_log`、`ydsz_job_node`、`ydsz_job_alert_rule` 等 18 张表

### 3.8 ydsz-agent AI Agent 服务 (端口 9010)

#### 核心能力（5 种 Agent 执行器 + 全链路能力）

| 能力 | 描述 |
|---|---|
| LLM Provider 抽象 | `LlmClient` 统一接口 + OpenAI 兼容实现（GPT/DeepSeek/Qwen/Moonshot/智谱） |
| 同步对话 | `POST /agent/chat` 完整请求/响应 |
| 流式对话（SSE） | `POST /agent/chat/stream` 逐 token 推送 |
| 对话管理 | Conversation 聚合 + ChatMessage 值对象 + Redis 滑动窗口记忆 |
| Prompt 模板 | PromptTemplate + `#{var}` 变量替换 + Prompt 管理服务 |
| 多模型路由 | LlmClientRouter + Fallback 降级 |
| Token 计量 | 每次 LLM 调用记录 prompt/completion/total tokens |
| Simple Agent | 单轮对话，无工具调用（`SimpleAgentExecutor`） |
| ReAct Agent | 推理-行动循环 + Tool Calling（`ReActAgentExecutor`） |
| Router Agent | 路由分发到子 Agent（`RouterAgentExecutor`） |
| Plan-Execute Agent | 先规划后执行，复杂任务分解（`PlanExecuteAgentExecutor`） |
| RAG Agent | 检索增强生成，结合知识库回答（`RagAgentExecutor`） |
| DAG 编排 | DSL 解析 + DAG 执行器（`DagDslParser` / `DagOrchestrationExecutor`） |
| Tool Calling | `@Tool` 注解 + 工具注册中心（`ToolRegistry` / `ToolExecutor`） |
| RAG 知识增强 | 文档摄入 Pipeline + 向量存储 + 检索（`RagService` / `DocumentIngestionService`） |
| 人工审批 | Agent 执行中暂停等待人工审批（`HumanApprovalService`） |
| 调试器 | 断点/单步/快照/恢复（`AgentDebuggerService`） |
| 成本分析 | LLM 调用成本统计与分析（`CostAnalysisService`） |
| 安全护栏 | 输入/输出 Guardrail + PII 脱敏（`GuardrailService`） |
| 请求防护 | 限流/内容安全/越狱检测（`AgentRequestGuard`） |
| 健康检查 | `/actuator/health` 暴露 LLM Provider + Memory + RAG 状态 |
| 指标埋点 | 对话次数/Token 用量/延迟（`AgentMetrics`） |

#### Web 层 Controller（8 个）

| Controller | 路径 | 端点 |
|---|---|---|
| `ChatController` | `/agent/chat` | 同步/流式对话/历史 |
| `AgentController` | `/agent` | Agent CRUD/启停/状态 |
| `AgentDefinitionController` | `/agent/definition` | Agent 定义 CRUD/版本 |
| `AgentMetadataController` | `/agent/metadata` | 元数据/模型配置/Token 用量 |
| `DagController` | `/agent/dag` | DAG 编排定义/触发/状态 |
| `DebugController` | `/agent/debug` | 调试器断点/单步/快照/恢复 |
| `HumanApprovalController` | `/agent/approval` | 人工审批提交/查询/结果 |
| `RagController` | `/agent/rag` | 文档上传/检索/索引管理 |

### 3.9 ydsz-literule 规则引擎 (独立微服务)

**模块类型**：独立微服务（独立部署、独立 JVM 进程、注册到 Nacos）
**端口**：9008（按构建顺序 9/10）

#### 7 种规则类型

| 类型 | 实现类 | 适用场景 |
|---|---|---|
| Expression 表达式 | `ExpressionRule` | 基于 LiteExpr 表达式动态评估 |
| DecisionTable 决策表 | `DecisionTableRule` | 二维表规则，Excel 导入导出 |
| CrossDecisionTable 交叉决策表 | `CrossDecisionTableRule` | 多维交叉决策表 |
| DecisionTree 决策树 | `DecisionTreeRule` | 多层 if-else 树规则 |
| Scorecard 评分卡 | `ScorecardRule` | 信用评分/风险评分多维加权 |
| Script 脚本 | `ScriptRule` | JSR-223 脚本规则 |
| Static 静态 | `StaticRule` | 静态常量规则 |
| CEP 复杂事件处理 | `CEPEngine` | 滑动窗口+模式匹配 |

#### 核心能力清单

| 能力 | 描述 |
|---|---|
| LiteExpr 表达式引擎 | 词法/语法分析、AST 编译缓存、常量折叠、短路求值、AST 级安全沙箱 |
| 规则链编排 | 8 种语义：THEN/WHEN/IF/ELSE/SWITCH/FOR/WHILE/BREAK，支持 DSL+可视化画布 |
| 多级缓存 | Caffeine（L1 本地）+ Redis（L2 分布式）装饰器模式 |
| 热加载 | DB/Nacos/Apollo/ZooKeeper/Redis/File 多源动态刷新 |
| 版本管理 | 版本快照+Diff+一键回滚 |
| dry-run 仿真 | 不实际执行，只评估结果 |
| 多级审批流 | 草稿→审核→上线，SINGLE/COUNTERSIGN/SEQUENCE 三种审批类型 |
| 灰度发布 | 按 canaryRatio 分流到候选版本 |
| A/B 测试 | 自动回滚策略+效果评估+回滚历史 |
| 规则冲突检测 | 条件重复/严重度矛盾/命名冲突，可阻塞保存 |
| 执行回放 | 按 traceId/版本/自定义表达式回放 |
| 断点调试 | IDE 风格在线调试：断点设置/单步/快照/恢复 |
| 业务测试用例 | 独立于 JUnit 的回归测试体系 |
| 压测 | QPS/P50/P95/P99 测量 |
| 分布式执行 | 一致性哈希分片+Redis Pub/Sub 广播 |
| 动态事实采集 | 评估前从 DB/Redis/HTTP 采集事实 |
| 动作分发 | 规则触发后联动通知/cronjob/工作流 |

### 3.10 ydsz-nextwiki 网盘知识库服务（独立微服务）

**模块类型**：独立可部署微服务（含 ydsz-nextwiki-web/src/main/resources/bootstrap.yml + application.yml，注册到 Nacos）
**端口**：9007（按构建顺序 8/10）

| 能力 | 描述 |
|---|---|
| 文件管理 | 上传（SHA-256 秒传）、下载（断点续传）、复制、移动、重命名、删除 |
| 目录树 | 创建/移动/重命名/递归路径更新 |
| 版本控制 | 版本历史、回滚、最多保留 20 版本 |
| 分享与 ACL | 分享链接（密码/过期/次数限制）、ACL 权限（读/写/删/分享/下载） |
| 搜索 | 文件名+路径+标签+内容全文搜索、索引同步、重建 |
| 预览 | Office→PDF（LibreOffice）、图片缩略图、直接预览 |
| 配额 | 用户/租户/项目级配额管理 |
| 回收站 | 30 天保留、恢复、永久删除、自动清理 |
| 标签 | 创建/绑定/推荐 |
| 批量操作 | 批量上传、ZIP 导入（炸弹防护）、文件夹打包下载 |
| 分片上传 | 大文件分片上传、断点续传 |
| AI 摘要 | TextRank 本地模式 + LLM 模式 |
| 在线编辑 | WOPI 协议（OnlyOffice/Collabora 集成） |
| 文件评论 | 评论/回复/批注 |
| 文件锁定 | Check-out/Check-in 防并发编辑 |
| 安全 | ClamAV 病毒扫描、OCR 文字识别、CDN 集成 |

---

## 四、前端应用能力现状（10 个应用）

### 4.1 前端架构基线

| 维度 | 选型 |
|---|---|
| Monorepo 工具 | pnpm workspace + Turborepo |
| 微前端方案 | Qiankun（1 主应用 main + 9 子应用） |
| 主子应用通信 | Pinia 全局状态 + Qiankun globalState |
| 路由模式 | history |
| 状态管理 | Pinia |
| UI 组件库 | Element Plus + vxe-table + ECharts + @core/ui-kit/shadcn-ui（100+ 组件） |
| HTTP 客户端 | axios（封装于 effects/request 与 effects/shared-auth） |
| 构建工具 | Vite 5.x |
| TypeScript | 全量 TS |
| 共享包命名规范 | `@ydsz/xxx`（公共功能）与 `@ydsz-core/xxx`（核心能力）两种命名空间并存 |
| 包管理 | workspace:* 协议 + catalog: 版本目录 |

### 4.2 main 主应用

**应用类型**：主入口应用（基于 qiankun 微前端架构，位于 `ydsz-frontend/main/`）

| 模块 | 功能 | 关键文件 |
|---|---|---|
| **登录认证** | 账号密码登录、二维码登录、验证码登录、忘记密码、注册 | `views/_core/authentication/` |
| **仪表盘** | 数据分析图表、工作空间、访问统计、趋势分析 | `views/dashboard/` |
| **子应用管理** | 子应用加载、全局状态管理 | `qiankun/`、`views/_core/subapp/` |
| **路由守卫** | 权限校验、路由拦截 | `router/guard.ts`、`router/access.ts` |
| **布局系统** | 认证布局、基础布局、菜单系统 | `layouts/` |
| **全局搜索** | 全局搜索组件 | `components/global-search.vue` |
| **国际化** | 中英文语言包 | `locales/langs/` |

### 4.3 9 个子应用

| 子应用 | 路径 | 核心功能模块 |
|---|---|---|
| **project-web** | `apps/project-web/` | 商机、立项、合同、EVM、成本、收入、发票、付款、风险、费率、利润、客户信用、执行、费用、预算 |
| **workflow-web** | `apps/workflow-web/` | 流程分类、流程实例、审批任务、流程模板、委托授权、常用意见（⚠️ 仅 PC Web） |
| **system-web** | `apps/system-web/` | 应用管理、系统配置、字典类型、字典项、系统变量 |
| **userinfo-web** | `apps/userinfo-web/` | 用户、角色、菜单、部门、公司、岗位、语言管理 |
| **message-web** | `apps/message-web/` | 消息发送、模板管理、批量发送、用户偏好、路由规则、死信队列、站内通知 |
| **cronjob-web** | `apps/cronjob-web/` | 任务管理、任务分组、DAG 管理、执行日志、告警管理、连接器 |
| **agent-web** | `apps/agent-web/` | Agent 管理、审批配置、DAG 编排、流程定义、RAG 知识库 |
| **literule-web** | `apps/literule-web/` | 规则管理、审计日志、断点调试、CEP 模式、DSL 管理、变量管理 |
| **nextwiki-web** | `apps/nextwiki-web/` | 文件管理、分享管理、标签管理、配额管理、评论管理 |

---

## 五、前端公共组件库 (comm/) 与构建配置 (conf/)

### 5.1 comm/ 共享包（29 个子包）

#### @core 核心能力包（@ydsz-core/xxx，12 个）

| 子包 | 包名 | 功能 |
|---|---|---|
| @core/base/design | @ydsz-core/design | 设计 Token、BEM SCSS 架构、主题变量（dark/default）、全局 CSS（nprogress/transition） |
| @core/base/icons | @ydsz-core/icons | 图标创建器（create-icon）、Lucide 图标集成 |
| @core/base/shared | @ydsz-core/shared | 通用工具（cn/date/diff/dom/download/inference/letter/merge/nprogress/resources/state-handler/to/tree/unique/update-css-variables/util/window）、StorageManager 缓存、color 颜色转换、global-state 全局状态 |
| @core/base/typings | @ydsz-core/typings | 全局类型（app/basic/helper/menu-record/tabs/vue-router） |
| @core/composables | @ydsz-core/composables | Vue 3 Composables（use-is-mobile/use-layout-style/use-namespace/use-priority-value/use-scroll-lock/use-sortable/use-simple-locale） |
| @core/preferences | @ydsz-core/preferences | 偏好设置（config/constants/preferences.ts/update-css-variables/use-preferences） |
| @core/ui-kit/form-ui | @ydsz-core/form-ui | 高级表单（form-api/form-render/form-field/form-label/form-actions/remi-form/use-remi-form） |
| @core/ui-kit/layout-ui | @ydsz-core/layout-ui | 布局组件（layout-header/sidebar/content/footer/tabbar + widgets + use-layout） |
| @core/ui-kit/menu-ui | @ydsz-core/menu-ui | 菜单组件（menu/sub-menu/normal-menu/collapse-transition/menu-badge） |
| @core/ui-kit/popup-ui | @ydsz-core/popup-ui | 弹窗组件（alert/drawer/modal + popup-api + use-modal/use-drawer） |
| @core/ui-kit/shadcn-ui | @ydsz-core/shadcn-ui | Shadcn UI 组件库（100+ 组件：accordion/alert-dialog/avatar/badge/breadcrumb/button/card/checkbox/context-menu/dialog/dropdown-menu/form/hover-card/input/label/number-field/pagination/pin-input/popover/radio-group/resizable/scroll-area/select/separator/sheet/switch/tabs/textarea/toggle/toggle-group/tooltip/tree 等） |
| @core/ui-kit/tabs-ui | @ydsz-core/tabs-ui | 标签页组件（tabs/tabs-chrome/tabs-view + widgets） |

#### @ydsz 公共功能包（@ydsz/xxx，17 个）

| 子包 | 包名 | 功能 |
|---|---|---|
| constants | @ydsz/constants | 全局常量（core.ts） |
| effects/access | @ydsz/access | 权限访问控制（useAccess/directive/accessible） |
| effects/common-ui | @ydsz/common-ui | 通用 UI 组件 |
| effects/hooks | @ydsz/hooks | 通用 Hooks（use-refresh/use-tabs/use-watermark） |
| effects/layouts | @ydsz/layouts | 布局（basic） |
| effects/monitor | @ydsz/monitor | 前端监控（setup/web-vitals） |
| effects/plugins | @ydsz/plugins | 插件系统 |
| effects/request | @ydsz/request | HTTP 请求客户端封装 |
| effects/shared-auth | @ydsz/shared-auth | 共享认证（request/types） |
| effects/shared-business | @ydsz/shared-business | 跨微前端子应用复用的业务 UI 组件（依赖 common-ui + element-plus） |
| icons | @ydsz/icons | 图标库（iconify + svg + empty-icon） |
| locales | @ydsz/locales | 国际化（i18n + zh-CN/en-US 语言包） |
| preferences | @ydsz/preferences | 系统偏好（基于 @ydsz-core/preferences） |
| stores | @ydsz/stores | Pinia 状态管理（access/tabbar/user 三大 store + setup） |
| styles | @ydsz/styles | 全局样式（element-plus 覆盖 + global SCSS） |
| types | @ydsz/types | 类型定义（api-response/base-entity/user） |
| utils | @ydsz/utils | 工具函数（helpers/reset-routes） |

### 5.2 conf/ 构建配置包（8 个子包）

| 子包 | 功能 |
|---|---|
| conf/node-utils | Node 端工具（constants/date/fs/git/hash/monorepo/path/prettier/spinner） |
| conf/tailwind-config | Tailwind CSS 配置（plugins/postcss 集成） |
| conf/tsconfig | TypeScript 配置（base/library/node/web/web-app） |
| conf/vite-config | Vite 构建配置（application/common/library + plugins：inject-app-loading/archiver/extra-app-config/importmap/inject-metadata/license/print/vxe-table + utils/env） |
| conf/lint-configs/eslint-config | ESLint 配置（command/comments/disableds/ignores/import/javascript/jsdoc/jsonc/node/perfectionist/prettier/regexp/test/turbo/typescript/unicorn/vue） |
| conf/lint-configs/prettier-config | Prettier 配置 |
| conf/lint-configs/stylelint-config | Stylelint 配置 |
| conf/lint-configs/commitlint-config | Commitlint 配置 |

---

## 六、核心业务能力地图

### 6.1 项目全生命周期

```
商机管理 → 立项管理 → 合同管理 → 执行管理 → 成本归集 → 收入确认 → 利润核算 → 开票回款 → 项目结项 → 售后管理
    │          │          │          │          │          │          │          │          │
    ▼          ▼          ▼          ▼          ▼          ▼          ▼          ▼          ▼
  A/B/C     WBS预算    模板/补充    WBS/工时   成本分配    终验/里程碑  双费率    发票/付款   正式/预/强制
  分级      立项审批    变更/风险    采购/费用   成本采购    月结收入    利润模拟   客户信用   质保/工单
                                                                                       满意度
```

### 6.2 核心引擎能力

| 引擎 | 核心能力 | 依赖关系 |
|---|---|---|
| **工作流引擎** | BPMN 2.0 解析、流程实例、任务审批、SLA、灰度、监控 | 依赖 userinfo（审批人）、message（通知） |
| **规则引擎** | 表达式/决策表/决策树/评分卡、规则链、热加载、熔断 | 可选联动 cronjob/workflow |
| **消息引擎** | 12 渠道、模板、偏好、限流、灰度、回执、编排 | 依赖 common-notify |
| **调度引擎** | Leader 选举、Cron 调度、分片广播、故障转移、DAG | 依赖 message（告警） |

### 6.3 横切关注点

| 关注点 | 实现方式 |
|---|---|
| **认证鉴权** | JWT + RBAC + @DataScope + TOTP 2FA |
| **数据权限** | 6 模式（全部/本部门/本部门及下级/本人/自定义/无权限） |
| **审计日志** | @OperationLog + @Audit、Disruptor 异步批写 |
| **安全防护** | AES-256+SM4 加密、7 种脱敏、CSRF、SQL 注入防护、验证码 |
| **分布式锁** | Redisson 4 种实现、幂等注解 |
| **缓存策略** | 多级缓存（Caffeine L1 + Redis L2）、三防（穿透/击穿/雪崩） |
| **配置管理** | Nacos 动态配置、敏感配置加密 |
| **链路追踪** | TraceId Filter + Logback MDC + W3C Trace Context |
| **异常处理** | 统一异常体系、ProblemDetail、i18n |
| **可观测性** | Prometheus 指标、Actuator 健康检查、ELK 日志聚合 |

---

## 七、待完善项与后续优化建议

### 7.1 已知待完善项

| 模块 | 待完善项 | 优先级 |
|---|---|---|
| **ydsz-agent** | ReAct Agent、Tool Calling、RAG、安全护栏（P1-P4） | P1 |
| **ydsz-literule** | 单元测试补齐、Feign 客户端、启动类/配置文件 | P1 |
| **ydsz-workflow** | 移动端审批替代方案（轻审批 H5） | P2 |
| **ydsz-nextwiki** | Web 控制台 UI 页面补齐（后端 API 已完备） | P2 |
| **ydsz-frontend** | i18n 迁移（部分业务页面待完成） | P2 |
| **ydsz-common** | 跨服务链路追踪（SkyWalking/OpenTelemetry） | P3 |

### 7.2 代码优化建议（待用户决策是否执行）

| 建议 | 影响范围 | 预期收益 |
|---|---|---|
| 补齐 literule 核心引擎单测 | ydsz-literule | 提高代码质量，降低回归风险 |
| 补齐 literule Feign 客户端 | ydsz-literule、ydsz-project | 简化跨服务调用 |
| 前端 i18n 全量迁移 | ydsz-frontend | 国际化覆盖完整 |
| 跨服务链路追踪接入 | 全链路 | 问题定位效率提升 |
| agent 模块 P1 能力开发 | ydsz-agent | AI 能力增强 |

---

## 八、总结

### 8.1 项目规模指标

| 维度 | 数量 |
|---|---|
| 后端部署单元 | 10 个（gateway/userinfo/system/project/message/cronjob/workflow/agent/literule/nextwiki） |
| 后端公共库 | common 公共库（**30 子模块**，L1-L6 分层） |
| 数据库表 | 126 张 + 5 视图 |
| 前端应用 | 10 个（1 主应用 main + 9 子应用：project/workflow/system/userinfo/message/cronjob/agent/literule/nextwiki） |
| 前端共享包 | comm/ 29 个子包（12 个 @ydsz-core 包 + 17 个 @ydsz 包）+ conf/ 8 个构建配置包（4 个核心 + 4 个 lint-configs） |

### 8.2 技术亮点

1. **自研核心引擎**：工作流引擎、规则引擎、消息引擎、调度引擎
2. **DDD 分层架构**：api/domain/infra/server/web 五层严格分离
3. **L1-L6 公共库分层**：30 个子模块，依赖方向严格自下而上
4. **SPI 扩展点**：30+ 扩展点，支持业务方无侵入扩展
5. **多租户隔离**：从数据、缓存、任务调度全链路租户隔离
6. **高性能设计**：Disruptor 无锁队列、多级缓存、流式 Excel 读写、ASM 字节码优化
7. **微前端架构**：qiankun 基座 + 独立 SPA 子应用
8. **模板方法模式**：common-base 抽象基座 + common-web/common-app 对称实现

---

> 本模型由代码阅读生成，反映了当前代码仓库的实际能力状态。
> 任何变更请走 PR + Code Review 流程。
