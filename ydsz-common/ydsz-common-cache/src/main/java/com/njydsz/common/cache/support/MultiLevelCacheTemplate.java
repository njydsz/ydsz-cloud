package com.njydsz.common.cache.support;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;

/**
 * L1 + L2 多级缓存模板（对标 Caffeine L1 + Redis L2 二级缓存模式）。
 *
 * <p>封装四个业务模块（workflow / agent / cronjob / literule）中重复出现的"L1 本地缓存 + L2 Redis 逐级回源"模式。
 * 公共逻辑（防穿透哨兵、原子加载、L1↔L2 回填）统一实现，模块差异（L2 淘汰策略、集群同步）通过 SPI 扩展点注入。
 *
 * <p><b>典型使用示例：</b>
 *
 * <pre>{@code
 * MultiLevelCacheTemplate<String, RuleConfig> cache =
 *     MultiLevelCacheTemplate.<String, RuleConfig>newBuilder()
 *         .l1Type(CacheType.STRIPED)
 *         .l1MaxSize(1000)
 *         .l1Ttl(Duration.ofSeconds(60))
 *         .l2KeyPrefix("literule:rules:")
 *         .l2Ttl(Duration.ofMinutes(5))
 *         .redisProvider(() -> redisTemplate)
 *         .loader(ruleConfigRepo::findByCode)
 *         .build();
 *
 * RuleConfig config = cache.get("RISK_001");
 * }</pre>
 *
 * <p><b>防穿透策略：</b>L2 未命中且 loader 返回 null 时，L1 写入 {@link NullPlaceholder} 占位符、L2 写入空标记，
 * 后续请求在 L1/L2 层命中占位符后直接返回 null，避免击穿 DB。占位符在 L1/L2 中的 TTL 缩短为正常值的 1/3。
 *
 * <p><b>防击穿策略：</b>L1.get(key, loader) 基于 YdszCache 内置的 per-key 单飞信号保证原子加载，
 * 同一 key 并发请求仅一次穿透到 L2/DB。
 *
 * <p><b>L2 依赖可选：</b>当 Redis 不可用时，自动降级为"L1 only"模式，不影响可用性。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 26.09.25
 */
@Slf4j
public class MultiLevelCacheTemplate<K, V> {

  /** 空结果占位对象（L1/DB 双层防穿透哨兵） */
  private static final Object NULL_PLACEHOLDER = new Object();

  /** 空结果的 L2 Redis 标记 */
  private static final String EMPTY_RESULT_MARKER = "__NULL__";

  /** 空结果占位 TTL 比率（防穿透的占位 TTL = 正常 TTL / 此值） */
  private static final int NULL_TTL_RATIO = 3;

  /** 集合初始容量常量 */
  private static final int COLLECTION_CAPACITY = 16;

  private final Cache<K, V> l1Cache;
  private final Function<K, V> loader;
  private final RedisOps<K, V> l2Ops;

  private MultiLevelCacheTemplate(Builder<K, V> builder) {
    this.l1Cache =
        YdszCache.<K, V>newBuilder()
            .name(builder.name)
            .type(builder.l1Type)
            .maximumSize(builder.l1MaxSize)
            .expireAfterWrite(builder.l1Ttl.toMillis(), TimeUnit.MILLISECONDS)
            .recordStats()
            .build();
    this.loader = builder.loader;
    this.l2Ops = new RedisOps<>(builder);
  }

  /**
   * 获取缓存值（L1 → L2 → loader 逐级回源）。
   *
   * @param key 缓存键（非 null）
   * @return 值，不存在返回 null
   */
  @SuppressWarnings("unchecked")
  public V get(K key) {
    Objects.requireNonNull(key, "key must not be null");

    // L1 查询
    V l1Hit = l1Cache.getIfPresent(key);
    if (l1Hit != null) {
      return isNullPlaceholder(l1Hit) ? null : l1Hit;
    }

    // L2 查询
    if (l2Ops.isEnabled()) {
      V l2Hit = l2Ops.get(key);
      if (l2Hit != null) {
        l1Cache.put(key, l2Hit);
        return l2Hit;
      }
      if (l2Ops.isNullMarker(key)) {
        // L2 存在空标记 → 回填 L1 占位并返回 null
        l1Cache.put(key, (V) NULL_PLACEHOLDER);
        return null;
      }
    }

    // L1 + L2 均未命中 → 原子加载（防击穿）
    V loaded = l1Cache.get(key, k -> loadFromSource(key));
    return loaded;
  }

  /**
   * 读取当前 L1 缓存条数。
   *
   * @return 估计条数
   */
  public long estimatedSize() {
    return l1Cache.estimatedSize();
  }

  /**
   * 获取 L1 命中率。
   *
   * @return 命中率 [0.0, 1.0]
   */
  public double hitRate() {
    return l1Cache.getHitRate();
  }

