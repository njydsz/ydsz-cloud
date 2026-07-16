# ydsz-common-redis

PMIS Redis 服务增强 — 6 种基础 ops + 9 种高级 ops、布隆过滤器、延迟队列、滑动窗口限流、雪花 ID、缓存击穿防护、Pipeline 批处理、集群工具。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 45 |

## 核心能力

### 基础 Ops 封装

| 接口 | 说明 |
|---|---|
| `ValueOps` | String 操作（get / set / incr / decr） |
| `HashOps` | Hash 操作（hGet / hSet / hGetAll） |
| `ListOps` | List 操作（push / pop / range） |
| `SetOps` | Set 操作（add / remove / members） |
| `ZSetOps` | Sorted Set 操作（add / range / rank） |
| `GeoOps` | 地理位置（add / radius / distance） |

### 高级 Ops

| 类 | 说明 |
|---|---|
| `RedisAdvancedOps` | 高级操作聚合接口 |
| `RedisPipelineOps` / `RedisPipelineOpsImpl` | Pipeline 批量操作 |
| `RedisTransactionOps` | 事务操作（MULTI / EXEC / DISCARD） |
| `RedisPubSubOps` | 发布订阅 |
| `RedisStreamOps` | Redis Stream 消息流 |
| `RedisCollectionOps` | 集合操作工具 |
| `BatchRedisOperations` | 批量操作封装 |

### 高级服务

| 类 | 说明 |
|---|---|
| `RedisService` | Redis 统一服务入口 |
| `RedisRateLimiter` | 滑动窗口限流器（Lua 脚本实现） |
| `RedisBloomFilter` / `BloomFilterService` | 布隆过滤器（元素存在性判断） |
| `RedisDelayedQueue` / `DelayedTask` | 延迟队列（ZSET + 定时轮询） |
| `RedisSnowflakeIdGenerator` | Redis 协调的雪花 ID 生成器 |
| `RedisCacheGuard` | 缓存防护（防穿透 / 防击穿 / 防雪崩） |
| `NullValueCacheHelper` | 空值缓存助手 |

### 注解驱动

| 注解 / 类 | 说明 |
|---|---|
| `@YdszCacheable` | 声明式缓存（类似 Spring Cache） |
| `YdszCacheableAspect` | 缓存切面实现 |
| `RedisRetryInterceptor` | Redis 操作重试拦截器 |

### 序列化

| 类 | 说明 |
|---|---|
| `JacksonRedisSerializer` | Jackson JSON 序列化器 |
| `RedisKeyPrefixProvider` | Key 前缀提供者 |

### 集群与配置

| 类 | 说明 |
|---|---|
| `ClusterSlotUtil` | Redis Cluster Slot 工具 |
| `RedisConfiguration` | Redis 自动配置 |
| `RedisConnectionFactoryConfigurer` | 连接工厂配置器 |
| `RedisClientType` | 客户端类型（LETTUCE / JEDIS） |
| `RedisClientProperties` / `RedisProperties` | 配置属性 |

### 可观测性

| 类 | 说明 |
|---|---|
| `RedisMetricsCollector` / `RedisMetricsConfiguration` | Micrometer 指标采集 |
| `RedisHealthIndicator` | 健康检查 |
| `RedisKeysEnum` | Redis Key 枚举管理 |
| `RedisFailurePolicy` / `FailOpenPolicy` | 失败策略（快速失败 / 降级放行） |

## 配置项

```yaml
pmis:
  redis:
    client-type: lettuce           # lettuce / jedis
    key-prefix: pmis               # 全局 Key 前缀
    rate-limiter:
      enabled: true
      default-limit: 100           # 默认限流次数
      default-window: 60           # 默认窗口（秒）
    bloom-filter:
      expected-elements: 1000000   # 预期元素数
      fpp: 0.01                    # 误判率
    cache-guard:
      null-ttl: 300                # 空值缓存 TTL（秒）
      lock-wait: 3s                # 击穿防护锁等待时间
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `RedisConfiguration` | Redis 可用时激活 |
| `RedisMetricsConfiguration` | Micrometer 可用时激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-redis</artifactId>
</dependency>
```
