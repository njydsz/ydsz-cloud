# ydsz-common-cache

> 高性能本地缓存框架（L1 工具模块层）— 零第三方依赖核心 + 可选 Spring/Micrometer 集成

提供 Window-TinyLFU 与 Striped 两种缓存淘汰策略、防穿透/防击穿/防雪崩三防、写穿透装饰器、注解驱动（Spring Cache 标准注解）、Micrometer 可观测性、自定义 Actuator 端点等企业级能力，是所有业务模块本地缓存的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供高性能本地缓存、三防（穿透/击穿/雪崩）、写穿透、注解驱动、可观测性能力 |
| **依赖** | common-util；可选依赖 spring-context、spring-boot-autoconfigure、spring-boot-health、micrometer-core、spring-boot-actuator、jackson-annotations（compileOnly） |
| **版本** | 2.0.0 |

## 核心能力

### 1. 缓存算法

| 类 | 说明 |
|---|---|
| `Cache` | 缓存基础接口，定义 get/put/remove/invalidateAll/policy 等操作 |
| `LoadingCache` | 自动加载缓存接口，扩展 `Cache` 支持 loader 自动加载 |
| `CacheBuilder` | 流畅构建器（参考 Caffeine），链式配置容量、过期、loader、writer 等 |
| `CacheType` | 缓存类型枚举：`TINYLFU`（默认，命中率最优）、`STRIPED`（高并发写入） |
| `YdszCache` | 工具类入口，`YdszCache.newBuilder()` 创建 `CacheBuilder` |
| `WindowTinyLFUCache` | Window-TinyLFU 算法（参考 Caffeine），命中率最优，通用场景默认 |
| `StripedConcurrentCache` | 高性能分段锁并发缓存，高并发写入场景首选 |
| `EnhancedLoadingCache` | 增强版自动加载缓存，支持自动加载、自动刷新 |
| `FrequencySketch` | 频率草图，为 TinyLFU 提供访问频率统计 |

### 2. 缓存防护（三防）

| 类 | 说明 |
|---|---|
| `CacheProtectionGuard` | 防护守卫：防穿透（null 缓存空标记）、防击穿（per-cache Key 级锁）、防雪崩（空值占位 [minExpire, maxExpire] 随机过期） |
| `NullValueGuard` | 空值占位管理（per-cache 实例级状态，使用 `WeakHashMap` 关联 Cache 实例，GC 自动清理） |

`Cache` 接口默认方法 `getWithProtection(key, loader, minExpireMs, maxExpireMs)` 委托给 `CacheProtectionGuard`，业务方无需感知防护细节。

### 3. 装饰器

| 类 | 说明 |
|---|---|
| `WriteThroughCache` | 写穿透装饰器，put 时同步写入 `CacheWriter` 后端存储 |
| `ExpirableCache` | 可过期装饰器，支持 `expireAfterWrite` / `expireAfterAccess` / 自定义 `Expiry` |

### 4. Spring Cache 注解驱动

模块遵循 Spring Cache 标准，直接使用 `@Cacheable` / `@CacheEvict` / `@CachePut` 注解驱动缓存操作，无需额外自定义注解。

### 5. 可观测性

| 类 | 说明 |
|---|---|
| `CacheStats` | 缓存统计信息（hitCount / missCount） |
| `CacheMeterBinder` | Micrometer `MeterBinder` 实现，注册 Gauge / FunctionCounter / Timer（含 P50/P90/P99 分位数） |
| `CacheMetricsCollector` | 指标收集器 |
| `CacheMetricsAutoConfiguration` | 自动配置，Micrometer + `YdszCacheManager` 可用时自动注册 |

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

### 6. Actuator 端点

| 类 | 说明 |
|---|---|
| `CacheMetricsEndpoint` | 自定义 Actuator 端点（ID: `cache-metrics`），暴露所有缓存的实时统计信息 |
| `CacheActuatorAutoConfiguration` | 当 Actuator 在 classpath 中且存在 Cache Bean 时自动注册端点 |

### 7. 辅助组件

| 类 | 说明 |
|---|---|
| `CacheLoader` | 缓存加载器接口（单键/批量/同步/异步） |
| `CacheWriter` | 缓存写入器接口（Write-Through） |
| `Expiry` | 自定义过期策略接口，per-entry 动态计算过期时间 |
| `CacheKeyGenerator` | 缓存 Key 生成器 |
| `AsyncFunction` | 异步函数接口 |
| `CacheThreadPoolManager` | 缓存线程池管理器，实现 `DisposableBean` 在 Spring 容器关闭时自动清理 |
| `RemovalListener` | 删除监听器接口（`@FunctionalInterface`） |
| `RemovalCause` | 删除原因枚举（EXPIRED / EXPLICIT / REPLACED / SIZE） |
| `CacheLoadException` | 缓存加载异常（loader 执行失败时包装） |

