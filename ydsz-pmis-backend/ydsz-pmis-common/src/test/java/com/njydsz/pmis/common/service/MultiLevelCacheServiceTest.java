package com.njydsz.pmis.common.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MultiLevelCacheService 多级缓存服务单元测试
 *
 * <p>覆盖 L1(Caffeine) → L2(Redis) → DB(loader) 读取流程,
 * 以及 put/evict/evictAll/evictByPattern/clearAll 写入与失效流程.
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiLevelCacheService 多级缓存服务测试")
class MultiLevelCacheServiceTest {

    @Mock
    private Cache<String, Object> localCache;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private MultiLevelCacheService cacheService;

    private static final String REDIS_PREFIX = "pmis:mc:";

    // ==================== get ====================

    @Test
    @DisplayName("正常场景：L1 命中直接返回，不查 L2 和 DB")
    void get_L1命中_直接返回() {
        String key = "user:1";
        String cached = "value-from-l1";
        when(localCache.getIfPresent(key)).thenReturn(cached);

        String result = cacheService.get(key, String.class, () -> "from-db");

        assertEquals(cached, result);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("正常场景：L1 未命中 L2 命中，回填 L1 并返回")
    void get_L1未命中L2命中_回填L1() {
        String key = "user:2";
        String l2Value = "value-from-l2";
        when(localCache.getIfPresent(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(l2Value);

        String result = cacheService.get(key, String.class, () -> "from-db");

        assertEquals(l2Value, result);
        verify(localCache).put(key, l2Value);
    }

    @Test
    @DisplayName("正常场景：L1/L2 均未命中，调用 loader 回填 L1+L2")
    void get_两级均未命中_调用loader回填() {
        String key = "user:3";
        String dbValue = "from-db";
        Duration ttl = Duration.ofMinutes(10);
        when(localCache.getIfPresent(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(null);

        String result = cacheService.get(key, String.class, () -> dbValue, ttl);

        assertEquals(dbValue, result);
        verify(localCache).put(key, dbValue);
        verify(valueOperations).set(REDIS_PREFIX + key, dbValue, ttl);
    }

    @Test
    @DisplayName("边界场景：loader 返回 null 不回填缓存")
    void get_loader返回Null_不回填() {
        String key = "user:4";
        when(localCache.getIfPresent(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(null);

        String result = cacheService.get(key, String.class, () -> null);

        assertNull(result);
        verify(localCache, never()).put(anyString(), any());
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("正常场景：默认 TTL 重载方法使用 30 分钟")
    void get_默认TTL_30分钟() {
        String key = "user:5";
        String dbValue = "db";
        when(localCache.getIfPresent(key)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(null);

        cacheService.get(key, String.class, () -> dbValue);

        verify(valueOperations).set(eq(REDIS_PREFIX + key), eq(dbValue), eq(Duration.ofMinutes(30)));
    }

    // ==================== put ====================

    @Test
    @DisplayName("正常场景：put 写入 L1+L2 并设置 TTL")
    void put_写入两级缓存() {
        String key = "dict:1";
        String value = "v1";
        Duration ttl = Duration.ofSeconds(60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.put(key, value, ttl);

        verify(localCache).put(key, value);
        verify(valueOperations).set(REDIS_PREFIX + key, value, ttl);
    }

    @Test
    @DisplayName("正常场景：put 默认 TTL 30 分钟")
    void put_默认TTL() {
        String key = "dict:2";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.put(key, "v2");

        verify(valueOperations).set(eq(REDIS_PREFIX + key), eq("v2"), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("边界场景：put null 值不写入任何缓存")
    void put_null值_跳过() {
        cacheService.put("key", null);

        verify(localCache, never()).put(anyString(), any());
        verify(redisTemplate, never()).opsForValue();
    }

    // ==================== evict ====================

    @Test
    @DisplayName("正常场景：evict 失效 L1+L2")
    void evict_失效两级缓存() {
        String key = "user:10";

        cacheService.evict(key);

        verify(localCache).invalidate(key);
        verify(redisTemplate).delete(REDIS_PREFIX + key);
    }

    // ==================== evictAll ====================

    @Test
    @DisplayName("正常场景：evictAll 批量失效多个 key")
    void evictAll_批量失效() {
        String k1 = "a";
        String k2 = "b";
        String k3 = "c";

        cacheService.evictAll(k1, k2, k3);

        verify(localCache).invalidate(k1);
        verify(localCache).invalidate(k2);
        verify(localCache).invalidate(k3);
        verify(redisTemplate).delete(REDIS_PREFIX + k1);
        verify(redisTemplate).delete(REDIS_PREFIX + k2);
        verify(redisTemplate).delete(REDIS_PREFIX + k3);
    }

    @Test
    @DisplayName("边界场景：evictAll 无参数不调用任何失效")
    void evictAll_无参数() {
        cacheService.evictAll();

        verify(localCache, never()).invalidate(anyString());
        verify(redisTemplate, never()).delete(anyString());
    }

    // ==================== evictByPattern ====================

    @Test
    @DisplayName("正常场景：evictByPattern 匹配到 key 删除 L2，L1 全量清理")
    void evictByPattern_匹配到Key() {
        String pattern = "dict:*";
        Set<String> keys = Set.of(REDIS_PREFIX + "dict:1", REDIS_PREFIX + "dict:2");
        when(redisTemplate.keys(REDIS_PREFIX + pattern)).thenReturn(keys);

        cacheService.evictByPattern(pattern);

        verify(localCache).invalidateAll();
        verify(redisTemplate).delete(keys);
    }

    @Test
    @DisplayName("边界场景：evictByPattern 无匹配 key 仅清理 L1")
    void evictByPattern_无匹配Key() {
        String pattern = "empty:*";
        when(redisTemplate.keys(REDIS_PREFIX + pattern)).thenReturn(Set.of());

        cacheService.evictByPattern(pattern);

        verify(localCache).invalidateAll();
        verify(redisTemplate, never()).delete(any(Set.class));
    }

    @Test
    @DisplayName("边界场景：evictByPattern keys 返回 null 仅清理 L1")
    void evictByPattern_keys返回Null() {
        String pattern = "null:*";
        when(redisTemplate.keys(REDIS_PREFIX + pattern)).thenReturn(null);

        cacheService.evictByPattern(pattern);

        verify(localCache).invalidateAll();
        verify(redisTemplate, never()).delete(any(Set.class));
    }

    // ==================== clearAll ====================

    @Test
    @DisplayName("正常场景：clearAll 清空 L1 并删除所有 L2 key")
    void clearAll_清空全部() {
        Set<String> keys = Set.of(REDIS_PREFIX + "a", REDIS_PREFIX + "b");
        when(redisTemplate.keys(REDIS_PREFIX + "*")).thenReturn(keys);

        cacheService.clearAll();

        verify(localCache).invalidateAll();
        verify(redisTemplate).delete(keys);
    }

    @Test
    @DisplayName("边界场景：clearAll 无 key 时仅清空 L1")
    void clearAll_无Key() {
        when(redisTemplate.keys(REDIS_PREFIX + "*")).thenReturn(Set.of());

        cacheService.clearAll();

        verify(localCache).invalidateAll();
        verify(redisTemplate, never()).delete(any(Set.class));
    }

    @Test
    @DisplayName("边界场景：clearAll keys 返回 null 仅清空 L1")
    void clearAll_keys返回Null() {
        when(redisTemplate.keys(REDIS_PREFIX + "*")).thenReturn(null);

        cacheService.clearAll();

        verify(localCache).invalidateAll();
        verify(redisTemplate, never()).delete(any(Set.class));
    }
}
