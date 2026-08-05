# remi-common-redis

> Redis 服务增强与基础数据层公共模块（L4 基础数据层）

提供 Redis 操作门面、按数据类型拆分的 Ops 子组件（String / Hash / Collection / Geo / Pipeline / Pub-Sub / Stream / 事务）、分布式限流器（固定窗口 / 滑动窗口 / 令牌桶）、布隆过滤器、延迟队列、缓存防护（防穿透 / 防击穿 / 防雪崩）、雪花 ID 生成器、注解驱动缓存、租户级 Key 前缀器、可观测性指标等开箱即用能力，是所有业务模块缓存与分布式协调的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 Redis 操作门面、分布式限流 / 布隆过滤器 / 延迟队列 / 缓存防护 / 雪花 ID / 注解缓存等能力 |
| **依赖** | common-core、common-json、common-util；spring-boot-starter-data-redis、commons-pool2、lettuce-core；可选依赖 jedis、micrometer-core、redisson-spring-boot-starter、spring-boot-health |
| **版本** | 1.0.0 |

## 核心能力

### 1. Redis 操作门面与 Ops 拆分

| 类 | 说明 |
|---|---|
| `RedisService` | Redis 服务门面类，聚合所有 Ops 子组件，向后兼容老代码 |
| `RedisStringOps` | String + Bitmap 操作（含 key prefix 拼接、慢操作检测） |
| `RedisHashOps` | Hash 操作 |
| `RedisCollectionOps` | Set + List + ZSet 操作 |
| `RedisGeoOps` | Geo + HyperLogLog 操作 |
| `RedisAdvancedOps` | Pipeline + Lua 脚本操作聚合接口 |
| `RedisPipelineOps` / `RedisPipelineOpsImpl` | Pipeline 批量操作 |
| `RedisTransactionOps` | 事务操作（MULTI / EXEC / DISCARD） |
| `RedisPubSubOps` | 发布订阅 |
| `RedisStreamOps` | Redis Stream 消息流 |
| `BatchRedisOperations` | 批量操作接口（Pipeline + Transaction 组合） |

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

| 类 | 说明 |
|---|---|
| `RedisBloomFilter` | 基于 Redis BitMap 的分布式布隆过滤器，MurmurHash 3 多哈希、Lua 原子操作 |
| `BloomFilterService` | 布隆过滤器服务接口（解耦 Redisson / BitMap 实现） |

特性：可配置预期元素数与误判率、自动计算最优位数组大小和哈希函数数量、支持批量 add / mightContain。

### 4. 延迟队列

| 类 | 说明 |
|---|---|
| `RedisDelayedQueue` | 基于 Redis ZSET 的分布式延时队列，score 为到期时间戳 |
| `DelayedTask` | 延迟任务载体（payload / taskId / retryCount） |

特性：毫秒级精度、ZREM 原子移除保证不重复消费、SETNX 消费者侧去重、支持指数退避重试。

### 5. 缓存防护

| 类 | 说明 |
|---|---|
| `RedisCacheGuard` | 缓存防护工具类，提供防穿透 / 防击穿 / 防雪崩三重保护 |
| `NullValueCacheHelper` | 空值缓存助手 |

三大防护策略：

- **防穿透**：布隆过滤器模式 + 空值缓存（`__NULL__` 占位），TTL 由 `nullValueTtlSeconds` 控制
- **防击穿**：分布式锁模式 + WatchDog 续期机制（内嵌实现，避免与 remi-common-lock 循环依赖），自旋等待持锁线程回填
- **防雪崩**：随机 TTL，在基础 TTL 上叠加随机扰动

### 6. 雪花 ID 生成器

| 类 | 说明 |
|---|---|
| `RedisSnowflakeIdGenerator` | 基于 Twitter Snowflake 算法的分布式 ID 生成器（41 位时间戳 + 10 位 workerId + 12 位序列号） |
| `RedisWorkerIdRegistry` | Redis 协调的 workerId 注册中心（INCR 全局递增分配 + 心跳续约 + PreDestroy 释放） |

特性：全局唯一、趋势递增、单实例峰值 4096×1000 = 409.6 万 QPS、Redis 不可用时降级为本地序列。

