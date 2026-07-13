package com.njydsz.pmis.common.cache.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.api.LoadingCache;
import com.njydsz.pmis.common.cache.support.CacheLoader;
import com.njydsz.pmis.common.cache.support.CacheWriter;

class CacheBuilderTest {

  @Nested
  @DisplayName("构建缓存")
  class BuildCache {

    @Test
    @DisplayName("默认构建 TINYLFU 类型")
    void defaultType() {
      Cache<String, Integer> cache =
          YdszCache.<String, Integer>newBuilder()
              .maximumSize(100)
              .build();
      assertThat(cache).isNotNull();
      assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    @DisplayName("构建 LRU 缓存")
    void lruCache() {
      Cache<String, Integer> cache =
          YdszCache.<String, Integer>newBuilder()
              .type(CacheType.LRU)
              .maximumSize(100)
              .build();
      assertThat(cache).isNotNull();
    }

    @Test
    @DisplayName("构建 STRIPED 缓存")
    void stripedCache() {
      Cache<String, Integer> cache =
          YdszCache.<String, Integer>newBuilder()
              .type(CacheType.STRIPED)
              .maximumSize(100)
              .build();
      assertThat(cache).isNotNull();
    }

    @Test
    @DisplayName("maximumSize=0 抛出异常")
    void maximumSizeZero() {
      assertThatThrownBy(
              () -> YdszCache.newBuilder().maximumSize(0).build())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("WEIGHTED 类型缺少 weigher 抛出异常")
    void weightedCacheWithoutWeigher() {
      assertThatThrownBy(
              () ->
                  YdszCache.<String, Integer>newBuilder()
                      .type(CacheType.WEIGHTED)
                      .maximumWeight(1000, null)
                      .build())
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("构建加载缓存")
  class BuildLoadingCache {

    @Test
    @DisplayName("缺少 loader 抛出异常")
    void missingLoader() {
      assertThatThrownBy(
              () ->
                  YdszCache.<String, Integer>newBuilder()
                      .type(CacheType.ENHANCED_LOADING)
                      .maximumSize(100)
                      .buildLoadingCache())
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("构建 LoadingCache 正常工作")
    void loadingCacheWorks() {
      LoadingCache<String, Integer> cache =
          YdszCache.<String, Integer>newBuilder()
              .type(CacheType.ENHANCED_LOADING)
              .maximumSize(100)
              .loader(CacheLoader.from(key -> key.length()))
              .buildLoadingCache();
      assertThat(cache.get("hello")).isEqualTo(5);
    }

    @Test
    @DisplayName("loaderFrom 方法正常工作")
    void loaderFromWorks() {
      LoadingCache<String, Integer> cache =
          YdszCache.<String, Integer>newBuilder()
              .type(CacheType.ENHANCED_LOADING)
              .maximumSize(100)
              .loaderFrom(key -> key.length())
              .buildLoadingCache();
      assertThat(cache.get("hello")).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("配置组合")
  class ConfigurationCombination {

    @Test
    @DisplayName("TTL 缓存支持过期配置")
    void ttlCacheWithExpiry() {
      Cache<String, Integer> cache =
          YdszCache.<String, Integer>newBuilder()
              .type(CacheType.TTL)
              .expireAfterWrite(1, TimeUnit.SECONDS)
              .maximumSize(100)
              .build();
      assertThat(cache).isNotNull();
    }

    @Test
    @DisplayName("写穿透缓存正常工作")
    void writeThroughCache() {
      StringBuilder log = new StringBuilder();
      CacheWriter<String, Integer> writer =
          new CacheWriter<>() {
            @Override
            public void write(String key, Integer value) {
              log.append("write:").append(key);
            }

            @Override
            public void delete(String key, Integer value) {
              log.append("delete:").append(key);
            }
          };
      Cache<String, Integer> cache =
          YdszCache.<String, Integer>newBuilder()
              .type(CacheType.STRIPED)
              .maximumSize(100)
              .writer(writer)
              .build();
      cache.put("a", 1);
      assertThat(log.toString()).contains("write:a");
    }
  }
}