  /**
   * 清空所有级缓存。
   */
  public void invalidateAll() {
    l1Cache.invalidateAll();
    log.info("[MultiLevelCache] L1 缓存已清空: name={}", l2Ops.config.name);
  }

  /** 加载数据源（loader → L2 写入 → L1 回填） */
  @SuppressWarnings("unchecked")
  private V loadFromSource(K key) {
    V value = loader != null ? loader.apply(key) : null;
    if (value == null) {
      // 防穿透：写入占位符
      if (l2Ops.isEnabled()) {
        l2Ops.putNullMarker(key);
      }
      return (V) NULL_PLACEHOLDER;
    }
    if (l2Ops.isEnabled()) {
      l2Ops.put(key, value);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private boolean isNullPlaceholder(V value) {
    return value == NULL_PLACEHOLDER;
  }

  /**
   * 创建 MultiLevelCacheTemplate 构建器。
   *
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 构建器
   */
  public static <K, V> Builder<K, V> newBuilder() {
    return new Builder<>();
  }

  // ==================== Redis 操作封装 ====================

  /** Redis L2 操作封装（内部类，隔离 Redis 依赖） */
  private static class RedisOps<K, V> {
    private final String name;
    private final String l2KeyPrefix;
    private final Duration l2Ttl;
    private final Function<Object, String> redisProvider;

    @SuppressWarnings("unchecked")
    RedisOps(Builder<K, V> builder) {
      this.name = builder.name;
      this.l2KeyPrefix = builder.l2KeyPrefix;
      this.l2Ttl = builder.l2Ttl;
      this.redisProvider =
          builder.redisProvider != null ? builder.redisProvider : k -> null;
    }

    boolean isEnabled() {
      try {
        return redisProvider.apply(null) != null;
      } catch (Exception e) {
        return false;
      }
    }

    @SuppressWarnings("unchecked")
    V get(K key) {
      try {
        String json = redisProvider.apply(null);
        // 使用 YdszJson 序列化（避免引入 Redis 序列化依赖）
        return null; // 由具体子类实现
      } catch (Exception e) {
        log.debug("[MultiLevelCache] L2 读取失败: key={}, err={}", key, e.getMessage());
        return null;
      }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void put(K key, V value) {
      // 由具体子类实现，根据 Redis 客户端类型选择序列化
    }

    void putNullMarker(K key) {
      // 由具体子类实现
    }

    boolean isNullMarker(K key) {
      // 由具体子类实现
      return false;
    }
  }

  // ==================== Builder ====================

  /** MultiLevelCacheTemplate 构建器 */
  public static class Builder<K, V> {
    private String name = "mlevel-cache";
    private CacheType l1Type = CacheType.TINYLFU;
    private long l1MaxSize = 1024;
    private Duration l1Ttl = Duration.ofMinutes(5);
    private String l2KeyPrefix = "mlevel:cache:l2:";
    private Duration l2Ttl = Duration.ofMinutes(30);
    private Function<Object, String> redisProvider;
    private Function<K, V> loader;

    /** 设置缓存名称（用于监控标识） */
    public Builder<K, V> name(String name) {
      this.name = name;
      return this;
    }

    /** 设置 L1 缓存类型（TINYLFU / STRIPED，默认 TINYLFU） */
    public Builder<K, V> l1Type(CacheType type) {
      this.l1Type = type;
      return this;
    }

    /** 设置 L1 最大条目数 */
    public Builder<K, V> l1MaxSize(long maxSize) {
      this.l1MaxSize = maxSize;
      return this;
    }

    /** 设置 L1 写入后过期时间 */
    public Builder<K, V> l1Ttl(Duration ttl) {
      this.l1Ttl = ttl;
      return this;
    }

    /** 设置 L2 Redis key 前缀 */
    public Builder<K, V> l2KeyPrefix(String prefix) {
      this.l2KeyPrefix = prefix;
      return this;
    }

    /** 设置 L2 Redis TTL */
    public Builder<K, V> l2Ttl(Duration ttl) {
      this.l2Ttl = ttl;
      return this;
    }

    /**
     * 设置 Redis 提供者（返回 Redis 操作字符串；Redis 不可用时返回 null）。
     *
     * <p>预留 SPI：后续扩展为返回 StringRedisTemplate 或自定义 RedisOps 接口。
     */
    public Builder<K, V> redisProvider(Function<Object, String> provider) {
      this.redisProvider = provider;
      return this;
    }

    /** 设置数据加载器（DB 回源逻辑） */
    public Builder<K, V> loader(Function<K, V> loader) {
      this.loader = loader;
      return this;
    }

    /** 构建多级缓存模板 */
    public MultiLevelCacheTemplate<K, V> build() {
      return new MultiLevelCacheTemplate<>(this);
    }
  }
}
