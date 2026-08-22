# ydsz-common-redis

> Redis 服务增强与基础数据层公共模块（L4 基础数据层）

提供按数据类型拆分的 Ops 子组件（String / Hash / Collection / Geo / Pipeline / Pub-Sub / Stream / 事务）、分布式限流器（固定窗口 / 滑动窗口 / 令牌桶）、注解驱动缓存、租户级 Key 前缀器、Key 过期事件监听、统一异常处理、可观测性指标等开箱即用能力，是所有业务模块缓存与分布式协调的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 Redis 操作封装、分布式限流、注解缓存、Key 过期监听等能力 |
| **依赖** | common-core、common-json、common-util；spring-boot-starter-data-redis、commons-pool2、lettuce-core；可选依赖 jedis、micrometer-core、redisson-spring-boot-starter、spring-boot-health |
| **版本** | 1.4.1 |

## 核心能力

### 1. Redis 操作门面与 Ops 拆分

| 类 | 说明 |
|---|---|
| `RedisStringOps` | String + Bitmap 操作（含 key prefix 拼接、慢操作检测） |
| `RedisHashOps` | Hash 操作 |
| `RedisCollectionOps` | Set + List + ZSet 操作 |
| `RedisGeoOps` | Geo + HyperLogLog 操作 |
| `RedisAdvancedOps` | Pipeline + Lua 脚本操作聚合接口 |
| `RedisPipelineOps` / `RedisPipelineOpsImpl` | Pipeline 批量操作 |
| `RedisTransactionOps` | 事务操作（MULTI / EXEC / DISCARD） |
| `RedisPubSubOps` | 发布订阅 |
| `RedisStreamOps` | Redis Stream 消息流 |
| `CacheProvider` | 缓存提供者接口（供多级缓存使用） |

> 注：9 类 Ops 组件可直接注入使用，无统一的 RedisService 门面。

### 2. 分布式限流器

| 类 | 说明 |
|---|---|
| `RedisRateLimiter` | 基于 Redis + Lua 的分布式限流器，提供三种工业级算法 |

支持三种限流算法（均基于 Lua 脚本保证原子性）：

| 算法 | 精度 | 突发容忍 | 适用场景 |
|---|---|---|---|
| 固定窗口 | 低 | 2x 突发 | 粗粒度限流 |
| 滑动窗口（ZSET） | 高 | 无 | 严格限流 |
| 令牌桶 | 高 | 可配置 | 流量整形 |

### 3. 布隆过滤器

> 说明：布隆过滤器能力当前**未提供实现**（历史版本曾有 `RedisBloomFilter` / `BloomFilterService`，已移除）。如需使用请基于 BitMap + Lua 自行封装。

### 4. 延迟队列

> 说明：延迟队列能力当前**未提供实现**（历史版本曾有 `RedisDelayedQueue` / `DelayedTask`，已移除）。建议基于 ZSET 或 RocketMQ 延时消息实现。

### 5. 缓存防护

| 类 | 说明 |
|---|---|
| `NullValueCacheHelper` | 空值缓存助手（`__NULL__` 占位，TTL 由 `nullValueTtlSeconds` 控制） |
| `RedisRateLimiter` | 三算法限流器（供防击穿场景配合使用） |

缓存防护策略由 `@YdszCacheable` 注解切面（`YdszCacheableAspect`）承载：防穿透（空值缓存）、防击穿（分布式互斥锁）、防雪崩（随机 TTL）。

### 6. 雪花 ID 生成器

> 说明：Redis 版雪花 ID（`RedisSnowflakeIdGenerator` / `RedisWorkerIdRegistry`）**已移除**。WorkerId 分配由 `ydsz-common-util` 的 `WorkerIdAllocator`（SPI：PodOrdinal → IpHash）承担。

### 7. 注解驱动缓存

