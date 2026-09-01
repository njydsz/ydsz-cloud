package com.njydsz.common.cache.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;

/**
 * Spring Cache 注解路径空值短 TTL 测试（防穿透，对标 Spring Cache null TTL 配置惯例）。
 *
 * <p>锁定的不变量：
 *
 * <ul>
 *   <li>空值占位期内二次 get 不回源（loader 不重复执行）
 *   <li>占位过期后自动恢复回源
 *   <li>占位期内写入真实值，get 优先返回真实值（占位不屏蔽）
 *   <li>YdszCacheManager 装配：per-cache 空值 TTL 覆盖全局配置
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class SpringYdszCacheNullTtlTest {

  @Test
  @DisplayName("空值占位期内不回源，过期后恢复加载")
  void nullPlaceholderShouldBlockReloadAndRecoverAfterExpiry() {
    Cache<Object, Object> delegate = YdszCache.newBuilder().build();
    SpringYdszCache cache = new SpringYdszCache("nullTtl", delegate, true);
    cache.setNullValueTtl(50, 100);

    AtomicInteger loadCount = new AtomicInteger();
    Callable<Object> nullLoader =
        () -> {
          loadCount.incrementAndGet();
          return null;
        };
    assertThat(cache.get("k", nullLoader)).isNull();
    // 占位期内：lookup 命中空值占位，不触发 loader
    assertThat(cache.get("k", nullLoader)).isNull();
    assertThat(loadCount.get()).isOne();

    // 越过 max TTL 后占位过期，恢复回源
    sleepQuietly(250);
    Callable<Object> valueLoader =
        () -> {
          loadCount.incrementAndGet();
          return "recovered";
        };
    assertThat(cache.get("k", valueLoader)).isEqualTo("recovered");
    assertThat(loadCount.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("占位期内写入真实值，get 优先返回真实值")
  void realValuePutShouldNotBeMaskedByActivePlaceholder() {
    Cache<Object, Object> delegate = YdszCache.newBuilder().build();
    SpringYdszCache cache = new SpringYdszCache("nullTtlPut", delegate, true);
    cache.setNullValueTtl(60_000, 60_000);

    AtomicInteger loadCount = new AtomicInteger();
    Callable<Object> nullLoader =
        () -> {
          loadCount.incrementAndGet();
          return null;
        };
    assertThat(cache.get("k", nullLoader)).isNull();

    // 占位仍活动期内写入真实值
    cache.put("k", "real");
    Callable<Object> unusedLoader =
        () -> {
          loadCount.incrementAndGet();
          return "should-not-load";
        };
    assertThat(cache.get("k", unusedLoader)).isEqualTo("real");
    assertThat(loadCount.get()).isOne();
  }

  @Test
  @DisplayName("YdszCacheManager 装配：per-cache 空值 TTL 覆盖全局配置")
  void managerShouldApplyPerCacheNullTtlOverGlobal() {
    YdszCacheManager manager = new YdszCacheManager();
    // 全局长 TTL（60s），占位期内将持续屏蔽回源
    manager.setNullValueTtl(60_000, 60_000);
    YdszCacheProperties.CacheConfig shortTtl = new YdszCacheProperties.CacheConfig();
    shortTtl.setNullValueTtlMin(50L);
    shortTtl.setNullValueTtlMax(80L);
    Map<String, YdszCacheProperties.CacheConfig> per = new HashMap<>();
    per.put("shortTtlCache", shortTtl);
    manager.setPerCacheConfigs(per);

    // per-cache：短 TTL 生效，过期后恢复回源
    SpringYdszCache shortCache = manager.getCache("shortTtlCache");
    AtomicInteger shortLoadCount = new AtomicInteger();
    Callable<Object> shortNullLoader =
        () -> {
          shortLoadCount.incrementAndGet();
          return null;
        };
    Callable<Object> shortValueLoader =
        () -> {
          shortLoadCount.incrementAndGet();
          return "v";
        };
    shortCache.get("k", shortNullLoader);
    sleepQuietly(250);
    shortCache.get("k", shortValueLoader);
    assertThat(shortLoadCount.get()).isEqualTo(2);

    // 未覆盖的缓存走全局长 TTL：占位持续屏蔽回源
    SpringYdszCache globalCache = manager.getCache("globalCache");
    AtomicInteger globalLoadCount = new AtomicInteger();
    Callable<Object> globalNullLoader =
        () -> {
          globalLoadCount.incrementAndGet();
          return null;
        };
    Callable<Object> globalValueLoader =
        () -> {
          globalLoadCount.incrementAndGet();
          return "v";
        };
    globalCache.get("k", globalNullLoader);
    sleepQuietly(250);
    globalCache.get("k", globalValueLoader);
    assertThat(globalLoadCount.get()).isOne();
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
