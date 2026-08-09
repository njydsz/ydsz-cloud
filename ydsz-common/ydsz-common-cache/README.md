# ydsz-common-cache

> 多策略本地缓存框架（L4 基础数据层）— 零第三方依赖核心 + 可选 Spring/Micrometer/Resilience4j 集成

提供 LRU / LFU / Window-TinyLFU / Weighted / Striped / EnhancedLoading 等多种缓存算法、防穿透/防击穿/防雪崩三防、写穿透/写回装饰器、L1+L2 多级缓存、注解驱动、可观测性、熔断降级、安全导出导入等企业级能力，是所有业务模块本地缓存的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多策略本地缓存、三防（穿透/击穿/雪崩）、多级缓存、注解驱动、可观测性、熔断降级能力 |
| **依赖** | common-core、common-util、common-json、common-exception；可选依赖 common-redis、spring-context、spring-boot-autoconfigure、spring-boot-health、micrometer-core、resilience4j-circuitbreaker、aspectjweaver |
| **版本** | 1.0.0 |

## 核心能力

### 1. 缓存算法

| 类 | 说明 |
|---|---|
| `Cache` | 缓存基础接口，定义 get/put/remove/invalidateAll/asMap/policy 等操作 |
| `LoadingCache` | 自动加载缓存接口，扩展 `Cache` 支持 loader 自动加载 |
| `AsyncCache` | 异步缓存接口，返回 `CompletableFuture` |
| `CacheBuilder` | 流畅构建器（参考 Caffeine/Guava），链式配置容量、过期、权重、loader、writer 等 |
| `CacheType` | 缓存类型枚举：LRU / LFU / TINYLFU / WEIGHTED / CONCURRENT / STRIPED / ENHANCED_LOADING |
| `YdszCache` | 工具类入口，`YdszCache.newBuilder()` 创建 `CacheBuilder` |
| `LRUCache` | 最近最少使用淘汰策略，热点数据场景 |
| `LFUCache` | 最不经常使用淘汰策略，访问频率差异大的场景（含 `FrequencySketch` 频率草图） |
| `WindowTinyLFUCache` | Window-TinyLFU 算法（参考 Caffeine），命中率最优，通用场景默认 |
| `WeightedCache` | 按权重控制容量，内存敏感场景（配合 `Weigher`） |
| `ConcurrentCache` | 并发安全 `ConcurrentHashMap` 缓存，中等并发场景 |
| `StripedConcurrentCache` | 高性能分段锁并发缓存（默认），高并发写入场景首选 |
| `EnhancedLoadingCache` | 增强版自动加载缓存，支持自动加载、自动刷新、异步加载 |
| `AsyncLoadingCacheImpl` | 异步加载缓存实现 |
| `SoftValueCache` / `WeakValueCache` / `WeakKeyCache` | 引用类型缓存，内存敏感场景（通过 builder 配置） |

### 2. 缓存防护（三防）

| 类 | 说明 |
|---|---|
| `CacheProtectionGuard` | 防护守卫：防穿透（null 缓存空标记）、防击穿（per-cache Key 级锁）、防雪崩（空值占位 `[minExpire, maxExpire]` 随机过期） |
| `NullValueGuard` | 空值占位管理（per-cache 实例级状态，使用 `WeakHashMap` 关联 Cache 实例，GC 自动清理） |

`Cache` 接口默认方法 `getWithProtection(key, loader, minExpireMs, maxExpireMs)` 委托给 `CacheProtectionGuard`，业务方无需感知防护细节。

### 3. 装饰器

| 类 | 说明 |
|---|---|
| `WriteThroughCache` | 写穿透装饰器，put 时同步写入 `CacheWriter` 后端存储 |
| `WriteBehindCache` | 写回装饰器，put 时仅更新缓存，后台线程异步批量写入后端 |
| `ExpirableCache` | 可过期装饰器，支持 `expireAfterWrite` / `expireAfterAccess` |
| `TimedCacheDecorator` | 定时过期装饰器 |
| `SwrCacheDecorator` | SWR（Stale-While-Revalidate）装饰器，先返回旧值异步刷新 |
| `MemoryAwareEvictionCache` | 内存感知淘汰装饰器 |
| `ConditionalCacheDecorator` | 条件缓存装饰器 |
| `Resilience4jCacheDecorator` | Resilience4j 熔断降级装饰器（见下文） |