### 8. Spring Cache 适配

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
        type: TINYLFU
        maximum-size: 5000
        expire-after-write: 60
      order-cache:
        type: STRIPED
        maximum-size: 20000
        expire-after-write: 10
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
User safe = cache.getWithProtection("user:1",
        key -> loadUserFromDb(key),
        60_000, 300_000);  // 空值占位 60~300 秒随机过期
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.cache.type` | TINYLFU | 全局默认缓存类型（TINYLFU / STRIPED） |
| `ydsz.cache.cache-names` | - | 启动期预创建的缓存名称列表 |
| `ydsz.cache.maximum-size` | 1000 | 全局默认最大容量 |
| `ydsz.cache.expire-after-write` | 30 | 写入后过期时间（配合 `expire-time-unit`） |
| `ydsz.cache.expire-after-access` | 0 | 访问后过期时间（0 表示不启用） |
| `ydsz.cache.expire-time-unit` | MINUTES | 过期时间单位 |
| `ydsz.cache.refresh-after-write` | 0 | 写入后自动刷新间隔（0 表示不刷新） |
| `ydsz.cache.initial-capacity` | 64 | 初始容量 |
| `ydsz.cache.allow-null-values` | true | 是否允许缓存 null 值（防穿透） |
| `ydsz.cache.record-stats` | true | 是否启用统计 |
| `ydsz.cache.caches.<name>.*` | - | per-cache 独立配置（覆盖全局默认，字段同上） |
| `ydsz.cache.health-check.enabled` | true | 缓存健康检查开关 |

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

### 2. 写穿透缓存

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

### 3. Actuator 端点

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,cache-metrics
  endpoint:
    cache-metrics:
      enabled: true
```

访问 `/actuator/cache-metrics` 可获取所有缓存的实时统计（size、hitRate、stats 等）。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `CacheLoader` | 缓存加载器，未命中时自动加载 | 业务模块实现，或通过 `CacheLoader.from(Function)` 快速创建 |
| `CacheWriter` | 缓存写入器，Write-Through 同步写后端 | 业务模块实现 |
| `Expiry` | 自定义过期策略，per-entry 动态计算过期时间 | 业务模块实现 |
| `RemovalListener` | 删除监听器，缓存项被移除时回调 | 业务模块实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/cache` | Spring Cache 健康检查（包装 `YdszCacheManager` 中所有缓存） | `spring-boot-health` 在 classpath 且 `ydsz.cache.health-check.enabled=true` |
| `CacheHealthIndicator.health()` | 编程式健康检查（直接调用，不依赖 Spring Boot Health 抽象） | 任何场景 |
| `SpringCacheHealthIndicator` | Spring Boot Actuator `HealthIndicator` 适配器，对接 `YdszCacheManager` 中所有缓存 | `spring-boot-health` 在 classpath 且 `ydsz.cache.health-check.enabled=true` |

健康检查暴露信息（每个缓存）：

| 字段 | 说明 |
|---|---|
| `size` | 当前缓存条目数 |
| `hitRate` | 命中率（0.0 ~ 1.0） |
| `hitCount` / `missCount` | 命中 / 未命中次数 |
| `maxSize` | 最大容量 |
| `usage` | 容量使用率 |
| `status` | 状态（UP / WARN / DOWN） |
| `warning` | 告警原因 |
| `totalCaches` | 监控的缓存总数 |

状态判定规则：

- 命中率 < 0.3 且总访问次数 > 100 → WARN
- 容量使用率 > 90% → WARN
- 检查过程抛异常 → DOWN

## 注意事项

1. **三防 per-cache 隔离**：`CacheProtectionGuard` 与 `NullValueGuard` 使用 `WeakHashMap` 关联 Cache 实例，避免跨缓存锁竞争与内存泄漏；Cache 实例 GC 后状态自动清理。
2. **空值占位随机过期**：`getWithProtection` 的 `minExpireMs` 与 `maxExpireMs` 必须为正数且 `maxExpireMs >= minExpireMs`，空值占位在区间内随机过期以防雪崩。
3. **线程池生命周期**：`CacheThreadPoolManager` 由 Spring 管理（`YdszCacheAutoConfiguration` 注册为 Bean 并调用 `setInstance`），容器关闭时通过 `DisposableBean` 自动清理；脱离 Spring 使用时需手动调用 `CacheThreadPoolManager.getInstance().shutdown()`。
4. **Spring Cache 兼容**：`YdszCacheManager` 支持 Spring 标准 `@Cacheable` / `@CacheEvict` / `@CachePut` 注解。
5. **STRIPED 类型适用场景**：写多读少、并发量大的场景选择 STRIPED；通用场景默认 TINYLFU 命中率更优。
6. **过期策略互斥**：`expireAfterWrite` 与 `expireAfterAccess` 不可同时使用，如需 per-entry 动态过期请使用 `expireAfter(Expiry)`。

## 变更记录

- **1.0.0**（2026-08-15）：架构精简重构，移除 LRU/Weighted/Concurrent/弱引用缓存实现、多级缓存（L1+L2 Redis）、热点 Key 追踪、Resilience4j 熔断降级、内存感知淘汰、SWR、WriteBehind 等未落地能力；统一使用 Spring Cache 标准注解（@Cacheable/@CacheEvict）；下线 AsyncCache/AsyncLoadingCacheImpl；精简 pom 依赖（移除 resilience4j、aspectjweaver、spring-aop、skywalking、lombok、common-redis、common-json）；保留核心能力（TINYLFU/STRIPED + 三防 + ExpirableCache + WriteThroughCache + Spring Cache 适配 + Micrometer 指标 + Actuator 端点 + 健康检查）。
- **1.0.0**（2026-08-02）：初始版本，对标 common-jdbc 标准格式重构 README。