| 注解 / 类 | 说明 |
|---|---|
| `@YdszCacheable` | ydsz 分布式缓存注解（增强 Spring `@Cacheable`） |
| `@YdszCachePut` | 缓存更新注解 |
| `@YdszCacheEvict` | 缓存清除注解 |
| `YdszCacheableAspect` | 缓存切面实现（SpEL 解析 + 分布式锁防击穿 + 空值缓存防穿透 + 随机 TTL 防雪崩） |
| `RedisRetryInterceptor` | Redis 操作重试拦截器（指数退避，默认仅读操作重试） |
| `RedisOperation` | Redis 操作类型声明注解（READ / WRITE / UNKNOWN，供异常处理拦截器决策） |

`@YdszCacheable` 增强项：自定义 TTL、空值缓存防穿透（默认 60s）、分布式互斥锁防击穿、随机过期防雪崩。

### 8. 序列化与 Key 管理

| 类 | 说明 |
|---|---|
| `YdszJsonRedisSerializer` | 基于 YdszJson 的高性能 Redis 序列化器（支持 Java 8 时间类型） |
| `RedisKeyPrefixProvider` | Key 前缀提供者接口（业务模块实现，统一 Key 命名规范） |
| `RedisKeyFormatter` | Redis Key 前缀格式化器（统一拼接规范，支持分类前缀） |
| `RedisKeyNamingConvention` | Key 命名约定策略 |
| `TenantRedisKeyPrefixer` | 租户级 Redis Key 前缀器（格式 `{tenantId}:{originalKey}`，超级管理员不添加前缀） |
| `RedisKeysEnum` | Redis Key 模板枚举管理（统一 `ydsz:` 前缀，模板化 `{}` 占位符，分组管理 + 默认 TTL） |

### 9. Key 过期事件监听

| 类 / 注解 | 说明 |
|---|---|
| `@RedisKeyExpireListener` | Redis Key 过期事件监听注解（基于 Redis Keyspace Notifications，需开启 `notify-keyspace-events Ex`） |
| `RedisKeyExpirationEvent` | Redis Key 过期事件封装（含 expiredKey / businessKey / occurredAt） |

### 10. 集群与连接配置

| 类 | 说明 |
|---|---|
| `RedisConfiguration` | Redis 自动配置（在 `DataRedisAutoConfiguration` 之前加载），注册 RedisTemplate / Ops Bean / 健康检查等 |
| `RedisConnectionFactoryConfigurer` | 连接工厂配置器，根据 `client.type` 自动选择 Jedis / Lettuce |
| `RedisClientType` | 客户端类型枚举（JEDIS / LETTUCE） |
| `ClusterSlotUtil` | Redis Cluster Slot 工具 |

### 11. 统一异常处理

| 类 | 说明 |
|---|---|
| `RedisOperationExceptionHandler` | Redis 操作统一异常处理拦截器（Spring AOP，将 Spring Data Redis 常见异常统一转换为内部异常体系） |
| `RedisOperationException` | Redis 操作异常（枚举） |
| `RedisConnectionException` | Redis 连接异常（枚举，可恢复异常） |
| `RedisBusinessException` | Redis 业务异常（枚举，不可恢复异常） |
| `RedisScriptConstants` | Redis Lua 脚本常量 |

异常转换规则：连接失败/超时 → 可恢复异常（可用于重试判断）；序列化/参数错误 → 不可恢复异常。

### 12. 故障处理策略

| 类 | 说明 |
|---|---|
| `FailOpenPolicy` | 故障处理策略枚举（FAIL_OPEN / FAIL_CLOSED / FAIL_THROW） |

各子组件可单独配置故障策略，未单独配置时使用全局 `failurePolicy`（默认 FAIL_OPEN）：

- 限流器默认 `FAIL_CLOSED`（安全场景推荐）

### 13. 可观测性

| 类 | 说明 |
|---|---|
| `RedisMetricsCollector` | Redis 操作指标收集器（Timer.Sample 低开销测量） |
| `RedisMetricsConfiguration` | 指标采集自动配置（`MeterRegistry` 存在时激活） |
| `RedisHealthIndicator` | Redis 健康检查 |

### 14. 租户隔离