### 4. 注解驱动

| 类 / 注解 | 说明 |
|---|---|
| `@Cached` | 声明式缓存读取（方法返回值自动缓存，支持 name / key / condition / unlessNull / expireAfterWrite / expireAfterAccess） |
| `@CacheInvalidate` | 声明式缓存失效（方法执行后清除指定 key 或 allEntries） |
| `@CacheRefresh` | 声明式自动刷新（配合 `@Cached` 使用，支持 SWR 模式） |
| `CacheAnnotationAspect` | 注解驱动 AOP 切面，拦截 `@Cached` / `@CacheInvalidate` / `@CacheRefresh`，委托底层 Cache 实例执行 |

### 5. 多级缓存

| 类 | 说明 |
|---|---|
| `MultiLevelCache` | L1 本地 + L2 Redis 两级缓存；读：L1 命中直接返回 → L1 未命中查 L2 回填 L1 → L2 未命中返回 null；写：同时写 L1+L2（Write-Through） |
| `MultiLevelCacheBuilder` | 多级缓存构建器 |
| `RedisCacheAdapter` | Redis `Cache` 适配器，将 `RedisTemplate` 包装为 `Cache` 接口 |
| `CacheInvalidationBroadcaster` | 跨节点 L1 失效广播器 SPI（Redis Pub/Sub / MQ / Noop） |
| `RedisCacheInvalidationBroadcaster` | 基于 Redis Pub/Sub 的默认实现 |
| `DistributedRebuildLock` | 分布式缓存重建锁（基于 Redis），防止多节点同时重建缓存 |

### 6. 可观测性

| 类 | 说明 |
|---|---|
| `CacheStats` | 缓存统计信息（hitCount / missCount / evictionCount / loadDuration） |
| `CacheMeterBinder` | Micrometer `MeterBinder` 实现，注册 Gauge / FunctionCounter / FunctionTimer / Timer（P50/P90/P99 分位数） |
| `CacheMetricsCollector` | 指标收集器 |
| `CacheMetricsAutoConfiguration` | 自动配置，Micrometer + `YdszCacheManager` 可用时自动注册；含 `CacheMetricsRegistrar` 支持运行时动态注册新缓存 |
| `PaddedStatsCounter` | 缓存行填充统计计数器，避免 false sharing |

注册的 Micrometer 指标：

| 指标 | 类型 | 说明 |
|---|---|---|
| `cache.size` | Gauge | 当前缓存条目数 |
| `cache.gets` | FunctionCounter | 缓存查询总次数 |
| `cache.misses` | FunctionCounter | 缓存未命中总次数 |
| `cache.puts` | FunctionCounter | 缓存放入总次数 |
| `cache.hit.rate` | Gauge | 缓存命中率（0.0 ~ 1.0） |
| `cache.evictions` | FunctionCounter | 淘汰总次数 |
| `cache.load.duration` | FunctionTimer | 平均加载耗时 |
| `cache.get.duration` | Timer | GET 操作耗时分布（含 P50/P90/P99） |
| `cache.put.duration` | Timer | PUT 操作耗时分布（含 P50/P90/P99） |

### 7. 熔断降级

| 类 | 说明 |
|---|---|
| `Resilience4jCacheDecorator` | 使用 Resilience4j `CircuitBreaker` 包装缓存操作，后端（如 Redis）不可用时自动降级返回 null |

熔断策略：

- 滑动窗口 100 次请求
- 失败率阈值 50%
- 熔断打开后等待 10 秒自动进入半开
- 半开状态允许 10 次探测请求

### 8. 序列化与导出

| 类 | 说明 |
|---|---|
| `CacheExportImport` | 缓存导出导入工具，支持对象序列化、文本格式、限制导入条目数 |
| `SafeObjectInputFilter` | 反序列化白名单过滤器（来自 common-json），统一白名单来源 |

