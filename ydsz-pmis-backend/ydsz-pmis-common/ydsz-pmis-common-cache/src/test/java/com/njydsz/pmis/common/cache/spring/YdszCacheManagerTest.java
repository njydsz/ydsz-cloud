package com.njydsz.pmis.common.cache.spring;

import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.common.cache.internal.concurrent.StripedConcurrentCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("YdszCacheManager 单元测试")
class YdszCacheManagerTest {

    @Nested
    @DisplayName("基础配置")
    class BasicConfig {

        @Test
        @DisplayName("getCache创建缓存")
        void getCache() {
            var manager = new YdszCacheManager();
            var cache = manager.getCache("myCache");
            assertThat(cache).isNotNull();
            assertThat(cache.getName()).isEqualTo("myCache");
        }

        @Test
        @DisplayName("重复getCache返回同一实例")
        void getCacheSameInstance() {
            var manager = new YdszCacheManager();
            var cache1 = manager.getCache("myCache");
            var cache2 = manager.getCache("myCache");
            assertThat(cache1).isSameAs(cache2);
        }

        @Test
        @DisplayName("getCacheNames返回所有已创建缓存")
        void getCacheNames() {
            var manager = new YdszCacheManager();
            manager.getCache("cache1");
            manager.getCache("cache2");
            Collection<String> names = manager.getCacheNames();
            assertThat(names).containsExactlyInAnyOrder("cache1", "cache2");
        }
    }

    @Nested
    @DisplayName("配置选项")
    class Configuration {

        @Test
        @DisplayName("设置缓存类型")
        void setCacheType() {
            var manager = new YdszCacheManager();
            manager.setCacheType(CacheType.STRIPED);
            manager.setMaximumSize(100);
            var cache = manager.getCache("myCache");
            assertThat(cache).isNotNull();
        }

        @Test
        @DisplayName("设置最大容量")
        void setMaximumSize() {
            var manager = new YdszCacheManager();
            manager.setMaximumSize(500);
            var cache = manager.getCache("myCache");
            assertThat(cache).isNotNull();
        }

        @Test
        @DisplayName("设置过期时间")
        void setExpireAfterWrite() {
            var manager = new YdszCacheManager();
            manager.setExpireAfterWrite(30, TimeUnit.MINUTES);
            manager.setMaximumSize(100);
            var cache = manager.getCache("myCache");
            assertThat(cache).isNotNull();
        }

        @Test
        @DisplayName("设置初始容量")
        void setInitialCapacity() {
            var manager = new YdszCacheManager();
            manager.setInitialCapacity(256);
            manager.setMaximumSize(100);
            var cache = manager.getCache("myCache");
            assertThat(cache).isNotNull();
        }

        @Test
        @DisplayName("禁止null值")
        void setAllowNullValues() {
            var manager = new YdszCacheManager();
            manager.setAllowNullValues(false);
            manager.setMaximumSize(100);
            var cache = manager.getCache("myCache");
            assertThat(cache).isNotNull();
            cache.put("key1", null);
            assertThat(cache.lookup("key1")).isNull();
        }
    }

    @Nested
    @DisplayName("预定义缓存名称")
    class PredefinedCache {

        @Test
        @DisplayName("预定义名称的缓存可创建")
        void predefinedCacheAllowed() {
            var manager = new YdszCacheManager();
            manager.setCacheNames(List.of("users", "products"));
            manager.setMaximumSize(100);

            assertThat(manager.getCache("users")).isNotNull();
            assertThat(manager.getCache("products")).isNotNull();
        }

        @Test
        @DisplayName("非预定义名称的缓存返回null")
        void nonPredefinedCacheReturnsNull() {
            var manager = new YdszCacheManager();
            manager.setCacheNames(List.of("users"));
            manager.setMaximumSize(100);

            assertThat(manager.getCache("unknown")).isNull();
        }
    }

    @Nested
    @DisplayName("自定义缓存构建器")
    class CustomBuilder {

        @Test
        @DisplayName("自定义构建器创建缓存")
        void customBuilder() {
            var manager = new YdszCacheManager();
            manager.setCacheBuilder(name -> new StripedConcurrentCache<>(100));

            var cache = manager.getCache("myCache");
            assertThat(cache).isNotNull();
            assertThat(cache.getName()).isEqualTo("myCache");
        }
    }
}
