package com.njydsz.common.cache.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;

/**
 * CacheBuilder 参数校验与基础行为测试。
 *
 * <p>锁定的不变量：非法参数组合在构建期 fail-fast（maximumSize=0、write/access 过期互斥）、
 * STRIPED 类型容量上限生效、带 loader 的 LoadingCache 构建。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class CacheBuilderTest {

  @Test
  @DisplayName("maximumSize=0 拒绝构建")
  void shouldRejectZeroMaximumSize() {
    assertThrows(
        IllegalArgumentException.class, () -> YdszCache.<String, String>newBuilder().maximumSize(0).build());
  }

  @Test
  @DisplayName("expireAfterWrite 与 expireAfterAccess 互斥")
  void shouldRejectConflictingExpirationPolicies() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            YdszCache.<String, String>newBuilder()
                .maximumSize(10)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .build());
  }

  @Test
  @DisplayName("STRIPED 类型容量上限生效（全局总容量，非段容量）")
  void stripedCacheShouldEnforceGlobalCapacity() {
    Cache<String, String> cache =
        YdszCache.<String, String>newBuilder().type(CacheType.STRIPED).maximumSize(64).build();
    for (int i = 0; i < 500; i++) {
      cache.put("key-" + i, "value-" + i);
    }
    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(64);
    assertThat(cache.getIfPresent("key-499")).isEqualTo("value-499");
  }

  @Test
  @DisplayName("未设置 loader 时 buildLoadingCache 拒绝构建")
  void shouldRejectLoadingCacheWithoutLoader() {
    assertThrows(
        IllegalStateException.class, () -> YdszCache.<String, String>newBuilder().buildLoadingCache());
  }

  @Test
  @DisplayName("带 loader 的 LoadingCache 未命中自动加载")
  void loadingCacheShouldLoadOnMiss() {
    com.njydsz.common.cache.api.LoadingCache<String, String> loading =
        YdszCache.<String, String>newBuilder()
            .maximumSize(100)
            .loaderFrom(k -> "loaded-" + k)
            .buildLoadingCache();

    assertThat(loading.get("a")).isEqualTo("loaded-a");
    assertThat(loading.get("a")).isEqualTo("loaded-a");
  }
}
