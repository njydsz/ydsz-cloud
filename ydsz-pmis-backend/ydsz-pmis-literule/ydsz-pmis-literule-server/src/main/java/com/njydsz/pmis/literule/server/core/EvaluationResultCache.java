package com.njydsz.pmis.literule.server.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 评估结果缓存（P2-3 高性能优化）
 *
 * <p>缓存规则引擎的评估结果，避免对相同事实数据的重复计算。
 * 当同一上下文（scenario + tenantId + environment + facts）在 TTL 内再次评估时，
 * 直接返回缓存结果，跳过全部规则遍历。
 *
 * <h3>缓存键设计</h3>
 * <p>缓存键由以下维度组合的哈希值构成：
 * <ul>
 *   <li>{@code scenario}：业务场景</li>
 *   <li>{@code tenantId}：租户 ID</li>
 *   <li>{@code environment}：环境标识</li>
 *   <li>{@code facts}：事实数据快照（按 key 排序后哈希）</li>
 * </ul>
 * 相同的上下文维度会产生相同的缓存键，保证缓存命中率。
 *
 * <h3>淘汰策略</h3>
 * <ul>
 *   <li><b>TTL 过期</b>：缓存条目在写入后经过 TTL 时间自动失效</li>
 *   <li><b>LRU 淘汰</b>：当缓存条目数超过 maxSize 时，淘汰最近最少访问的条目</li>
 * </ul>
 *
 * <h3>性能预期</h3>
 * <p>在重复评估率高的场景（如批量数据回放、风控规则试运行），
 * 缓存命中率可达 60%~90%，端到端评估耗时降低 50%~80%。
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link ReentrantLock} 保护 LRU 链表操作，{@link AtomicLong} 统计计数器。
 * 适用于高并发读写场景。
 *
 * <h3>使用示例</h3>
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
 * @since 2.0.0
 */
@Slf4j
public class EvaluationResultCache {

    /** 默认 TTL（5 分钟） */
    public static final long DEFAULT_TTL_MS = 300_000L;

    /** 默认最大缓存条目数 */
    public static final int DEFAULT_MAX_SIZE = 10_000;

    /** 缓存条目 */
    private static class CacheEntry {
        final List<RuleResult> results;
        final long expireAt;

        CacheEntry(List<RuleResult> results, long expireAt) {
            this.results = results;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    /** LRU 缓存（LinkedHashMap 按访问顺序） */
    private final LinkedHashMap<String, CacheEntry> cache;

    /** TTL（毫秒） */
    private final long ttlMs;

    /** 最大缓存条目数 */
    private final int maxSize;

    /** 读写锁（保护 LRU 操作） */
    private final ReentrantLock lock = new ReentrantLock();

    /** 统计：命中次数 */
    private final AtomicLong hitCount = new AtomicLong(0);

    /** 统计：未命中次数 */
    private final AtomicLong missCount = new AtomicLong(0);

    /** 统计：淘汰次数 */
    private final AtomicLong evictionCount = new AtomicLong(0);

    /**
     * 使用默认配置创建缓存
     */
    public EvaluationResultCache() {
        this(DEFAULT_TTL_MS, DEFAULT_MAX_SIZE);
    }

    /**
     * 指定 TTL 和最大条目数创建缓存
     *
     * @param ttlMs  TTL（毫秒），&le; 0 表示不过期
     * @param maxSize 最大缓存条目数，&le; 0 表示不限
     */
    public EvaluationResultCache(long ttlMs, int maxSize) {
        this.ttlMs = ttlMs > 0 ? ttlMs : Long.MAX_VALUE;
        this.maxSize = maxSize > 0 ? maxSize : Integer.MAX_VALUE;
        this.cache = new LinkedHashMap<>(16, 0.75f, true);
        log.info("[EvalCache] 评估结果缓存已初始化（ttlMs={}, maxSize={}）", this.ttlMs, this.maxSize);
    }

    /**
     * 尝试获取缓存结果
     *
     * @param context 规则上下文
     * @return 缓存的评估结果；未命中或已过期返回 null
     */
    public List<RuleResult> get(RuleContext context) {
        String key = buildCacheKey(context);
        lock.lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                missCount.incrementAndGet();
                return null;
            }
            if (entry.isExpired()) {
                cache.remove(key);
                missCount.incrementAndGet();
                evictionCount.incrementAndGet();
                return null;
            }
            // 访问后 LinkedHashMap 自动移到末尾（LRU）
            hitCount.incrementAndGet();
            // 返回防御性副本
            return new ArrayList<>(entry.results);
        } finally {
            lock.unlock();
        }
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
        long expireAt = System.currentTimeMillis() + ttlMs;
        CacheEntry entry = new CacheEntry(
                Collections.unmodifiableList(new ArrayList<>(results)), expireAt);

        lock.lock();
        try {
            // LRU 淘汰
            while (cache.size() >= maxSize) {
                evictOldest();
            }
            cache.put(key, entry);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清除全部缓存
     */
    public void clear() {
        lock.lock();
        try {
            int size = cache.size();
            cache.clear();
            log.info("[EvalCache] 缓存已清空（cleared={}）", size);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前缓存条目数
     *
     * @return 条目数
     */
    public int size() {
        lock.lock();
        try {
            return cache.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取缓存命中率
     *
     * @return 命中率（0.0 ~ 1.0）；无请求时返回 0.0
     */
    public double getHitRate() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * 获取命中次数
     *
     * @return 命中次数
     */
    public long getHitCount() {
        return hitCount.get();
    }

    /**
     * 获取未命中次数
     *
     * @return 未命中次数
     */
    public long getMissCount() {
        return missCount.get();
    }

    /**
     * 获取淘汰次数
     *
     * @return 淘汰次数
     */
    public long getEvictionCount() {
        return evictionCount.get();
    }

    /**
     * 获取缓存统计摘要
     *
     * @return 统计摘要文本
     */
    public String getStatsSummary() {
        return String.format(
                "[EvalCache] size=%d, hits=%d, misses=%d, hitRate=%.4f, evictions=%d",
                size(), getHitCount(), getMissCount(), getHitRate(), getEvictionCount());
    }

    // ==================== 内部实现 ====================

    /**
     * 淘汰最旧的条目（LRU 链表头部）
     */
    private void evictOldest() {
        if (cache.isEmpty()) return;
        // LinkedHashMap 的迭代器按访问顺序，第一个元素即最旧
        String oldestKey = cache.keySet().iterator().next();
        cache.remove(oldestKey);
        evictionCount.incrementAndGet();
    }

    /**
     * 构建缓存键
     *
     * <p>缓存键由 scenario + tenantId + environment + facts 哈希组成。
     * facts 按 key 排序后拼接，保证相同事实数据产生相同键。
     *
     * @param context 规则上下文
     * @return 缓存键
     */
    private String buildCacheKey(RuleContext context) {
        Map<String, Object> facts = context.getFacts();

        // 按 key 排序后拼接
        StringBuilder sb = new StringBuilder(256);
        sb.append(context.getScenario()).append('|');
        sb.append(context.getTenantId()).append('|');
        sb.append(context.getEnvironment()).append('|');

        facts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    sb.append(e.getKey()).append('=');
                    if (e.getValue() != null) {
                        sb.append(e.getValue().hashCode());
                    } else {
                        sb.append("null");
                    }
                    sb.append(';');
                });

        return Integer.toHexString(sb.toString().hashCode());
    }
}