### 7. 注解驱动缓存

| 注解 / 类 | 说明 |
|---|---|
| `@RemiCacheable` | remi 分布式缓存注解（增强 Spring `@Cacheable`） |
| `@RemiCachePut` | 缓存更新注解 |
| `@RemiCacheEvict` | 缓存清除注解 |
| `RemiCacheableAspect` | 缓存切面实现（SpEL 解析 + 分布式锁防击穿 + 空值缓存防穿透 + 随机 TTL 防雪崩） |
| `RedisRetryInterceptor` | Redis 操作重试拦截器（指数退避，默认仅读操作重试） |

`@RemiCacheable` 增强项：自定义 TTL、空值缓存防穿透（默认 60s）、分布式互斥锁防击穿、随机过期防雪崩。

### 8. 序列化与 Key 管理

| 类 | 说明 |
|---|---|
| `RemiJsonRedisSerializer` | 基于 RemiJson 的高性能 Redis 序列化器（支持 Java 8 时间类型） |
| `RedisKeyPrefixProvider` | Key 前缀提供者接口（业务模块实现，统一 Key 命名规范） |
| `TenantRedisKeyPrefixer` | 租户级 Redis Key 前缀器（格式 `{tenantId}:{originalKey}`，超级管理员不添加前缀） |
| `RedisKeysEnum` | Redis Key 模板枚举管理（统一 `remi:` 前缀，模板化 `{}` 占位符，分组管理 + 默认 TTL） |

### 9. 集群与连接配置

| 类 | 说明 |
|---|---|
| `RedisConfiguration` | Redis 自动配置（在 `DataRedisAutoConfiguration` 之前加载），注册 RedisTemplate / Ops Bean / 健康检查等 |
| `RedisConnectionFactoryConfigurer` | 连接工厂配置器，根据 `client.type` 自动选择 Jedis / Lettuce |
| `RedisClientType` | 客户端类型枚举（JEDIS / LETTUCE） |
| `ClusterSlotUtil` | Redis Cluster Slot 工具 |

### 10. 故障处理策略

| 类 | 说明 |
|---|---|
| `FailOpenPolicy` | 故障处理策略枚举（FAIL_OPEN / FAIL_CLOSED / FAIL_THROW） |
| `RedisOperationException` | Redis 操作异常 |

各子组件可单独配置故障策略，未单独配置时使用全局 `failurePolicy`（默认 FAIL_OPEN）：

- 限流器默认 `FAIL_CLOSED`（安全场景推荐）
- 布隆过滤器默认 `FAIL_OPEN`（放行可能导致穿透）

### 11. 可观测性

| 类 | 说明 |
|---|---|
| `RedisMetricsCollector` | Redis 操作指标收集器（Timer.Sample 低开销测量） |
| `RedisMetricsConfiguration` | 指标采集自动配置（`MeterRegistry` 存在时激活） |
| `RedisHealthIndicator` | Redis 健康检查 |

### 12. 租户隔离

| 类 | 说明 |
|---|---|
| `TenantRedisKeyPrefixer` | 租户级 Key 前缀器，通过包装 `RedisSerializer` 在序列化 key 时自动添加 `{tenantId}:` 前缀 |

特性：超级管理员（tenantId = null 或 "0"）不添加前缀，仅对 key 序列化生效，value 不受影响。

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-redis</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
remi:
  redis:
    host: localhost
    port: 6379
    password: xxx
    database: 0
    timeout: 3000
    client:
      type: lettuce       # lettuce / jedis
    key-prefix: remi       # 全局 Key 前缀
    failure-policy: FAIL_OPEN
```

### 3. 使用 RedisService 或子组件

```java
import com.remisoft.common.redis.service.RedisService;
import com.remisoft.common.redis.service.ops.RedisStringOps;

// 通过门面类（向后兼容）
redisService.set("key", "value");
String value = redisService.get("key", String.class);

