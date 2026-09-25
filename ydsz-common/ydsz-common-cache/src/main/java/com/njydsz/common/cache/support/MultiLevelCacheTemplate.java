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
 * <p>封装 workflow / agent / cronjob / literule 四个业务模块中重复出现的"L1 本地缓存 + L2 Redis 逐级回源"模式。
 * 公共逻辑（L1 Window-TinyLFU / 防穿透哨兵 / 原子加载 / null 占位回填）统一实现；L2 存储差异通过 {@link L2Storage} SPI 注入。
 *
 * <p><b>典型使用示例：</b>
 *
 * <pre>{@code
 * MultiLevelCacheTemplate<String, RuleConfig> cache =
 *     MultiLevelCacheTemplate.<String, RuleConfig>newBuilder()
 *         .name("literule:config")
 *         .l1Type(CacheType.STRIPED)
 *         .l1MaxSize(1000)
 *         .l1Ttl(Duration.ofSeconds(60))
 *         .l2Ttl(Duration.ofMinutes(5))
 *         .l2Storage(new RedisL2Storage<>(redisTemplate, "literule:rules:"))
 *         .loader(ruleConfigRepo::findByCode)
 *         .build();
 *
 * RuleConfig config = cache.get("RISK_001");
 * }</pre>
 *
 * <p><b>防穿透策略：</b>L2 未命中且 loader 返回 null 时，L1 写入 {@link NullPlaceholder} 占位符、L2 写入空标记，
 * 后续请求在 L1/L2 命中占位符后直接返回 null，避免击穿 DB。占位符在 L1/L2 中的 TTL 为标准 TTL 的 1/3。
 *
 * <p><b>防击穿策略：</b>L1.get(key, loader) 基于 YdszCache 内置 per-key 单飞信号保证原子加载，
 * 同一 key 并发请求仅一次穿透到 L2/DB。
 *
 * <p><b>L2 依赖可选：</b>L2Storage.isEnabled() == false 时自动降级为"L1 only"模式，不影响可用性。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 26.09.25
 */
@Slf4j
public class MultiLevelCacheTemplate<K, V> {

  /** 空结果占位对象（L1 层防穿透哨兵） */
  static final Object NULL_PLACEHOLDER = new Object();

  /** 空结果的 L2 标记 */
  private static final String EMPTY_RESULT_MARKER = "__NULL__";

  /** 空结果占位 TTL 比率（占位 TTL = 正常 TTL / 此值） */
  private static final int NULL_TTL_RATIO = 3;

  private final Cache<K, V> l1Cache;
  private final Function<K, V> loader;
  private final L2Storage<K, V> l2Storage;
  private final Duration l2Ttl;

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
    this.l2Storage = builder.l2Storage;
    this.l2Ttl = builder.l2Ttl;
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
    if (l2Storage != null && l2Storage.isEnabled()) {
      V l2Hit = l2Storage.get(key);
      if (l2Hit != null) {
        l1Cache.put(key, l2Hit);
        return l2Hit;
      }
      if (l2Storage.isNullMarker(key)) {
        // L2 存在空标记 → 回填 L1 占位并返回 null
        l1Cache.put(key, (V) NULL_PLACEHOLDER);
        return null;
      }
    }

    // L1 + L2 均未命中 → 原子加载（防击穿）
    return l1Cache.get(key, k -> loadFromSource(key));
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
   * 清空所有级缓存（仅 L1；L2 需业务侧按需清理）。
   */
  public void invalidateAll() {
    l1Cache.invalidateAll();
    log.info("[MultiLevelCache] L1 缓存已清空: name={}", l2Storage != null ? l2Storage.name() : "n/a");
  }

  /** 加载数据源（loader → L2 写入 → L1 回填） */
  @SuppressWarnings("unchecked")
  private V loadFromSource(K key) {
    V value = loader != null ? loader.apply(key) : null;
    if (value == null) {
      // 防穿透：写入占位符
      if (l2Storage != null && l2Storage.isEnabled()) {
        l2Storage.putNullMarker(key, reducedNullTtl());
      }
      return (V) NULL_PLACEHOLDER;
    }
    if (l2Storage != null && l2Storage.isEnabled()) {
      l2Storage.put(key, value, l2Ttl);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private boolean isNullPlaceholder(V value) {
    return value == null ? false : value == NULL_PLACEHOLDER;
  }

  private Duration reducedNullTtl() {
    return Duration.ofMillis(Math.max(1000, l2Ttl.toMillis() / NULL_TTL_RATIO));
  }

  // ==================== L2 存储 SPI ====================

  /**
   * L2 存储 SPI（各业务模块基于此接口实现 Redis 操作）。
   *
   * <p>实现类需处理：
   *
   * <ul>
   *   <li>key 前缀拼接（如 {@code "literule:rules:" + key}） — 避免不同业务模块 L2 key 冲突</li>
   *   <li>序列化 / 反序列化 — 推荐使用 JSON 库</li>
   *   <li>TTL 设置 — 空标记与正常值可使用不同 TTL</li>
   * </ul>
   *
   * @param <K> 键类型
   * @param <V> 值类型
   */
  public interface L2Storage<K, V> {

    /** 存储名称（用于日志标识） */
    String name();

    /** 当前 L2 是否可用 */
    boolean isEnabled();

    /**
     * 读取 L2 值。
     *
     * @param key 业务 key（不含前缀）
     * @return 反序列化后的值，不存在或反序列化失败返回 null
     */
    V get(K key);

    /**
     * 写入 L2 值。
     *
     * @param key 业务 key（不含前缀）
     * @param value 值
     * @param ttl TTL
     */
    void put(K key, V value, Duration ttl);

    /**
     * 写入空标记（防穿透）。
     *
     * @param key 业务 key（不含前缀）
     * @param ttl 空标记 TTL
     */
    void putNullMarker(K key, Duration ttl);

    /**
     * 检查 L2 存在空标记。
     *
     * @param key 业务 key（不含前缀）
     * @return true 如果存在空标记
     */
    boolean isNullMarker(K key);
  }

  // ==================== Builder ====================

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

  /** MultiLevelCacheTemplate 构建器 */
  public static class Builder<K, V> {
    private String name = "mlevel-cache";
    private CacheType l1Type = CacheType.TINYLFU;
    private long l1MaxSize = 1024;
    private Duration l1Ttl = Duration.ofMinutes(5);
    private Duration l2Ttl = Duration.ofMinutes(30);
    private L2Storage<K, V> l2Storage;
    private Function<K, V> loader;

    /** 设置缓存名称（用于监控标识和日志） */
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

    /** 设置 L2 TTL */
    public Builder<K, V> l2Ttl(Duration ttl) {
      this.l2Ttl = ttl;
      return this;
    }

    /** 设置 L2 存储 SPI 实现 */
    public Builder<K, V> l2Storage(L2Storage<K, V> storage) {
      this.l2Storage = storage;
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
