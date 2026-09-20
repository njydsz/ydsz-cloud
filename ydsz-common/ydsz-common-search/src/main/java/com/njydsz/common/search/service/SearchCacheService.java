package com.njydsz.common.search.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.util.security.HexUtils;

/**
 * 搜索结果缓存服务（Caffeine L1 + Redis L2 二级缓存）。
 *
 * <p>两级缓存架构：
 *
 * <ul>
 *   <li>L1 Caffeine（进程内） — 亚毫秒级命中，小容量短 TTL（10s），减轻热点 key 竞争</li>
 *   <li>L2 Redis（跨进程） — 毫秒级命中，按配置 TTL，集群多节点共享</li>
 * </ul>
 *
 * <p>读取顺序：L1 → L2 → Engine；写入顺序：L1 + L2 同时写入。L2 不可用时降级到仅 L1（不影响可用性）。
 *
 * <p>空结果使用更短的 TTL（整体 TTL / {@link #EMPTY_TTL_RATIO}）防缓存穿透。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SearchCacheService {

  private static final String L2_CACHE_PREFIX = "search:cache:l2:";

  /** 空结果占位符（序列化到 Redis 的特殊标记） */
  private static final String EMPTY_RESULT_MARKER = "__EMPTY__";

  /** 空结果缓存使用更短的 TTL（防穿透） */
  private static final int EMPTY_TTL_RATIO = 3;

  private final Cache<String, SearchResponse> l1Cache;
  private final Supplier<StringRedisTemplate> redisProvider;
  private final SearchProperties properties;

  /**
   * 创建搜索缓存服务（兼容旧构造器，等价于 {@code redisProvider = () -> null}）。
   *
   * @param properties 搜索配置
   */
  public SearchCacheService(SearchProperties properties) {
    this(properties, () -> null);
  }

  /**
   * 创建搜索缓存服务。
   *
   * @param properties 搜索配置
   * @param redisProvider Redis 提供者（用于 L2；返回 {@code null} 时自动禁用 L2）
   */
  public SearchCacheService(
      SearchProperties properties, Supplier<StringRedisTemplate> redisProvider) {
    this.properties = properties;
    this.redisProvider = redisProvider;

    long l1Size = properties.getCache().getL1MaxSize();
    long l1Ttl = properties.getCache().getL1Ttl();
    this.l1Cache =
        Caffeine.newBuilder()
            .maximumSize(Math.max(64, l1Size))
            .expireAfterWrite(Duration.ofSeconds(Math.max(1, l1Ttl)))
            .build();
  }

  /**
   * 获取缓存的搜索结果。
   *
   * <p>先查 L1（进程内），再查 L2（Redis）；L2 命中后回填 L1 以加速后续请求。
   *
   * @param request 搜索请求
   * @return 缓存结果，不存在返回 null；空结果返回 SearchResponse.empty()
   */
  public SearchResponse get(SearchRequest request) {
    if (!properties.getCache().isEnabled()) {
      return null;
    }
    String key = buildCacheKey(request);

    // L1: 进程内 Caffeine
    SearchResponse l1Hit = l1Cache.getIfPresent(key);
    if (l1Hit != null) {
      return l1Hit;
    }

    // L2: Redis
    StringRedisTemplate redis = getRedis();
    if (redis != null) {
      try {
        String json = redis.opsForValue().get(L2_CACHE_PREFIX + key);
        if (json != null) {
          if (EMPTY_RESULT_MARKER.equals(json)) {
            SearchResponse empty = SearchResponse.empty(request.getPage(), request.getPageSize());
            l1Cache.put(key, empty); // 回填 L1
            return empty;
          }
          SearchResponse response = YdszJson.fromJson(json, SearchResponse.class);
          if (response != null) {
            l1Cache.put(key, response); // 回填 L1
            return response;
          }
        }
      } catch (Exception e) {
        log.debug("[SearchCache] Redis 反序列化失败: {}", e.getMessage());
      }
    }

    return null;
  }

  /**
   * 缓存搜索结果。
   *
   * <p>同时写入 L1（进程内）和 L2（Redis，如果可用）。空结果使用更短的 TTL。
   *
   * @param request 搜索请求
   * @param response 搜索响应
   */
  public void put(SearchRequest request, SearchResponse response) {
    if (!properties.getCache().isEnabled()) {
      return;
    }
    String key = buildCacheKey(request);

    // L1: 始终写入 Caffeine
    l1Cache.put(key, response);

    // L2: 写入 Redis
    StringRedisTemplate redis = getRedis();
    if (redis == null) {
      return;
    }
    try {
      long ttl = computeTtlSeconds(response);
      if (response.getTotal() == 0) {
        redis.opsForValue().set(
            L2_CACHE_PREFIX + key, EMPTY_RESULT_MARKER, Duration.ofSeconds(ttl));
      } else {
        String json = YdszJson.toJson(response);
        redis.opsForValue().set(L2_CACHE_PREFIX + key, json, Duration.ofSeconds(ttl));
      }
    } catch (Exception e) {
      log.debug("[SearchCache] Redis 序列化/写入失败: {}", e.getMessage());
    }
  }

  /** 清空所有级缓存 */
  public void clear() {
    l1Cache.invalidateAll();
    log.info("[SearchCache] L1 缓存已清空（entries after: {})", l1Cache.estimatedSize());
  }

  /**
   * 获取当前 L1 缓存条数。
   *
   * @return 估计条数（Caffeine 返回近似值）
   */
  public long size() {
    return l1Cache.estimatedSize();
  }

  private long computeTtlSeconds(SearchResponse response) {
    long baseTtl = properties.getCache().getTtl();
    if (response != null && response.getTotal() == 0) {
      return Math.max(1, baseTtl / EMPTY_TTL_RATIO);
    }
    return baseTtl;
  }

  /** 构建缓存键（MD5 规范化） */
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
      request.getTypes().stream().sorted().forEach(t -> sb.append(t).append(','));
      sb.append('|');
    }
    if (request.getFilters() != null) {
      sb.append(request.getFilters()).append('|');
    }
    if (request.getSortBy() != null) {
      sb.append(request.getSortBy()).append('|');
      sb.append(request.isAscending()).append('|');
    }
    return md5(sb.toString());
  }

  private String md5(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexUtils.encode(digest);
    } catch (Exception e) {
      return Integer.toHexString(input.hashCode());
    }
  }

  private StringRedisTemplate getRedis() {
    try {
      return redisProvider.get();
    } catch (Exception e) {
      return null;
    }
  }
}
