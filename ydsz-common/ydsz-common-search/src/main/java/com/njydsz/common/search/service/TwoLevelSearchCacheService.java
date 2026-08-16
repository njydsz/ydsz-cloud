package com.njydsz.common.search.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.config.SearchProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 二级搜索缓存服务（Caffeine L1 + Redis L2）。
 *
 * <p>架构设计：
 * <ul>
 *   <li>L1（Caffeine）：本地内存缓存，毫秒级响应，容量受限（默认 500 条），TTL 短（默认 30 秒）</li>
 *   <li>L2（Redis）：分布式缓存，微秒级响应，容量大（默认 5000 条），TTL 长（默认 60 秒）</li>
 * </ul>
 *
 * <p>缓存读取顺序：L1 → L2 → 引擎。L2 命中后回填 L1。
 * 缓存写入顺序：同时写入 L1 和 L2。
 *
 * <p>对标行业实践：
 * <ul>
 *   <li>Elasticsearch：Node Query Cache + Request Cache + Shard Query Cache 三级</li>
 *   <li>美团搜索：Cellar（Redis Cluster）+ 本地缓存双层</li>
 *   <li>阿里 OpenSearch：分布式缓存层 + 客户端本地缓存</li>
 * </ul>
 *
 * <p>Redis 不可用时自动降级到纯 L1 模式，保证高可用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TwoLevelSearchCacheService {

    /** 空结果哨兵值 — 区分缓存命中空结果和缓存未命中 */
    private static final String EMPTY_SENTINEL = "EMPTY";

    /** 空结果缓存使用更短的 TTL（防穿透） */
    private static final int EMPTY_TTL_RATIO = 3;

    /** Redis key 前缀 */
    private static final String REDIS_KEY_PREFIX = "search:cache:v2:";

    private final Cache<String, SearchResponse> l1Cache;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final SearchProperties properties;
    private final ObjectMapper objectMapper;

    public TwoLevelSearchCacheService(ObjectProvider<StringRedisTemplate> redisProvider,
                                       SearchProperties properties,
                                       ObjectMapper objectMapper) {
        this.redisProvider = redisProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;

        // L1 Caffeine 配置：容量为 L2 的 1/10，TTL 为 L2 的 1/2
        long l1MaxSize = Math.max(100, properties.getCache().getMaxSize() / 10);
        long l1TtlSeconds = Math.max(10, properties.getCache().getTtl() / 2);

        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .expireAfterWrite(l1TtlSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();

        log.info("[TwoLevelCache] 初始化完成: l1MaxSize={}, l1Ttl={}s, l2Ttl={}s",
                l1MaxSize, l1TtlSeconds, properties.getCache().getTtl());
    }

    /**
     * 获取缓存的搜索结果。
     *
     * <p>读取顺序：L1 → L2 → null。L2 命中后异步回填 L1。
     *
     * @param request 搜索请求
     * @return 缓存结果，不存在返回 null；空结果返回 SearchResponse.empty()
     */
    public SearchResponse get(SearchRequest request) {
        if (!properties.getCache().isEnabled()) {
            return null;
        }
        String key = buildCacheKey(request);

        // L1 查询
        SearchResponse l1Result = l1Cache.getIfPresent(key);
        if (l1Result != null) {
            log.debug("[TwoLevelCache] L1 命中: key={}", key);
            return l1Result;
        }

        // L2 查询
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                String json = redis.opsForValue().get(REDIS_KEY_PREFIX + key);
                if (json != null) {
                    if (EMPTY_SENTINEL.equals(json)) {
                        log.debug("[TwoLevelCache] L2 命中空结果: key={}", key);
                        return SearchResponse.empty(request.getPage(), request.getPageSize());
                    }
                    SearchResponse l2Result = objectMapper.readValue(json, SearchResponse.class);
                    // 回填 L1
                    l1Cache.put(key, l2Result);
                    log.debug("[TwoLevelCache] L2 命中，回填 L1: key={}", key);
                    return l2Result;
                }
            } catch (Exception e) {
                log.debug("[TwoLevelCache] L2 读取失败，降级到 L1: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 缓存搜索结果。
     *
     * <p>同时写入 L1 和 L2。空结果使用更短 TTL 防穿透。
     *
     * @param request  搜索请求
     * @param response 搜索响应
     */
    public void put(SearchRequest request, SearchResponse response) {
        if (!properties.getCache().isEnabled()) {
            return;
        }
        String key = buildCacheKey(request);

        // 写入 L1
        l1Cache.put(key, response);

        // 写入 L2
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                long ttlSeconds = properties.getCache().getTtl();
                if (response.getTotal() == 0) {
                    ttlSeconds = ttlSeconds / EMPTY_TTL_RATIO;
                    redis.opsForValue().set(REDIS_KEY_PREFIX + key, EMPTY_SENTINEL,
                            ttlSeconds, TimeUnit.SECONDS);
                } else {
                    String json = objectMapper.writeValueAsString(response);
                    redis.opsForValue().set(REDIS_KEY_PREFIX + key, json,
                            ttlSeconds, TimeUnit.SECONDS);
                }
            } catch (JsonProcessingException e) {
                log.warn("[TwoLevelCache] L2 序列化失败: {}", e.getMessage());
            } catch (Exception e) {
                log.debug("[TwoLevelCache] L2 写入失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 清空缓存（L1 + L2 双端清空）。
     */
    public void clear() {
        // 清空 L1
        l1Cache.invalidateAll();

        // 清空 L2（使用 scan + delete 避免 KEYS 命令阻塞）
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                var connection = redis.getConnectionFactory().getConnection();
                var scanOptions = org.springframework.data.redis.core.ScanOptions
                        .scanOptions().match(REDIS_KEY_PREFIX + "*").count(100).build();
                var cursor = connection.keyCommands().scan(scanOptions);
                int deleted = 0;
                while (cursor.hasNext()) {
                    connection.keyCommands().del(cursor.next());
                    deleted++;
                }
                log.info("[TwoLevelCache] L2 清空完成: {} keys deleted", deleted);
            } catch (Exception e) {
                log.warn("[TwoLevelCache] L2 清空失败: {}", e.getMessage());
            }
        }

        log.info("[TwoLevelCache] 缓存已清空");
    }

    /**
     * 获取缓存大小（L1 当前条目数）。
     *
     * @return L1 缓存条目数
     */
    public int size() {
        return (int) l1Cache.estimatedSize();
    }

    /**
     * 获取缓存统计信息。
     *
     * @return Caffeine 缓存统计
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return l1Cache.stats();
    }

    // ==================== 私有方法 ====================

    private StringRedisTemplate getRedis() {
        return redisProvider.getIfAvailable();
    }

    private String buildCacheKey(SearchRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getKeyword()).append('|');
        sb.append(request.getPage()).append('|');
        sb.append(request.getPageSize()).append('|');
        sb.append(request.isHighlight()).append('|');
        sb.append(request.isFuzzy()).append('|');
        sb.append(request.isTitleOnly()).append('|');
        sb.append(request.getTenantId()).append('|');
        sb.append(request.getUserId()).append('|');
        if (request.getTypes() != null) {
            var sortedTypes = new java.util.ArrayList<>(request.getTypes());
            sortedTypes.sort(String::compareTo);
            sb.append(sortedTypes).append('|');
        }
        if (request.getFilters() != null) {
            sb.append(request.getFilters()).append('|');
        }
        if (request.getSortBy() != null) {
            sb.append(request.getSortBy()).append('|');
            sb.append(request.isAscending()).append('|');
        }
        if (request.getCursor() != null) {
            sb.append(request.getCursor()).append('|');
        }
        return md5(sb.toString());
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
