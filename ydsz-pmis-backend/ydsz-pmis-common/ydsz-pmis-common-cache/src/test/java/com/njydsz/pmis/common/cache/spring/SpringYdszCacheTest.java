package com.njydsz.pmis.common.cache.spring;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.internal.concurrent.StripedConcurrentCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache.ValueWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SpringYdszCache 单元测试")
class SpringYdszCacheTest {

    @Nested
    @DisplayName("基础操作")
    class BasicOperations {

        @Test
        @DisplayName("getName返回缓存名称")
        void getName() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            assertThat(springCache.getName()).isEqualTo("myCache");
        }

        @Test
        @DisplayName("getNativeCache返回底层缓存")
        void getNativeCache() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            assertThat(springCache.getNativeCache()).isSameAs(delegate);
        }

        @Test
        @DisplayName("lookup返回值")
        void lookup() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "value1");
            assertThat(springCache.lookup("key1")).isEqualTo("value1");
        }

        @Test
        @DisplayName("get返回ValueWrapper包含缓存值")
        void get() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "value1");
            ValueWrapper wrapper = springCache.get("key1");
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.get()).isEqualTo("value1");
        }

        @Test
        @DisplayName("get类型检查失败")
        void getTypeMismatch() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "stringValue");
            assertThatThrownBy(() -> springCache.get("key1", Integer.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not of required type");
        }

        @Test
        @DisplayName("get type为null时走父类逻辑")
        void getNullType() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "value1");
            assertThat(springCache.get("key1", (Class<Object>) null)).isEqualTo("value1");
        }

        @Test
        @DisplayName("get with Callable加载器")
        void getWithCallable() throws Exception {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            String result = springCache.get("key1", () -> "loaded");
            assertThat(result).isEqualTo("loaded");
        }
    }

    @Nested
    @DisplayName("put操作")
    class PutOperations {

        @Test
        @DisplayName("put存入缓存")
        void put() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            springCache.put("key1", "value1");
            assertThat(delegate.getIfPresent("key1")).isEqualTo("value1");
        }

        @Test
        @DisplayName("putIfAbsent不覆盖已有值")
        void putIfAbsent() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "existing");
            ValueWrapper wrapper = springCache.putIfAbsent("key1", "new");
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.get()).isEqualTo("existing");
        }

        @Test
        @DisplayName("putIfAbsent放入新值")
        void putIfAbsentNewValue() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            Object result = springCache.putIfAbsent("key1", "value1");
            assertThat(result).isNull();
            assertThat(delegate.getIfPresent("key1")).isEqualTo("value1");
        }
    }

    @Nested
    @DisplayName("null值处理")
    class NullValueHandling {

        @Test
        @DisplayName("allowNullValues=true时put null")
        void putNullAllowed() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            springCache.put("key1", null);
        }

        @Test
        @DisplayName("allowNullValues=false时put null被忽略")
        void putNullNotAllowed() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, false);
            springCache.put("key1", null);
            assertThat(delegate.getIfPresent("key1")).isNull();
        }

        @Test
        @DisplayName("allowNullValues=false时putIfAbsent null返回null")
        void putIfAbsentNullNotAllowed() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, false);
            assertThat(springCache.putIfAbsent("key1", null)).isNull();
        }
    }

    @Nested
    @DisplayName("删除操作")
    class Eviction {

        @Test
        @DisplayName("evict删除缓存")
        void evict() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "value1");
            springCache.evict("key1");
            assertThat(delegate.getIfPresent("key1")).isNull();
        }

        @Test
        @DisplayName("evictIfPresent返回true当key存在")
        void evictIfPresentExisting() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "value1");
            assertThat(springCache.evictIfPresent("key1")).isTrue();
            assertThat(delegate.getIfPresent("key1")).isNull();
        }

        @Test
        @DisplayName("evictIfPresent返回false当key不存在")
        void evictIfPresentNonExisting() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            assertThat(springCache.evictIfPresent("missing")).isFalse();
        }

        @Test
        @DisplayName("clear清空缓存")
        void clear() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "value1");
            delegate.put("key2", "value2");
            springCache.clear();
            assertThat(delegate.estimatedSize()).isZero();
        }

        @Test
        @DisplayName("invalidate返回true")
        void invalidate() {
            Cache<Object, Object> delegate = new StripedConcurrentCache<>(100);
            var springCache = new SpringYdszCache("myCache", delegate, true);
            delegate.put("key1", "value1");
            assertThat(springCache.invalidate()).isTrue();
            assertThat(delegate.estimatedSize()).isZero();
        }
    }
}
