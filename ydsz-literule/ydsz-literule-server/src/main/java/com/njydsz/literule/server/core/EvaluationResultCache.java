package com.njydsz.literule.server.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;

/**
 * 评估结果缓存（P1-1 高性能优化 - 基于 Caffeine）
 *
 * <p>使用 Caffeine 替换手工实现的 LRU+TTL 缓存，获得：
 *
 * <ul>
 *   <li>更高的并发性能（基于 ConcurrentHashMap 的分段锁）
 *   <li>更优的淘汰策略（W-TinyLFU 窗口淘汰算法）
 *   <li>内置统计指标（命中率、加载时间、淘汰数）
 *   <li>异步加载与刷新支持
 * </ul>
 *
 * <p>缓存规则引擎的评估结果，避免对相同事实数据的重复计算。 当同一上下文（scenario + tenantId + environment + facts）在 TTL 内再次评估时，
 * 直接返回缓存结果，跳过全部规则遍历。
 *
 * <h3>缓存键设计</h3>
 *
 * <p>缓存键由以下维度组合的哈希值构成：
 *
 * <ul>
 *   <li>{@code scenario}：业务场景
 *   <li>{@code tenantId}：租户 ID
 *   <li>{@code environment}：环境标识
 *   <li>{@code facts}：事实数据快照（按 key 排序后哈希）
 * </ul>
 *
 * <h3>淘汰策略</h3>
 *
 * <ul>
 *   <li><b>TTL 过期</b>：缓存条目在写入后经过 TTL 时间自动失效
 *   <li><b>W-TinyLFU 淘汰</b>：基于访问频率的智能淘汰，优于传统 LRU
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>
 * // 创建缓存（TTL=5分钟，maxSize=10000）
 * EvaluationResultCache cache = new EvaluationResultCache(300_000L, 10_000);
 *
 * // 尝试获取缓存
 * List&lt;RuleResult&gt; cached = cache.get(context);
 * if (cached != null) {
 *     return cached;  // 缓存命中
 * }
 *
 * // 缓存未命中，执行评估
 * List&lt;RuleResult&gt; results = engine.evaluate(context);
 * cache.put(context, results);  // 写入缓存
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class EvaluationResultCache {

    /** 纳秒到毫秒的换算系数 */
  private static final double NANOS_PER_MILLI = 1_000_000.0;

  /** 默认 TTL（5 分钟） */
  public static final long DEFAULT_TTL_MS = 300_000L;

  /** 默认最大缓存条目数 */
  public static final int DEFAULT_MAX_SIZE = 10_000;

  /** 缓存实例（基于 ydsz-common-cache 实现） */
  private final Cache<String, List<RuleResult>> cache;

  /**
   * 规则编码 → 涉及该规则的缓存键集合（P1 精准失效反向索引）
   *
   * <p>规则变更时按 ruleCode 精准 evict 相关缓存条目， 避免全量 {@link #clear()} 导致未变更规则的缓存全部失效。
   */
  private final Map<String, Set<String>> ruleToCacheKeys = new ConcurrentHashMap<>();

  /** 使用默认配置创建缓存 */
  public EvaluationResultCache() {
    this(DEFAULT_TTL_MS, DEFAULT_MAX_SIZE);
  }

  /**
   * 指定 TTL 和最大条目数创建缓存
   *
   * @param ttlMs TTL（毫秒），&le; 0 表示不过期
   * @param maxSize 最大缓存条目数，&le; 0 表示不限
   */
  public EvaluationResultCache(long ttlMs, int maxSize) {
    // CHECKSTYLE.OFF: RegexpSinglelineJava - 使用 ydsz-common-cache 替代 Caffeine
    // 显式类型见证：javac 对链式泛型方法调用无法从赋值目标推断 K/V
    CacheBuilder<String, List<RuleResult>> builder =
        YdszCache.<String, List<RuleResult>>newBuilder()
            .maximumSize(maxSize > 0 ? maxSize : -1)
            .recordStats();
    if (ttlMs > 0) {
      builder.expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS);
    }
    this.cache = builder.build();
    // CHECKSTYLE.ON: RegexpSinglelineJava
    log.info(
        "[EvalCache] 评估结果缓存已初始化（ttlMs={}, maxSize={}, implementation=ydsz-common-cache）",
        ttlMs > 0 ? ttlMs : "unlimited",
        maxSize > 0 ? maxSize : "unlimited");
  }

  /**
   * 尝试获取缓存结果
   *
   * @param context 规则上下文
   * @return 缓存的评估结果；未命中或已过期返回空列表
   */
  public List<RuleResult> get(RuleContext context) {
    String key = buildCacheKey(context);
    List<RuleResult> result = cache.getIfPresent(key);
    if (result == null) {
      return Collections.emptyList();
    }
    // 返回防御性副本
    return new ArrayList<>(result);
  }

  /**
   * 写入缓存
   *
   * @param context 规则上下文
   * @param results 评估结果
   */
  public void put(RuleContext context, List<RuleResult> results) {
    if (results == null) {
      return;
    }
    String key = buildCacheKey(context);
    List<RuleResult> immutableResults = Collections.unmodifiableList(new ArrayList<>(results));
    cache.put(key, immutableResults);
    // P1 精准失效：建立 ruleCode → cacheKey 反向索引
    for (RuleResult result : results) {
      if (result.getRuleCode() != null) {
        ruleToCacheKeys
            .computeIfAbsent(result.getRuleCode(), k -> ConcurrentHashMap.newKeySet())
            .add(key);
      }
    }
  }

  /**
   * 按规则编码精准失效缓存（P1 精准 evict）
   *
   * <p>规则注册/注销/变更时调用，仅删除评估结果涉及该规则的缓存条目， 保留其他规则的缓存（对比全量 {@link #clear()} 显著减少缓存抖动）。
   *
   * @param ruleCode 规则编码
   */
  public void invalidateRule(String ruleCode) {
    if (ruleCode == null) {
      return;
    }
    Set<String> keys = ruleToCacheKeys.remove(ruleCode);
    if (keys == null || keys.isEmpty()) {
      return;
    }
    int evicted = 0;
    for (String key : keys) {
      if (cache.getIfPresent(key) != null) {
        cache.invalidate(key);
        evicted++;
      }
    }
    log.debug("[EvalCache] 按规则精准失效完成: ruleCode={}, evicted={}", ruleCode, evicted);
  }

  /**
   * 触发缓存清理（执行异步淘汰任务）。
   *
   * <p>Caffeine 的淘汰是异步的，调用此方法可立即执行待处理的淘汰任务， 确保 {@link #size()} 反映最新的条目数。
   */
  public void cleanUp() {
    cache.cleanUp();
  }

  /** 清除全部缓存 */
  public void clear() {
    long size = cache.estimatedSize();
    cache.invalidateAll();
    ruleToCacheKeys.clear();
    log.info("[EvalCache] 缓存已清空（cleared≈{}）", size);
  }

  /**
   * 获取当前缓存条目数
   *
   * @return 条目数
   */
  public int size() {
    return (int) cache.estimatedSize();
  }

  /**
   * 获取缓存命中率
   *
   * @return 命中率（0.0 ~ 1.0）；无请求时返回 0.0
   */
  public double getHitRate() {
    return cache.getHitRate();
  }

  /**
   * 获取命中次数
   *
   * @return 命中次数
   */
  public long getHitCount() {
    return cache.getStats().getHitCount();
  }

  /**
   * 获取未命中次数
   *
   * @return 未命中次数
   */
  public long getMissCount() {
    return cache.getStats().getMissCount();
  }

  /**
   * 获取淘汰次数
   *
   * @return 淘汰次数
   */
  public long getEvictionCount() {
    return cache.getStats().getEvictionCount();
  }

  /**
   * 获取缓存统计摘要
   *
   * @return 统计摘要文本
   */
  public String getStatsSummary() {
    CacheStats stats = cache.getStats();
    return String.format(
        "[EvalCache] size=%d, hits=%d, misses=%d, hitRate=%.4f, evictions=%d, avgLoadTime=%.2fms",
        size(),
        stats.getHitCount(),
        stats.getMissCount(),
        stats.getHitRate(),
        stats.getEvictionCount(),
        stats.getAverageLoadPenalty() / NANOS_PER_MILLI);
  }

  // ==================== 内部实现 ====================

  /**
   * 构建缓存键（P1-1：使用 CacheKeyBuilder 生成固定长度哈希键）
   *
   * <p>替代原有的全量字符串拼接方案，将键长从 O(总 facts 序列化长度) 降至固定 ~80 字符， 减少内存占用和 equals 比较开销。
   *
   * @param context 规则上下文
   * @return 缓存键
   */
  private String buildCacheKey(RuleContext context) {
    return CacheKeyBuilder.buildKey(context);
  }
}