// 直接注入子组件（推荐新代码使用）
stringOps.set("key", "value");
```

## 配置项

### RedisProperties（`remi.redis.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.redis.failure-policy` | `FAIL_OPEN` | 全局故障处理策略（FAIL_OPEN / FAIL_CLOSED / FAIL_THROW） |
| `remi.redis.host` | `localhost` | Redis 服务器地址（单机模式） |
| `remi.redis.port` | `6379` | Redis 服务器端口 |
| `remi.redis.password` | - | Redis 密码 |
| `remi.redis.database` | `0` | 数据库索引（单机模式） |
| `remi.redis.timeout` | `3000` | 连接超时时间（毫秒） |
| `remi.redis.timeout-duration` | - | 连接超时时间（Duration 格式，优先于 timeout） |
| `remi.redis.user` | - | Redis 用户名 |
| `remi.redis.ssl-enabled` | `false` | 是否启用 SSL |
| `remi.redis.key-prefix` | `""` | 全局 Key 前缀（默认使用 spring.application.name） |
| `remi.redis.null-value-ttl-seconds` | `1800` | 空值缓存 TTL（秒，缓存防护使用） |
| `remi.redis.lettuce.shutdown-timeout` | `100` | Lettuce 关闭超时时间 |
| `remi.redis.cluster.nodes` | - | 集群节点列表 |
| `remi.redis.cluster.max-redirects` | `3` | 集群最大重定向次数 |
| `remi.redis.sentinel.master` | - | 哨兵 master 名称 |
| `remi.redis.sentinel.nodes` | - | 哨兵节点列表 |
| `remi.redis.sentinel.password` | - | 哨兵密码 |
| `remi.redis.pool.max-active` | `8` | 连接池最大活跃连接数 |
| `remi.redis.pool.max-wait` | `-1` | 最大等待时间（毫秒） |
| `remi.redis.pool.max-idle` | `8` | 最大空闲连接数 |
| `remi.redis.pool.min-idle` | `0` | 最小空闲连接数 |
| `remi.redis.pool.enabled` | `true` | 是否启用连接池 |
| `remi.redis.retry.enabled` | `true` | 是否启用 Redis 重试拦截器 |
| `remi.redis.retry.max-retries` | `3` | 最大重试次数 |
| `remi.redis.retry.initial-backoff-ms` | `100` | 初始退避时间（毫秒） |
| `remi.redis.retry.max-backoff-ms` | `2000` | 最大退避时间（毫秒） |
| `remi.redis.retry.retry-on-write` | `false` | 是否对写操作重试（默认仅读操作） |
| `remi.redis.retry.proxy-template` | `false` | 是否代理 RedisTemplate 提供重试能力 |
| `remi.redis.rate-limiter.fail-open-policy` | `FAIL_CLOSED` | 限流器故障处理策略 |
| `remi.redis.bloom-filter.fail-mode` | `FAIL_OPEN` | 布隆过滤器故障处理策略 |
| `remi.redis.metrics.slow-operation-threshold-ms` | `100` | 慢操作阈值（毫秒），0 禁用 |
| `remi.redis.tenant.enabled` | `false` | 是否启用租户级 Redis Key 隔离 |

### RedisClientProperties（`remi.redis.client.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.redis.client.type` | `JEDIS` | 客户端类型（JEDIS / LETTUCE） |
| `remi.redis.client.read-from` | `MASTER` | 读策略（仅 Lettuce 生效：MASTER / MASTER_PREFERRED / REPLICA_PREFERRED / REPLICA / NEAREST） |
| `remi.redis.client.pool.max-active` | `16` | 最大连接数 |
| `remi.redis.client.pool.max-idle` | `8` | 最大空闲连接数 |
| `remi.redis.client.pool.min-idle` | `2` | 最小空闲连接数 |
| `remi.redis.client.pool.max-wait` | `-1` | 获取连接最大等待时间（毫秒） |
| `remi.redis.client.pool.enabled` | `true` | 是否启用连接池 |
| `remi.redis.client.ssl.enabled` | `false` | 是否启用 SSL |

## 使用示例

### 1. 滑动窗口限流

```java
import java.time.Duration;
import com.remisoft.common.redis.service.RedisRateLimiter;

