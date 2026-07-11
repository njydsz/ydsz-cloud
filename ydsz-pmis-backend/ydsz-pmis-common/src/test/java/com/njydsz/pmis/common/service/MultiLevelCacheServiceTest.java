package com.njydsz.pmis.common.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MultiLevelCacheService} 多级缓存服务测试
 *
 * <p>覆盖 L1(Caffeine)/L2(Redis) 两级缓存的读取、写入、失效流程，
 * 包括 L1 命中、L2 命中回填 L1、DB loader 回填 L1+L2 等核心场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MultiLevelCacheService 多级缓存服务测试")
@ExtendWith(MockitoExtension.class)
class MultiLevelCacheServiceTest {

    @Mock
    private Cache<String, Object> localCache;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private MultiLevelCacheService cacheService;

    private static final String REDIS_PREFIX = "pmis:mc:";

    @BeforeEach
    void setUp() {
        cacheService = new MultiLevelCacheService(localCache, redisTemplate);
    }

    @Nested
    @DisplayName("get() 读取缓存")
    class GetTest {

        @Test
        @DisplayName("L1 命中时直接返回，不查 L2")
        void shouldReturnFromL1WhenHit() {
            String key = "user:1";
            String cachedValue = "value-from-l1";
            when(localCache.getIfPresent(key)).thenReturn(cachedValue);

            String result = cacheService.get(key, String.class,
                    () -> "from-db", Duration.ofMinutes(30));

            assertThat(result).isEqualTo(cachedValue);
            verify(localCache).getIfPresent(key);
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("L1 未命中、L2 命中时回填 L1 并返回")
        void shouldBackfillL1WhenL2Hit() {
            String key = "user:2";
            String l2Value = "value-from-l2";
            when(localCache.getIfPresent(key)).thenReturn(null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(l2Value);

            String result = cacheService.get(key, String.class,
                    () -> "from-db", Duration.ofMinutes(30));

            assertThat(result).isEqualTo(l2Value);
            verify(localCache).put(key, l2Value);
        }

        @Test
        @DisplayName("L1/L2 均未命中时调用 loader 并回填 L1+L2")
        void shouldCallLoaderAndBackfillBothWhenAllMiss() {
            String key = "user:3";
            String dbValue = "value-from-db";
            when(localCache.getIfPresent(key)).thenReturn(null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(null);

            String result = cacheService.get(key, String.class,
                    () -> dbValue, Duration.ofMinutes(30));

            assertThat(result).isEqualTo(dbValue);
            verify(localCache).put(key, dbValue);
            verify(valueOperations).set(eq(REDIS_PREFIX + key), eq(dbValue), eq(Duration.ofMinutes(30)));
        }

        @Test
        @DisplayName("loader 返回 null 时不回填缓存")
        void shouldNotBackfillWhenLoaderReturnsNull() {
            String key = "user:4";
            when(localCache.getIfPresent(key)).thenReturn(null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(null);

            String result = cacheService.get(key, String.class,
                    () -> null, Duration.ofMinutes(30));

            assertThat(result).isNull();
            verify(localCache, never()).put(eq(key), any());
            verify(valueOperations, never()).set(any(), any(), any(Duration.class));
        }

        @Test
        @DisplayName("无参 TTL 重载方法使用默认 30 分钟")
        void shouldUseDefaultTtlWhenNotSpecified() {
            String key = "user:5";
            String dbValue = "value";
            when(localCache.getIfPresent(key)).thenReturn(null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(null);

            cacheService.get(key, String.class, () -> dbValue);

            verify(valueOperations).set(eq(REDIS_PREFIX + key), eq(dbValue), eq(Duration.ofMinutes(30)));
        }

        @Test
        @DisplayName("L2 命中返回非 String 类型时正确泛型转换")
        void shouldHandleNonStringTypeFromL2() {
            String key = "counter:1";
            Integer l2Value = 42;
            when(localCache.getIfPresent(key)).thenReturn(null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_PREFIX + key)).thenReturn(l2Value);

            Integer result = cacheService.get(key, Integer.class,
                    () -> 0, Duration.ofMinutes(30));

            assertThat(result).isEqualTo(42);
            verify(localCache).put(key, l2Value);
        }
    }

    @Nested
    @DisplayName("put() 写入缓存")
    class PutTest {

        @Test
        @DisplayName("写入 L1 + L2，使用指定 TTL")
        void shouldWriteToBothLevels() {
            String key = "config:1";
            String value = "v1";
            Duration ttl = Duration.ofMinutes(60);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            cacheService.put(key, value, ttl);

            verify(localCache).put(key, value);
            verify(valueOperations).set(REDIS_PREFIX + key, value, ttl);
        }

        @Test
        @DisplayName("无参 TTL 重载方法使用默认 30 分钟")
        void shouldUseDefaultTtlForPut() {
            String key = "config:2";
            String value = "v2";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            cacheService.put(key, value);

            verify(localCache).put(key, value);
            verify(valueOperations).set(REDIS_PREFIX + key, value, Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("value 为 null 时不写入任何缓存")
        void shouldNotWriteWhenValueIsNull() {
            String key = "config:3";

            cacheService.put(key, null, Duration.ofMinutes(30));

            verify(localCache, never()).put(eq(key), any());
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("value 为 null 时使用无参 put 也不写入")
        void shouldNotWriteWhenValueIsNullAndDefaultTtl() {
            String key = "config:4";

            cacheService.put(key, null);

            verify(localCache, never()).put(eq(key), any());
            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("evict() 失效缓存")
    class EvictTest {

        @Test
        @DisplayName("evict 单个 key 失效 L1 + L2")
        void shouldInvalidateBothLevels() {
            String key = "user:1";

            cacheService.evict(key);

            verify(localCache).invalidate(key);
            verify(redisTemplate).delete(REDIS_PREFIX + key);
        }

        @Test
        @DisplayName("evict 不同 key 各自失效")
        void shouldEvictDifferentKeys() {
            cacheService.evict("k1");
            cacheService.evict("k2");

            verify(localCache).invalidate("k1");
            verify(localCache).invalidate("k2");
            verify(redisTemplate).delete(REDIS_PREFIX + "k1");
            verify(redisTemplate).delete(REDIS_PREFIX + "k2");
        }
    }

    @Nested
    @DisplayName("evictAll() 批量失效")
    class EvictAllTest {

        @Test
        @DisplayName("批量失效多个 key")
        void shouldEvictAllKeys() {
            cacheService.evictAll("a", "b");

            verify(localCache).invalidate("a");
            verify(localCache).invalidate("b");
            verify(redisTemplate).delete(REDIS_PREFIX + "a");
            verify(redisTemplate).delete(REDIS_PREFIX + "b");
        }

        @Test
        @DisplayName("批量失效单个 key")
        void shouldEvictSingleKeyViaEvictAll() {
            cacheService.evictAll("only");

            verify(localCache).invalidate("only");
            verify(redisTemplate).delete(REDIS_PREFIX + "only");
        }
    }

    @Nested
    @DisplayName("evictByPattern() 按模式失效")
    class EvictByPatternTest {

        @Test
        @DisplayName("L1 全量清理 + L2 按 pattern 删除")
        void shouldInvalidateL1AllAndL2ByPattern() {
            String pattern = "dict:*";
            Set<String> matchedKeys = Set.of(
                    REDIS_PREFIX + "dict:1",
                    REDIS_PREFIX + "dict:2"
            );
            when(redisTemplate.keys(REDIS_PREFIX + pattern)).thenReturn(matchedKeys);

            cacheService.evictByPattern(pattern);

            verify(localCache).invalidateAll();
            verify(redisTemplate).delete(matchedKeys);
        }

        @Test
        @DisplayName("L2 返回 null keys 时仅清理 L1")
        void shouldOnlyInvalidateL1WhenKeysNull() {
            String pattern = "empty:*";
            when(redisTemplate.keys(REDIS_PREFIX + pattern)).thenReturn(null);

            cacheService.evictByPattern(pattern);

            verify(localCache).invalidateAll();
            verify(redisTemplate, never()).delete(any(Set.class));
        }

        @Test
        @DisplayName("L2 返回空 keys 集合时仅清理 L1")
        void shouldOnlyInvalidateL1WhenKeysEmpty() {
            String pattern = "empty:*";
            when(redisTemplate.keys(REDIS_PREFIX + pattern)).thenReturn(Set.of());

            cacheService.evictByPattern(pattern);

            verify(localCache).invalidateAll();
            verify(redisTemplate, never()).delete(any(Set.class));
        }
    }

    @Nested
    @DisplayName("clearAll() 清空所有缓存")
    class ClearAllTest {

        @Test
        @DisplayName("清空 L1 全部 + L2 全部")
        void shouldClearAllBothLevels() {
            Set<String> allKeys = Set.of(REDIS_PREFIX + "k1", REDIS_PREFIX + "k2");
            when(redisTemplate.keys(REDIS_PREFIX + "*")).thenReturn(allKeys);

            cacheService.clearAll();

            verify(localCache).invalidateAll();
            verify(redisTemplate).delete(allKeys);
        }

        @Test
        @DisplayName("L2 返回 null keys 时仅清空 L1")
        void shouldOnlyClearL1WhenKeysNull() {
            when(redisTemplate.keys(REDIS_PREFIX + "*")).thenReturn(null);

            cacheService.clearAll();

            verify(localCache).invalidateAll();
            verify(redisTemplate, never()).delete(any(Set.class));
        }

        @Test
        @DisplayName("L2 返回空 keys 时仅清空 L1")
        void shouldOnlyClearL1WhenKeysEmpty() {
            when(redisTemplate.keys(REDIS_PREFIX + "*")).thenReturn(Set.of());

            cacheService.clearAll();

            verify(localCache).invalidateAll();
            verify(redisTemplate, never()).delete(any(Set.class));
        }
    }
}
