package com.njydsz.common.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.internal.decorator.ExpirableCache;
import com.njydsz.common.cache.internal.tinylfu.WindowTinyLFUCache;

/**
 * ExpirableCache 过期装饰器测试。
 *
 * <p>覆盖：TTL 过期自动移除、过期后重新写入。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class ExpirableCacheTest {

  @Nested
  @DisplayName("写入后过期（expireAfterWrite）")
  class ExpireAfterWriteTest {

    @Test
    @DisplayName("TTL 内能获取到值")
    void beforeExpiry_shouldReturnValue() {
      Cache<String, String> base = new WindowTinyLFUCache<>(50);
      Cache<String, String> expiringCache =
          new ExpirableCache<>(base, TimeUnit.SECONDS.toNanos(1), 0, null, 1);

      expiringCache.put("key", "value");
      assertThat(expiringCache.getIfPresent("key")).isEqualTo("value");

      expiringCache.clear();
    }

    @Test
    @DisplayName("TTL 过期后返回 null")
    void afterExpiry_shouldReturnNull() throws Exception {
      Cache<String, String> base = new WindowTinyLFUCache<>(50);
      Cache<String, String> expiringCache =
          new ExpirableCache<>(base, TimeUnit.MILLISECONDS.toNanos(200), 0, null, 1);

      expiringCache.put("key", "value");
      Thread.sleep(300);
      expiringCache.cleanUp();

      assertThat(expiringCache.getIfPresent("key")).isNull();

      expiringCache.clear();
    }

    @Test
    @DisplayName("过期后重新写入可正常使用")
    void rewriteAfterExpiry_shouldWork() throws Exception {
      Cache<String, String> base = new WindowTinyLFUCache<>(50);
      Cache<String, String> expiringCache =
          new ExpirableCache<>(base, TimeUnit.MILLISECONDS.toNanos(100), 0, null, 1);

      expiringCache.put("key", "value1");
      Thread.sleep(150);
      expiringCache.cleanUp();

      // 重新写入
      expiringCache.put("key", "value2");
      assertThat(expiringCache.getIfPresent("key")).isEqualTo("value2");

      expiringCache.clear();
    }
  }

  @Nested
  @DisplayName("访问后过期（expireAfterAccess）")
  class ExpireAfterAccessTest {

    @Test
    @DisplayName("频繁访问可延长存活期")
    void frequentAccess_shouldExtendLife() throws Exception {
      Cache<String, String> base = new WindowTinyLFUCache<>(50);
      Cache<String, String> expiringCache =
          new ExpirableCache<>(base, 0, TimeUnit.MILLISECONDS.toNanos(300), null, 1);

      expiringCache.put("key", "value");

      // 每隔 150ms 访问一次，应在 300ms TTL 内续期
      for (int i = 0; i < 4; i++) {
        Thread.sleep(150);
        expiringCache.getIfPresent("key");
        expiringCache.cleanUp();
      }

      // 如果过期策略正确，频繁访问应使条目存活
      assertThat(expiringCache.getIfPresent("key")).isEqualTo("value");

      expiringCache.clear();
    }

    @Test
    @DisplayName("长时间不访问后过期")
    void longIdle_shouldExpire() throws Exception {
      Cache<String, String> base = new WindowTinyLFUCache<>(50);
      Cache<String, String> expiringCache =
          new ExpirableCache<>(base, 0, TimeUnit.MILLISECONDS.toNanos(200), null, 1);

      expiringCache.put("key", "value");
      Thread.sleep(300);
      expiringCache.cleanUp();

      assertThat(expiringCache.getIfPresent("key")).isNull();

      expiringCache.clear();
    }
  }
}
