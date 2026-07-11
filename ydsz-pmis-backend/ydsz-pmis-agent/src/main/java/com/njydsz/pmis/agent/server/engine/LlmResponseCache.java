package com.njydsz.pmis.agent.server.engine.llm;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 响应缓存（P1-2 落地）。
 *
 * <p>对相同 (systemPrompt + userPrompt) 的请求进行 LRU 缓存，
 * 避免重复调用 LLM 浪费 Token 和时间。
 *
 * <p>设计要点：
 * <ul>
 *   <li>基于 {@link LinkedHashMap} 的 LRU 淘汰策略</li>
 *   <li>线程安全（synchronized），适用于低频写入场景</li>
 *   <li>支持 TTL 过期（默认 5 分钟），避免缓存陈旧</li>
 *   <li>仅缓存非空响应</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>Agent 评测：批量运行相同用例时避免重复调用</li>
 *   <li>开发调试：快速回放相同 prompt 的响应</li>
 *   <li>低频变更的配置类查询（如项目基础信息）</li>
 * </ul>
 *
 * <p><b>不适用场景</b>：
 * <ul>
 *   <li>需要实时性的查询（如项目最新进度）</li>
 *   <li>包含随机性/创造性的生成（如方案建议）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-2)
 */
@Slf4j
public class LlmResponseCache {

    /** 默认最大缓存条目数 */
    private static final int DEFAULT_MAX_SIZE = 200;

    /** 默认 TTL（5 分钟） */
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;

    /** LRU 缓存（通过 accessOrder=true 实现 LRU 淘汰） */
    private final LinkedHashMap<String, CacheEntry> cache;

    /** TTL 过期时间（毫秒） */
    private final long ttlMs;

    /** 缓存命中次数（用于统计） */
    private long hitCount = 0;

    /** 缓存未命中次数（用于统计） */
    private long missCount = 0;

    /**
     * 使用默认配置构造缓存。
     */
    public LlmResponseCache() {
        this(DEFAULT_MAX_SIZE, DEFAULT_TTL_MS);
    }

    /**
     * 自定义配置构造缓存。
     *
     * @param maxSize 最大缓存条目数
     * @param ttlMs   TTL 过期时间（毫秒）
     */
    public LlmResponseCache(int maxSize, long ttlMs) {
        this.ttlMs = ttlMs;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * 从缓存中获取响应。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 缓存的响应；未命中或已过期返回 null
     */
    public synchronized String get(String systemPrompt, String userPrompt) {
        String key = buildKey(systemPrompt, userPrompt);
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            missCount++;
            return null;
        }
        if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(key);
            missCount++;
            log.debug("[LlmCache] 缓存已过期, key={}", key.substring(0, Math.min(key.length(), 50)));
            return null;
        }
        hitCount++;
        log.debug("[LlmCache] 缓存命中, key={}", key.substring(0, Math.min(key.length(), 50)));
        return entry.response;
    }

    /**
     * 将响应存入缓存。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param response     LLM 响应
     */
    public synchronized void put(String systemPrompt, String userPrompt, String response) {
        if (response == null || response.isBlank()) {
            return;
        }
        String key = buildKey(systemPrompt, userPrompt);
        cache.put(key, new CacheEntry(response, System.currentTimeMillis()));
    }

    /**
     * 清空缓存。
     */
    public synchronized void clear() {
        cache.clear();
        hitCount = 0;
        missCount = 0;
        log.info("[LlmCache] 缓存已清空");
    }

    /**
     * 获取缓存命中率。
     *
     * @return 命中率（0.0 ~ 1.0）；无请求时返回 0
     */
    public synchronized double getHitRate() {
        long total = hitCount + missCount;
        return total > 0 ? (double) hitCount / total : 0;
    }

    /**
     * 获取当前缓存条目数。
     */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * 构建缓存 Key（systemPrompt + userPrompt 的 hash）。
     */
    private String buildKey(String systemPrompt, String userPrompt) {
        return (systemPrompt == null ? "" : systemPrompt)
                + "||"
                + (userPrompt == null ? "" : userPrompt);
    }

    /** 缓存条目 */
    private record CacheEntry(String response, long timestamp) {}
}
