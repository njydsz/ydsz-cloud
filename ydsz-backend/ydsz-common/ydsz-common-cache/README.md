# ydsz-common-cache

高性能多策略本地缓存框架 — 零第三方依赖核心 + 可选 Spring/Micrometer/Resilience4j 集成。

## 核心特性

### 缓存算法

| 算法 | 类 | 适用场景 |
|------|-----|---------|
| **Window-TinyLFU** | `WindowTinyLFUCache` | 通用高性能场景，兼顾命中率与抗扫描污染 |
| **LRU** | `LRUCache` | 最近访问优先，简单可靠 |
| **LFU** | `LFUCache` | 频率优先，热点数据友好 |
| **TTL** | `TTLCache` | 定时过期，配置数据缓存 |
| **Striped** | `StripedConcurrentCache` | 分段锁高并发写入 |
| **Soft/Weak Reference** | `SoftValueCache` / `WeakValueCache` / `WeakKeyCache` | 内存敏感场景 |
| **Weighted** | `WeightedCache` | 按权重控制容量 |
| **Write-Through** | `WriteThroughCache` | 写穿透装饰器 |
| **Enhanced Loading** | `EnhancedLoadingCache` | 异步加载 + 线程池管理 |
| **Multi-Level** | `MultiLevelCache` | L1 本地 + L2 Redis 两级缓存 |

### 缓存防护（三防）

| 防护 | 实现 | 说明 |
|------|------|------|
| **防穿透** | `NullValueGuard` + `CacheProtectionGuard` | 加载器返回 null 时缓存空标记，阻止恶意请求穿透 |
| **防击穿** | `CacheProtectionGuard` Key 级锁 | 并发请求同一 key 只执行一次加载 |
| **防雪崩** | 随机过期抖动 | 空值占位符使用 `[minExpire, maxExpire]` 随机过期 |

> **P1 优化**：`CacheProtectionGuard` 和 `NullValueGuard` 从全局静态状态重构为 per-cache 实例级状态（`WeakHashMap` 关联），消除跨缓存锁竞争和内存泄漏。

### 可观测性

- **Micrometer 指标**：`CacheMeterBinder` 注册 Gauge / FunctionCounter / FunctionTimer / Timer（P50/P90/P99 分位数）
- **自动装配**：`CacheMetricsAutoConfiguration` 在 Micrometer + CacheManager 可用时自动注册

注册的指标：

| 指标 | 类型 | 说明 |
|------|------|------|
| `cache.size` | Gauge | 当前缓存条目数 |
| `cache.gets` | FunctionCounter | 缓存查询总次数 |
| `cache.misses` | FunctionCounter | 缓存未命中总次数 |
| `cache.puts` | FunctionCounter | 缓存放入总次数 |
| `cache.hit.rate` | Gauge | 缓存命中率（0.0 ~ 1.0） |
| `cache.evictions` | FunctionCounter | 淘汰总次数 |
| `cache.load.duration` | FunctionTimer | 平均加载耗时 |
| `cache.get.duration` | Timer | GET 操作耗时分布（含分位数） |
| `cache.put.duration` | Timer | PUT 操作耗时分布（含分位数） |

### 熔断降级

`Resilience4jCacheDecorator` 使用 Resilience4j CircuitBreaker 包装缓存操作：

- 滑动窗口 100 次请求，失败率 > 50% 时熔断
- 熔断打开后等待 10 秒自动进入半开状态
- 半开状态允许 10 次探测请求

### 序列化安全

`CacheExportImport` 反序列化使用 `ObjectInputFilter` 白名单机制：

- 仅允许 `java.lang.` / `java.util.` / `java.math.` / `java.time.` 包
- 限制深度 ≤ 5、引用数 ≤ 500,000、字节数 ≤ 256MB
- 数组类型使用 `UNDECIDED` 交由元素级检查

## 快速开始

### 1. 直接使用

```java
// 构建缓存
Cache<String, User> cache = CacheBuilder.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build(CacheType.TINY_LFU);

// 读写
cache.put("user:1", user);
User cached = cache.getIfPresent("user:1");

// 带加载器的 get（自动缓存）
User user = cache.get("user:1", key -> loadUserFromDb(key));

// 带防护的 get（防穿透/击穿/雪崩）
User user = CacheProtectionGuard.getWithProtection(
        cache, "user:1",
        key -> loadUserFromDb(key),
        60_000, 300_000);  // 空值占位 60~300 秒随机过期
```

### 2. Spring Cache 集成

```yaml
ydsz:
  cache:
    default-cache-type: TINY_LFU
    default-maximum-size: 10000
    default-expire-after-write: 5m
    default-allow-null-values: true
    caches:
      user-cache:
        cache-type: TINY_LFU
        maximum-size: 5000
        expire-after-write: 10m
      config-cache:
        cache-type: TTL
        maximum-size: 500
        expire-after-write: 30m
```