| 类 | 说明 |
|---|---|
| `TenantRedisKeyPrefixer` | 租户级 Key 前缀器，通过包装 `RedisSerializer` 在序列化 key 时自动添加 `{tenantId}:` 前缀 |

特性：超级管理员（tenantId = null 或 "0"）不添加前缀，仅对 key 序列化生效，value 不受影响。

### 15. 多级缓存

| 类 | 说明 |
|---|---|
| `MultiLevelCacheProvider` | 多级缓存提供者（L1 Caffeine 本地缓存 + L2 Redis 远程缓存），@since 1.0.0 |
| `MultiLevelCacheAutoConfiguration` | 多级缓存自动配置，条件：Caffeine 在 classpath + `ydsz.redis.multilevel.enabled=true` |

读取流程：L1（Caffeine）→ L2（Redis）→ Supplier（回源）；写入流程：写 L2 → 失效 L1；删除流程：删 L2 → 失效 L1。L1 TTL 应显著小于 L2 TTL 以保证数据新鲜度。

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-redis</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  redis:
    host: localhost
    port: 6379
    password: xxx
    database: 0
    timeout: 3000
    client:
      type: lettuce       # lettuce / jedis
    key-prefix: ydsz       # 全局 Key 前缀
    failure-policy: FAIL_OPEN
```

### 3. 使用 Ops 子组件

```java
import com.njydsz.common.redis.service.ops.RedisStringOps;

// 直接注入子组件（推荐）
stringOps.set("key", "value");
String value = stringOps.get("key", String.class);
```

## 配置项

### RedisProperties（`ydsz.redis.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.redis.failure-policy` | `FAIL_OPEN` | 全局故障处理策略（FAIL_OPEN / FAIL_CLOSED / FAIL_THROW） |
| `ydsz.redis.host` | `localhost` | Redis 服务器地址（单机模式） |
| `ydsz.redis.port` | `6379` | Redis 服务器端口 |
| `ydsz.redis.password` | - | Redis 密码 |
| `ydsz.redis.database` | `0` | 数据库索引（单机模式） |
| `ydsz.redis.timeout` | `3000` | 连接超时时间（毫秒） |
| `ydsz.redis.timeout-duration` | - | 连接超时时间（Duration 格式，优先于 timeout） |
| `ydsz.redis.user` | - | Redis 用户名 |
| `ydsz.redis.ssl-enabled` | `false` | 是否启用 SSL |
| `ydsz.redis.key-prefix` | `""` | 全局 Key 前缀（默认使用 spring.application.name） |
| `ydsz.redis.null-value-ttl-seconds` | `1800` | 空值缓存 TTL（秒，缓存防护使用） |
| `ydsz.redis.lettuce.shutdown-timeout` | `100` | Lettuce 关闭超时时间 |
| `ydsz.redis.cluster.nodes` | - | 集群节点列表 |
| `ydsz.redis.cluster.max-redirects` | `3` | 集群最大重定向次数 |
| `ydsz.redis.sentinel.master` | - | 哨兵 master 名称 |
| `ydsz.redis.sentinel.nodes` | - | 哨兵节点列表 |
| `ydsz.redis.sentinel.password` | - | 哨兵密码 |
| `ydsz.redis.pool.max-active` | `8` | 连接池最大活跃连接数 |
| `ydsz.redis.pool.max-wait` | `-1` | 最大等待时间（毫秒） |
| `ydsz.redis.pool.max-idle` | `8` | 最大空闲连接数 |
| `ydsz.redis.pool.min-idle` | `0` | 最小空闲连接数 |
| `ydsz.redis.pool.enabled` | `true` | 是否启用连接池 |
| `ydsz.redis.retry.enabled` | `true` | 是否启用 Redis 重试拦截器 |
| `ydsz.redis.retry.max-retries` | `3` | 最大重试次数 |
| `ydsz.redis.retry.initial-backoff-ms` | `100` | 初始退避时间（毫秒） |
| `ydsz.redis.retry.max-backoff-ms` | `2000` | 最大退避时间（毫秒） |
| `ydsz.redis.retry.retry-on-write` | `false` | 是否对写操作重试（默认仅读操作） |
| `ydsz.redis.retry.proxy-template` | `false` | 是否代理 RedisTemplate 提供重试能力 |
| `ydsz.redis.rate-limiter.fail-open-policy` | `FAIL_CLOSED` | 限流器故障处理策略 |
| `ydsz.redis.bloom-filter.fail-mode` | `FAIL_OPEN` | 布隆过滤器故障处理策略 |
| `ydsz.redis.metrics.slow-operation-threshold-ms` | `100` | 慢操作阈值（毫秒），0 禁用 |
| `ydsz.redis.tenant.enabled` | `false` | 是否启用租户级 Redis Key 隔离 |
| `ydsz.redis.key-expiration.enabled` | `false` | 是否启用 Key 过期事件监听（需 Redis 服务端 notify-keyspace-events Ex） |
| `ydsz.redis.client.type` | `JEDIS` | 客户端类型（JEDIS / LETTUCE） |
| `ydsz.redis.client.read-from` | `MASTER` | 读策略（仅 Lettuce 生效：MASTER / MASTER_PREFERRED / REPLICA_PREFERRED / REPLICA / NEAREST） |
| `ydsz.redis.client.pool.max-active` | `16` | 最大连接数 |
| `ydsz.redis.client.pool.max-idle` | `8` | 最大空闲连接数 |
| `ydsz.redis.client.pool.min-idle` | `2` | 最小空闲连接数 |
| `ydsz.redis.client.pool.max-wait` | `-1` | 获取连接最大等待时间（毫秒） |
| `ydsz.redis.client.pool.enabled` | `true` | 是否启用连接池 |
| `ydsz.redis.client.ssl.enabled` | `false` | 是否启用 SSL |

### 多级缓存配置（`ydsz.redis.multilevel.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.redis.multilevel.enabled` | `false` | 是否启用多级缓存（需 Caffeine 在 classpath） |
| `ydsz.redis.multilevel.l1-max-size` | `1000` | L1 Caffeine 缓存最大条目数 |
| `ydsz.redis.multilevel.l1-ttl-seconds` | `60` | L1 Caffeine 缓存过期时间（秒），建议为 L2 TTL 的 1/5 ~ 1/10 |

## 使用示例

### 1. 滑动窗口限流

```java
import java.time.Duration;
import com.njydsz.common.redis.service.RedisRateLimiter;