// 每分钟最多 100 次
boolean allowed = rateLimiter.tryAcquireSlidingWindow(
    "api:user:10086", 100, Duration.ofMinutes(1));
```

### 2. 布隆过滤器防穿透

```java
import com.remisoft.common.redis.service.RedisBloomFilter;

// 添加元素
bloomFilter.add("user:bloom:1", "user123");

// 检查是否存在（可能误判）
boolean mightExist = bloomFilter.mightContain("user:bloom:1", "user123");
```

### 3. 延迟队列

```java
import java.time.Duration;
import com.remisoft.common.redis.service.RedisDelayedQueue;

// 投递延时任务（30 分钟后到期）
String taskId = delayedQueue.schedule(
    "order:pay:timeout",
    "orderId=10086",
    Duration.ofMinutes(30)
);

// 拉取已到期任务
DelayedTask task = delayedQueue.poll("order:pay:timeout", Duration.ofSeconds(5));
```

### 4. 注解驱动缓存

```java
import com.remisoft.common.redis.annotation.RemiCacheable;

@RemiCacheable(key = "'user:' + #userId", ttl = 300)
public User getUserById(Long userId) {
    return userMapper.selectById(userId);
}
```

### 5. 缓存防护（防击穿）

```java
import com.remisoft.common.redis.service.RedisCacheGuard;

Product product = cacheGuard.antiBreakdown(
    "product:hot:" + id,
    300,
    () -> productService.getById(id),
    Product.class
);
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `RedisKeyPrefixProvider` | Redis Key 前缀提供者接口，业务模块可自定义 Key 前缀来源 | 业务模块自定义实现 |
| `BloomFilterService` | 布隆过滤器服务接口，解耦 BitMap 与 Redisson 实现 | `RedisBloomFilter`（BitMap 实现） |
| `BatchRedisOperations` | 批量操作接口（Pipeline + Transaction 组合） | `RedisService` 实现 |
| `WorkerIdRegistry` | workerId 注册中心 SPI（由 common-util 定义，本模块提供 Redis 实现） | `RedisWorkerIdRegistry` |
| `RedisClientType` | 客户端类型枚举 SPI，扩展新的客户端实现 | `JEDIS` / `LETTUCE` |
| `FailOpenPolicy` | 故障处理策略 SPI（FAIL_OPEN / FAIL_CLOSED / FAIL_THROW） | 限流器 / 布隆过滤器 / 全局策略 |

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

1. **RedisTemplate 不再默认代理**：自 v1.0.0 起 `RedisTemplate` 不再被 AOP 代理包装，保持基础数据访问 Bean 的纯净性。如需重试能力，注入 `retryableRedisTemplate` 或开启 `remi.redis.retry.proxy-template=true`。
2. **重试仅读操作**：`retry.retry-on-write` 默认 `false`，避免写操作重复执行导致数据不一致。开启前需评估幂等性。
3. **缓存防护锁的循环依赖规避**：`RedisCacheGuard` 内嵌实现 WatchDog 续期机制（与 remi-common-lock 相同设计模式），不依赖 remi-common-lock，避免循环依赖。
4. **租户前缀只作用于 key**：`TenantRedisKeyPrefixer` 通过包装 `RedisSerializer` 实现，仅对 key 序列化生效，value 不受影响；超级管理员（tenantId = null 或 "0"）不添加前缀。
5. **布隆过滤器故障策略**：默认 `FAIL_OPEN`（返回 false 放行，可能导致穿透），安全敏感场景建议改为 `FAIL_CLOSED` 或 `FAIL_THROW`。
6. **限流器故障策略**：默认 `FAIL_CLOSED`（拒绝所有请求，安全场景推荐），与全局默认 `FAIL_OPEN` 不同。
7. **雪花 ID 降级**：Redis 不可用时 `RedisSnowflakeIdGenerator` 降级为本地序列生成，但 workerId 唯一性不再保证，恢复后需通过心跳续约重新分配。
8. **Key 模板管理**：`RedisKeysEnum` 中 Key 定义不可扩展，业务模块如需自定义 Key 前缀和过期时间，可通过 `RedisStringOps` 直接操作或自定义 Key 常量类。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
