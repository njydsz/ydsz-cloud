package com.njydsz.common.search.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.config.SearchProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索结果缓存服务
 * <p>
 * 基于 LRU + TTL 的轻量级内存缓存，用于缓存搜索结果。
 * 支持空结果缓存（防穿透），热点结果短 TTL 缓存（防雪崩）。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public class SearchCacheService {

    private final SearchProperties properties;
    private final LinkedHashMap<String, CacheEntry> cache;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public SearchCacheService(SearchProperties properties) {
        this.properties = properties;
        long maxSize = properties.getCache().getMaxSize();
        int capacity = (int) Math.min(maxSize, Integer.MAX_VALUE);
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > capacity;
            }
        };
    }

    /**
     * 获取缓存的搜索结果
     *
     * @param request 搜索请求
     * @return 缓存结果，不存在返回 null
     */
    public SearchResponse get(SearchRequest request) {
        if (!properties.getCache().isEnabled()) {
            return null;
        }
        String key = buildCacheKey(request);
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            if (System.currentTimeMillis() > entry.expireAt) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(key);
                } finally {
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
                return null;
            }
            return entry.response;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 缓存搜索结果
     *
     * @param request  搜索请求
     * @param response 搜索响应
     */
    public void put(SearchRequest request, SearchResponse response) {
        if (!properties.getCache().isEnabled()) {
            return;
        }
        String key = buildCacheKey(request);
        long ttlMs = properties.getCache().getTtl() * 1000;
        long expireAt = System.currentTimeMillis() + ttlMs;
        lock.writeLock().lock();
        try {
            cache.put(key, new CacheEntry(response, expireAt));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清空缓存
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            log.info("[SearchCache] 缓存已清空");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    private String buildCacheKey(SearchRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getKeyword()).append('|');
        sb.append(request.getTypes()).append('|');
        sb.append(request.getPage()).append('|');
        sb.append(request.getPageSize()).append('|');
        sb.append(request.isHighlight()).append('|');
        sb.append(request.isFuzzy()).append('|');
        sb.append(request.isTitleOnly()).append('|');
        sb.append(request.getTenantId()).append('|');
        sb.append(request.getUserId()).append('|');
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
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static class CacheEntry {
        final SearchResponse response;
        final long expireAt;

        CacheEntry(SearchResponse response, long expireAt) {
            this.response = response;
            this.expireAt = expireAt;
        }
    }
}
