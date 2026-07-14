package com.njydsz.pmis.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheBuilder;
import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.common.cache.internal.decorator.ExpirableCache;
import com.njydsz.pmis.common.cache.internal.reference.WeakKeyCache;
import com.njydsz.pmis.common.cache.support.CacheLoader;
import com.njydsz.pmis.common.cache.support.CacheWriter;

/**
 * CacheBuilder 单元测试
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>基础缓存构建（LRU/TINYLFU/STRIPED）
 *   <li>过期策略装饰器叠加
 *   <li>SWR 模式构建
 *   <li>Write-Behind 模式构建
 *   <li>MemoryAware 模式构建
 *   <li>参数校验
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("CacheBuilder 单元测试")
class CacheBuilderTest {

  @Test
  @DisplayName("构建默认 TINYLFU 缓存")
  void shouldBuildDefaultTinyLFU() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .maximumSize(100)
            .build();

    cache.put("key1", "value1");
    assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
    assertThat(cache.estimatedSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("构建 LRU 缓存")
  void shouldBuildLRU() {
    Cache<String, Integer> cache =
        CacheBuilder.<String, Integer>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(3)
            .build();

    cache.put("a", 1);
    cache.put("b", 2);
    cache.put("c", 3);
    cache.put("d", 4);

    assertThat(cache.getIfPresent("a")).isNull();
    assertThat(cache.getIfPresent("d")).isEqualTo(4);
  }

  @Test
  @DisplayName("构建带过期策略的缓存")
  void shouldBuildExpirableCache() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
  }

  @Test
  @DisplayName("构建 SWR 缓存")
  void shouldBuildSwrCache() {
    AtomicInteger loadCount = new AtomicInteger(0);
    CacheLoader<String, String> loader =
        CacheLoader.from(key -> {
          loadCount.incrementAndGet();
          return "loaded-" + key;
        });

    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .maximumSize(100)
            .staleWhileRevalidate(60, 120, TimeUnit.SECONDS)
            .loader(loader)
            .build();

    assertThat(cache).isNotNull();
  }

  @Test
  @DisplayName("构建 Write-Behind 缓存")
  void shouldBuildWriteBehindCache() {
    AtomicInteger writeCount = new AtomicInteger(0);
    CacheWriter<String, String> writer =
        new CacheWriter<>() {
          @Override
          public void write(String key, String value) {
            writeCount.incrementAndGet();
          }

          @Override
          public void delete(String key, String value) {
            // no-op
          }
        };

    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .maximumSize(100)
            .writeBehind(100, 10, 1000)
            .writer(writer)
            .build();

    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
  }

  @Test
  @DisplayName("构建 MemoryAware 缓存")
  void shouldBuildMemoryAwareCache() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .maximumSize(1000)
            .memoryAware(0.8, 0.9, 0.95, 5)
            .build();

    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
  }

  @Test
  @DisplayName("构建组合装饰器缓存（过期 + MemoryAware + WriteBehind）")
  void shouldBuildCombinedDecorators() {
    CacheWriter<String, String> writer =
        new CacheWriter<>() {
          @Override
          public void write(String key, String value) {}

          @Override
          public void delete(String key, String value) {}
        };

    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(500)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .memoryAware()
            .writeBehind()
            .writer(writer)
            .build();

    cache.put("key1", "value1");
    assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
  }

  @Test
  @DisplayName("参数校验：maximumSize=0 应抛异常")
  void shouldRejectZeroMaximumSize() {
    assertThatThrownBy(
            () -> CacheBuilder.<String, String>newBuilder().maximumSize(0).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("参数校验：stripes<1 应抛异常")
  void shouldRejectInvalidStripes() {
    assertThatThrownBy(
            () -> CacheBuilder.<String, String>newBuilder().stripes(0).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("参数校验：同时设置 expireAfterWrite 和 expireAfterAccess 应抛异常")
  void shouldRejectSimultaneousWriteAndAccessExpiration() {
    assertThatThrownBy(
            () ->
                CacheBuilder.<String, String>newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .expireAfterAccess(1, TimeUnit.HOURS)
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("deprecated TTL 类型重定向到 CONCURRENT + ExpirableCache")
  void shouldRedirectTtlTypeToExpirableCache() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TTL)
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    // TTL 类型应被重定向为 ExpirableCache 装饰器
    assertThat(cache).isInstanceOf(ExpirableCache.class);
    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
  }

  @Test
  @DisplayName("deprecated TTL 类型未设置过期时间时默认 5 分钟")
  void shouldDefaultTtlExpirationTo5Minutes() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TTL)
            .maximumSize(100)
            .build();

    // 应使用默认 5 分钟过期时间
    assertThat(cache).isInstanceOf(ExpirableCache.class);
    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
  }

  @Test
  @DisplayName("weakKeys() flag API 创建弱引用键缓存")
  void shouldBuildWeakKeysCacheWithFlag() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .weakKeys()
            .maximumSize(100)
            .build();

    assertThat(cache).isInstanceOf(WeakKeyCache.class);
    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
  }

  @Test
  @DisplayName("构建 LoadingCache 并应用装饰器")
  void shouldBuildLoadingCacheWithDecorators() {
    AtomicInteger loadCount = new AtomicInteger(0);
    CacheLoader<String, String> loader =
        CacheLoader.from(key -> {
          loadCount.incrementAndGet();
          return "loaded-" + key;
        });

    var loadingCache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.STRIPED)
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .loader(loader)
            .buildLoadingCache();

    String result = loadingCache.get("key1");
    assertThat(result).isEqualTo("loaded-key1");
    assertThat(loadCount.get()).isEqualTo(1);
    // 第二次应命中缓存
    loadingCache.get("key1");
    assertThat(loadCount.get()).isEqualTo(1);
  }
}
