package com.njydsz.pmis.common.file.storage;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存 Map 的分片上下文存储实现（降级方案）
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public class InMemoryMultipartContextStore implements MultipartContextStore {

    /** 缓存最大容量，超出时自动淘汰最久未访问的条目 */
    private static final int MAX_CACHE_SIZE = 1000;

    /** 基于 LRU 策略的内存缓存 */
    private final Map<String, MultipartContextData> cache = createBoundedCache();

    private static Map<String, MultipartContextData> createBoundedCache() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, MultipartContextData> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });
    }

    @Override
    public void save(String uploadId, MultipartContextData context, long ttlSeconds) {
        cache.put(uploadId, context);
    }

    @Override
    public MultipartContextData get(String uploadId) {
        return cache.get(uploadId);
    }

    @Override
    public void remove(String uploadId) {
        cache.remove(uploadId);
    }

    @Override
    public Map<String, MultipartContextData> getAll() {
        return new ConcurrentHashMap<>(cache);
    }

    @Override
    public void cleanExpired(int timeoutMinutes) {
        long cutoffTime = System.currentTimeMillis() - (timeoutMinutes * 60L * 1000L);
        cache.entrySet().removeIf(entry -> {
            MultipartContextData ctx = entry.getValue();
            return ctx != null && ctx.lastAccessTime() < cutoffTime;
        });
    }
}