```java
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

### 3. 多级缓存（L1 + L2 Redis）

```java
Cache<String, User> l1 = CacheBuilder.newBuilder()
        .maximumSize(10_000)
        .build(CacheType.TINY_LFU);

Cache<String, User> l2 = new RedisCacheAdapter<>(redisTemplate, "cache:");

// L2 包装熔断降级
Cache<String, User> resilientL2 = new Resilience4jCacheDecorator<>(l2, "redis-cache");

MultiLevelCache<String, User> multiLevel = new MultiLevelCache<>(l1, resilientL2);

// 读取：L1 命中直接返回；L1 未命中查 L2 并回填
// 写入：同时写入 L1 和 L2（Write-Through）
multiLevel.put("user:1", user);
User cached = multiLevel.getIfPresent("user:1");
```

### 4. 缓存导出导入

```java
// 导出
CacheExportImport.exportCache(cache, "/tmp/cache.dat");

// 导入（安全反序列化）
CacheExportImport.importCache(cache, "/tmp/cache.dat", String.class, User.class);

// 限制导入条目数
int count = CacheExportImport.importCacheWithLimit(
        cache, "/tmp/cache.dat", 1000, String.class, User.class);

// 文本格式导出导入
CacheExportImport.exportCacheToText(cache, "/tmp/cache.txt");
CacheExportImport.importCacheFromText(cache, "/tmp/cache.txt", new TextParser<>() {
    @Override public String parseKey(String text) { return text; }
    @Override public String parseValue(String text) { return text; }
});
```

## 模块结构

```
com.njydsz.common.cache/
├── api/                    # 核心 API（Cache/LoadingCache/AsyncCache + 防护）
├── builder/                # Fluent Builder + CacheType 枚举
├── export/                 # 安全序列化导出导入
├── internal/               # 缓存算法实现
│   ├── concurrent/         # 分段锁高并发缓存
│   ├── decorator/          # 装饰器（Write-Through）
│   ├── lfu/                # LFU + FrequencySketch
│   ├── loading/            # 增强异步加载缓存
│   ├── lru/                # LRU
│   ├── reference/          # Soft/Weak Reference 缓存
│   ├── tinylfu/            # Window-TinyLFU
│   ├── ttl/                # TTL 过期
│   └── weighted/           # 权重缓存
├── listener/               # 删除监听器
├── metrics/                # Micrometer 可观测性
├── multilevel/             # 多级缓存（L1+L2）
├── resilience/             # Resilience4j 熔断降级
├── spring/                 # Spring Cache 适配 + AutoConfiguration
├── stats/                  # 统计信息
├── support/                # 辅助组件（Scheduler/Warmer/Loader/Writer）
└── util/                   # 工具类（PaddedStatsCounter）
```

## 依赖关系

| 依赖 | Scope | 说明 |
|------|-------|------|
| `ydsz-common-core` | compile | 核心基础 |
| `ydsz-common-util` | compile | Jackson、SLF4J 等 |
| `ydsz-common-redis` | optional | L2 Redis 缓存后端 |
| `spring-context` | provided | Spring Cache 接口 |
| `spring-boot-autoconfigure` | provided | 自动装配 |
| `micrometer-core` | provided/optional | 可观测性指标 |
| `resilience4j-circuitbreaker` | provided/optional | 熔断降级 |

## 测试

```bash
mvn test
```

当前测试覆盖：

| 测试类 | 测试数 | 覆盖范围 |
|--------|--------|---------|
| `CacheBuilderTest` | 10 | Fluent Builder + 缓存类型 |
| `StripedConcurrentCacheTest` | — | 分段锁并发缓存 |
| `LRUCacheTest` | — | LRU 淘汰策略 |
| `WindowTinyLFUCacheTest` | — | TinyLFU 算法 |
| `CacheExportImportTest` | 5 | 安全导出导入 |
| `SpringLocalCacheTest` | 14 | Spring Cache 适配 |
| `LocalCacheManagerTest` | 11 | CacheManager 管理 |
| `MultiLevelCacheTest` | 14 | 多级缓存读写/批量/统计 |
| `Resilience4jCacheDecoratorTest` | 11 | 熔断降级装饰器 |
| `CacheProtectionGuardTest` | 7 | 防穿透/击穿/雪崩 |

**总计：106 个测试，全部通过。**

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 4.0.0 | 2026-07-13 | 从 remi-cache-dev 迁移，品牌替换为 ydsz；P1~P3 优化（per-cache 隔离、线程池生命周期、序列化安全、多级缓存、可观测性、Resilience4j 降级） |