反序列化安全约束：

- 白名单仅允许 `java.lang.` / `java.util.` / `java.math.` / `java.time.` 包
- 限制深度 ≤ 5、引用数 ≤ 500,000、字节数 ≤ 256MB
- 业务自定义类型在 ydsz-common-json 包前缀白名单内即可（`java.*`、`javax.*`、`com.njydsz.*` 默认允许）

### 9. 辅助组件

| 类 | 说明 |
|---|---|
| `CacheLoader` | 缓存加载器接口（单键/批量/同步/异步） |
| `CacheWriter` | 缓存写入器接口（Write-Through / Write-Behind） |
| `Weigher` | 权重计算器接口（`@FunctionalInterface`） |
| `Expiry` | 自定义过期策略接口，per-entry 动态计算过期时间 |
| `TTLMode` | TTL 模式枚举 |
| `CacheKeyGenerator` | 缓存 Key 生成器 |
| `CacheScheduler` | 缓存定时任务调度器（清理、刷新、健康检查） |
| `CacheThreadPoolManager` | 缓存线程池管理器，实现 `DisposableBean` 在 Spring 容器关闭时自动清理 |
| `CacheWarmer` | 缓存预热器 |
| `RemovalListener` | 删除监听器接口（`@FunctionalInterface`），配合 `RemovalCause` 区分删除原因 |
| `AsyncFunction` | 异步函数接口 |

### 10. Spring Cache 适配

| 类 | 说明 |
|---|---|
| `YdszCacheManager` | Spring `CacheManager` 实现，支持 per-cache 独立配置；实现 `DisposableBean` 在容器关闭时清理 `EnhancedLoadingCache` 实例与共享线程池 |
| `SpringYdszCache` | Spring `Cache` 接口实现，包装底层 `YdszCache` |
| `YdszCacheProperties` | 配置属性（`ydsz.cache.*`），支持全局默认 + per-cache 覆盖 |
| `YdszCacheAutoConfiguration` | Spring Boot 自动配置 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-cache</artifactId>
</dependency>
```

如需多级缓存（L2 Redis）能力，额外引入可选依赖：

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-redis</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  cache:
    type: TINYLFU
    maximum-size: 10000
    expire-after-write: 30
    expire-time-unit: MINUTES
    allow-null-values: true
    record-stats: true
    # per-cache 配置（覆盖全局默认）
    caches:
      user-cache:
        type: LRU
        maximum-size: 5000
        expire-after-write: 60
      config-cache:
        type: CONCURRENT
        maximum-size: 500
        expire-after-write: 0  # 不过期
```

### 3. 直接使用（脱离 Spring）

```java
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;

Cache<String, User> cache = YdszCache.newBuilder()
        .type(CacheType.TINYLFU)
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build();

cache.put("user:1", user);
User cached = cache.getIfPresent("user:1");

// 带加载器的 get（自动缓存）
User user = cache.get("user:1", key -> loadUserFromDb(key));

// 带三防的 get
User user = cache.getWithProtection("user:1",
        key -> loadUserFromDb(key),
        60_000, 300_000);  // 空值占位 60~300 秒随机过期
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.cache.type` | TINYLFU | 全局默认缓存类型（LRU / LFU / TINYLFU / WEIGHTED / CONCURRENT / STRIPED / ENHANCED_LOADING） |
| `ydsz.cache.cache-names` | - | 启动期预创建的缓存名称列表 |
| `ydsz.cache.maximum-size` | 1000 | 全局默认最大容量 |
| `ydsz.cache.expire-after-write` | 30 | 写入后过期时间（配合 `expire-time-unit`） |
| `ydsz.cache.expire-after-access` | 0 | 访问后过期时间（0 表示不启用） |
| `ydsz.cache.expire-time-unit` | MINUTES | 过期时间单位 |
| `ydsz.cache.refresh-after-write` | 0 | 写入后自动刷新间隔（0 表示不刷新） |
| `ydsz.cache.initial-capacity` | 64 | 初始容量 |
| `ydsz.cache.allow-null-values` | true | 是否允许缓存 null 值（防穿透） |
| `ydsz.cache.record-stats` | true | 是否启用统计 |
| `ydsz.cache.weak-keys` | false | 是否使用弱引用键 |
| `ydsz.cache.weak-values` | false | 是否使用弱引用值 |
| `ydsz.cache.soft-values` | false | 是否使用软引用值 |
| `ydsz.cache.caches.<name>.*` | - | per-cache 独立配置（覆盖全局默认，字段同上） |
| `ydsz.cache.health-check.enabled` | true | 缓存健康检查开关 |
| `ydsz.cache.warmup.enabled` | false | 启动期缓存预热开关 |
| `ydsz.cache.annotation.enabled` | true | 注解驱动切面开关（`@Cached` / `@CacheInvalidate` / `@CacheRefresh`） |
| `ydsz.cache.multilevel.rebuild-lock.enabled` | false | 分布式重建锁开关（需 RedisTemplate 在 classpath） |

