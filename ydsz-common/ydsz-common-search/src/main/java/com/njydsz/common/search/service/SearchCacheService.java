package com.njydsz.common.search.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.util.security.HexUtils;

/**
 * 搜索缓存服务接口。
 *
 * <p>缓存热门查询结果。
 *
 * <p>降低 ES 压力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SearchCacheService {

  private final SearchProperties properties;
  private final ConcurrentHashMap<String, CacheEntry> cache;
  private final int maxSize;

  /** P1-4: 空结果哨兵值 — 区分缓存命中空结果和缓存未命中 */
  private static final SearchResponse EMPTY_SENTINEL = SearchResponse.builder().total(-1L).build();

  /** P1-4: 空结果缓存使用更短的 TTL（防穿透） */
  private static final long EMPTY_TTL_RATIO = 3; // 空结果 TTL = 正常 TTL / 3

  public SearchCacheService(SearchProperties properties) {
    this.properties = properties;
    long configMaxSize = properties.getCache().getMaxSize();
    this.maxSize = (int) Math.min(configMaxSize, Integer.MAX_VALUE);
    this.cache = new ConcurrentHashMap<>(Math.min(maxSize, 256));
  }

  /**
   * 获取缓存的搜索结果
   *
   * @param request 搜索请求
   * @return 缓存结果，不存在返回 null；空结果返回 SearchResponse.empty()
   */
  public SearchResponse get(SearchRequest request) {
    if (!properties.getCache().isEnabled()) {
      return null;
    }
    String key = buildCacheKey(request);
    CacheEntry entry = cache.get(key);
    if (entry == null) {
      return null;
    }
    if (System.currentTimeMillis() > entry.expireAt) {
      // P1-1: 使用 ConcurrentHashMap 原子删除，无锁升级死锁风险
      cache.remove(key, entry);
      return null;
    }
    // P1-4: 空结果哨兵返回空响应
    if (entry.response == EMPTY_SENTINEL) {
      return SearchResponse.empty(request.getPage(), request.getPageSize());
    }
    return entry.response;
  }

  /**
   * 缓存搜索结果
   *
   * @param request 搜索请求
   * @param response 搜索响应
   */
  public void put(SearchRequest request, SearchResponse response) {
    if (!properties.getCache().isEnabled()) {
      return;
    }
    String key = buildCacheKey(request);
    long ttlMs = properties.getCache().getTtl() * 1000;

    // P1-4: 空结果使用更短 TTL 防穿透
    if (response.getTotal() == 0) {
      ttlMs = ttlMs / EMPTY_TTL_RATIO;
      cache.put(key, new CacheEntry(EMPTY_SENTINEL, System.currentTimeMillis() + ttlMs));
    } else {
      cache.put(key, new CacheEntry(response, System.currentTimeMillis() + ttlMs));
    }

    // P1-1: 惰性淘汰 — 超过最大容量时清理过期条目
    if (cache.size() > maxSize) {
      evictExpired();
    }
  }

  /** 清空缓存 */
  public void clear() {
    cache.clear();
    log.info("[SearchCache] 缓存已清空");
  }

  /**
   * 获取缓存大小。
   *
   * @return 缓存条目数
   */
  public int size() {
    return cache.size();
  }

  /** P1-1: 惰性淘汰过期条目 */
  private void evictExpired() {
    long now = System.currentTimeMillis();
    AtomicInteger removed = new AtomicInteger(0);
    cache.forEach(
        (k, v) -> {
          if (now > v.expireAt) {
            if (cache.remove(k, v)) {
              removed.incrementAndGet();
            }
          }
        });
    // 如果清理过期后仍然超限，随机淘汰
    if (cache.size() > maxSize) {
      int toRemove = cache.size() - maxSize;
      List<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(cache.entrySet());
      entries.sort(Comparator.comparingLong(e -> e.getValue().expireAt));
      for (int i = 0; i < toRemove && i < entries.size(); i++) {
        cache.remove(entries.get(i).getKey(), entries.get(i).getValue());
      }
    }
    if (removed.get() > 0) {
      log.debug("[SearchCache] 惰性淘汰过期条目: {}", removed.get());
    }
  }

  /** P2-9: 构建缓存键 — 过滤条件排序后再拼接，确保顺序无关 */
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
      List<String> sortedTypes = new ArrayList<>(request.getTypes());
      sortedTypes.sort(Comparator.naturalOrder());
      sb.append(sortedTypes).append('|');
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

  /**
   * 搜索缓存条目。
   *
   * <p>缓存响应体与过期时间戳，过期即视为未命中并从缓存中移除。
   */
  private static class CacheEntry {
    /** 缓存的搜索结果 */
    final SearchResponse response;

    /** 过期时间戳（毫秒） */
    final long expireAt;

    CacheEntry(SearchResponse response, long expireAt) {
      this.response = response;
      this.expireAt = expireAt;
    }
  }
}