// 每分钟最多 100 次
boolean allowed = rateLimiter.tryAcquireSlidingWindow(
    "api:user:10086", 100, Duration.ofMinutes(1));
```

### 2. 注解驱动缓存（防击穿/防穿透/防雪崩）

```java
import com.njydsz.common.redis.annotation.YdszCacheable;

@YdszCacheable(key = "'user:' + #userId", ttl = 300)
public User getUserById(Long userId) {
    return userMapper.selectById(userId);
}
```

### 3. Key 过期事件监听

```java
import com.njydsz.common.redis.annotation.RedisKeyExpireListener;

// 需先开启 ydsz.redis.key-expiration.enabled=true 及 Redis 服务端 notify-keyspace-events Ex
@RedisKeyExpireListener(keyPattern = "order:lock:*")
public void onOrderLockExpired(String expiredKey) {
    log.info("订单锁已过期：{}", expiredKey);
}
```

### 4. 操作类型声明注解

```java
import com.njydsz.common.redis.annotation.RedisOperation;

@RedisOperation(type = OperationType.READ)
public User getUser(String key) { ... }

@RedisOperation(type = OperationType.WRITE, retryOnWrite = true)
public boolean updateUser(String key, User user) { ... }
```

> 布隆过滤器、延迟队列、Redis 版雪花 ID 等能力已移除，不再提供使用示例。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `RedisKeyPrefixProvider` | Redis Key 前缀提供者接口，业务模块可自定义 Key 前缀来源 | 业务模块自定义实现 |
| `RedisClientType` | 客户端类型枚举 SPI，扩展新的客户端实现 | `JEDIS` / `LETTUCE` |
| `FailOpenPolicy` | 故障处理策略 SPI（FAIL_OPEN / FAIL_CLOSED / FAIL_THROW） | 限流器 / 全局策略 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/redis` | Redis 健康检查（PING/PONG 延迟、版本、内存、连接数、db_size） | `spring-boot-health` 在类路径 + `RedisConnectionFactory` Bean 存在 |