## 使用示例

### 1. Spring Cache 注解驱动

```java
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
public class UserService {

    @Cacheable(value = "user-cache", key = "#id")
    public User getUser(Long id) {
        return userRepo.findById(id);
    }

    @CacheEvict(value = "user-cache", key = "#user.id")
    public void updateUser(User user) {
        userRepo.save(user);
    }
}
```

### 2. YdszCache 注解驱动

```java
import com.njydsz.common.cache.annotation.Cached;
import com.njydsz.common.cache.annotation.CacheInvalidate;
import com.njydsz.common.cache.annotation.CacheRefresh;
import java.util.concurrent.TimeUnit;

@Service
public class ConfigService {

    @Cached(name = "config", key = "#configKey", expireAfterWrite = 30, timeUnit = TimeUnit.MINUTES)
    @CacheRefresh(refreshAfterWrite = 5, timeUnit = TimeUnit.MINUTES, staleWhileRevalidate = true)
    public Config getConfig(String configKey) {
        return configDao.findByKey(configKey);
    }

    @CacheInvalidate(name = "config", key = "#configKey")
    public void updateConfig(String configKey, ConfigDTO dto) {
        configDao.update(configKey, dto);
    }
}
```

### 3. 多级缓存（L1 + L2 Redis）

```java
import com.njydsz.common.cache.multilevel.MultiLevelCache;
import com.njydsz.common.cache.multilevel.RedisCacheAdapter;
import com.njydsz.common.cache.resilience.Resilience4jCacheDecorator;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.builder.CacheType;

Cache<String, User> l1 = YdszCache.newBuilder()
        .type(CacheType.TINYLFU)
        .maximumSize(10_000)
        .build();

Cache<String, User> l2 = new RedisCacheAdapter<>(redisTemplate, "cache:");

// L2 包装熔断降级
Cache<String, User> resilientL2 = new Resilience4jCacheDecorator<>(l2, "redis-cache");

MultiLevelCache<String, User> multiLevel = new MultiLevelCache<>(l1, resilientL2);

// 读取：L1 命中直接返回；L1 未命中查 L2 并回填
// 写入：同时写入 L1 和 L2（Write-Through）
multiLevel.put("user:1", user);
User cached = multiLevel.getIfPresent("user:1");
```

### 4. 写穿透缓存

```java
import com.njydsz.common.cache.support.CacheWriter;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.builder.CacheType;

CacheWriter<String, User> writer = (key, value) -> userRepo.save(value);

Cache<String, User> cache = YdszCache.newBuilder()
        .type(CacheType.STRIPED)
        .maximumSize(10_000)
        .writer(writer)
        .build();

// put 时同步写入后端存储
cache.put("user:1", user);
```

### 5. 缓存导出导入

```java
import com.njydsz.common.cache.export.CacheExportImport;

// 对象序列化导出
CacheExportImport.exportCache(cache, "/tmp/cache.dat");

// 安全反序列化导入（白名单 + 条目数限制）
int count = CacheExportImport.importCacheWithLimit(
        cache, "/tmp/cache.dat", 1000, String.class, User.class);

// 文本格式导出导入
CacheExportImport.exportCacheToText(cache, "/tmp/cache.txt");
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `CacheLoader` | 缓存加载器，未命中时自动加载 | 业务模块实现，或通过 `CacheLoader.from(Function)` 快速创建 |
| `CacheWriter` | 缓存写入器，Write-Through / Write-Behind 同步/异步写后端 | 业务模块实现 |
| `Weigher` | 权重计算器，按对象大小控制容量 | 业务模块实现 |
| `Expiry` | 自定义过期策略，per-entry 动态计算过期时间 | 业务模块实现 |
| `RemovalListener` | 删除监听器，缓存项被移除时回调 | 业务模块实现 |
| `CacheInvalidationBroadcaster` | 跨节点 L1 失效广播器 | 框架内置 `RedisCacheInvalidationBroadcaster`，业务可扩展 MQ 实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/cache` | Spring Cache 健康检查（包装 `YdszCacheManager` 中所有缓存） | `spring-boot-health` 在 classpath 且 `ydsz.cache.health-check.enabled=true` |
| `CacheHealthIndicator.health()` | 编程式健康检查（直接调用） | 任何场景 |

健康检查暴露信息（每个缓存）：

| 字段 | 说明 |
|---|---|
| `size` | 当前缓存条目数 |
| `hitRate` | 命中率（0.0 ~ 1.0） |
| `hitCount` / `missCount` | 命中 / 未命中次数 |
| `maxSize` | 最大容量（仅支持 eviction policy 的缓存） |
| `usage` | 容量使用率 |
| `status` | 状态（UP / WARN / DOWN） |
| `warning` | 告警原因（命中率高/容量使用率高时填充） |
| `totalCaches` | 监控的缓存总数 |

状态判定规则：

- 命中率 < 0.3 且总访问次数 > 100 → WARN（`reason=低命中率`）
- 容量使用率 > 90% → WARN（`reason=高容量使用率`）
- 检查过程抛异常 → DOWN

## 注意事项

1. **三防 per-cache 隔离**：`CacheProtectionGuard` 与 `NullValueGuard` 使用 `WeakHashMap` 关联 Cache 实例，避免跨缓存锁竞争与内存泄漏；Cache 实例 GC 后状态自动清理。
2. **空值占位随机过期**：`getWithProtection` 的 `minExpireMs` 与 `maxExpireMs` 必须为正数且 `maxExpireMs >= minExpireMs`，空值占位在区间内随机过期以防雪崩。
3. **多级缓存一致性**：L1 跨节点一致性依赖 `CacheInvalidationBroadcaster`（默认 Redis Pub/Sub），未配置广播器时各节点 L1 独立，仅保证最终一致。
4. **线程池生命周期**：`CacheThreadPoolManager` 由 Spring 管理（`YdszCacheAutoConfiguration` 注册为 Bean 并调用 `setInstance`），容器关闭时通过 `DisposableBean` 自动清理；脱离 Spring 使用时需手动调用 `CacheThreadPoolManager.getInstance().shutdown()`。
5. **反序列化白名单**：`CacheExportImport` 反序列化使用 `java.*`、`javax.*`、`com.njydsz.*` 包前缀白名单过滤，业务自定义类型需位于信任包内。
6. **Resilience4j 可选**：`Resilience4jCacheDecorator` 需 classpath 中存在 `resilience4j-circuitbreaker`，未引入时直接使用底层 `Cache` 即可。
7. **注解切面可选**：`@Cached` / `@CacheInvalidate` / `@CacheRefresh` 需 classpath 中存在 AspectJ Weaver，可通过 `ydsz.cache.annotation.enabled=false` 关闭。
8. **Spring Cache 兼容**：`YdszCacheManager` 同时支持 Spring 标准 `@Cacheable` / `@CacheEvict` 注解与 YdszCache 自定义注解，两者可混用但同一方法不应同时标注。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