`RedisHealthIndicator` 暴露信息：

- `pong` — PING 响应
- `latency_ms` — PING 延迟（毫秒）
- `version` — Redis 服务端版本
- `used_memory` / `max_memory` / `used_memory_peak` — 内存使用
- `connected_clients` — 当前连接数
- `mem_fragmentation_ratio` — 内存碎片率
- `uptime_in_days` — Redis 运行天数
- `db_size` — 当前数据库 Key 数量
- `status` — 延迟超过 100ms 时标记为 `degraded`

降级判定：PING 响应非 PONG → 标记 DOWN；延迟 > 100ms → 标记 degraded；连接异常 → DOWN（暴露 error / reason）。

## 注意事项

1. **RedisTemplate 不再默认代理**：自 1.0.0 起 `RedisTemplate` 不再被 AOP 代理包装，保持基础数据访问 Bean 的纯净性。如需重试能力，注入 `retryableRedisTemplate` 或开启 `ydsz.redis.retry.proxy-template=true`。
2. **重试仅读操作**：`retry.retry-on-write` 默认 `false`，避免写操作重复执行导致数据不一致。开启前需评估幂等性。
3. **缓存防护**：防击穿/防穿透/防雪崩由 `@YdszCacheable` 切面（`YdszCacheableAspect`）承载，通过 `ydsz-common-lock` 的分布式锁互斥回填。
4. **租户前缀只作用于 key**：`TenantRedisKeyPrefixer` 通过包装 `RedisSerializer` 实现，仅对 key 序列化生效，value 不受影响；超级管理员（tenantId = null 或 "0"）不添加前缀。
5. **限流器故障策略**：默认 `FAIL_CLOSED`（拒绝放行），安全敏感场景建议保持。
6. **限流器故障策略**：默认 `FAIL_CLOSED`（拒绝所有请求，安全场景推荐），与全局默认 `FAIL_OPEN` 不同。
7. **雪花 ID**：WorkerId 分配由 `ydsz-common-util` 的 `WorkerIdAllocator` 承担（PodOrdinal → IpHash 链），Redis 模块不再提供 ID 生成能力。
8. **Key 模板管理**：`RedisKeysEnum` 中 Key 定义不可扩展，业务模块如需自定义 Key 前缀和过期时间，可通过 `RedisStringOps` 直接操作或自定义 Key 常量类。
9. **Key 过期监听**：使用 `@RedisKeyExpireListener` 前需开启 `ydsz.redis.key-expiration.enabled=true` 并确保 Redis 服务端配置 `notify-keyspace-events Ex`。
10. **统一异常处理**：`RedisOperationExceptionHandler` 将 Spring Data Redis 常见异常统一转换为内部异常体系，连接失败/超时归类为可恢复异常，序列化/参数错误归类为不可恢复异常。

## 变更记录

- **1.0.0**（2026-08-18）：新增 Key 过期事件监听（`@RedisKeyExpireListener` / `RedisKeyExpirationEvent`）、统一异常处理拦截器（`RedisOperationExceptionHandler`）、操作类型声明注解（`RedisOperation`）、Key 前缀格式化器（`RedisKeyFormatter`）；新增内部异常体系（`RedisConnectionException` / `RedisBusinessException` / `RedisOperationException`）、Lua 脚本常量（`RedisScriptConstants`）、Key 命名约定策略（`RedisKeyNamingConvention`）。
- **1.0.0**（2026-08-17）：补全多级缓存（`MultiLevelCacheProvider` / `MultiLevelCacheAutoConfiguration`）章节与配置项文档
- **1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
