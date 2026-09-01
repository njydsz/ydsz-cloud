package com.njydsz.common.cache.internal.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;

/**
 * ExpirableCache 过期语义回归测试。
 *
 * <p>锁定的不变量：expireAfterWrite 到期后读返回 null 且条目被移除（读时惰性过期）、cleanUp 主动回收过期条目、
 * 未过期条目不受影响。TTL 抖动默认 ±10%，测试时长已预留足够裕量避免抖动导致的 flaky。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class ExpirableCacheTest {

  /** 测试 TTL（毫秒），需大于默认抖动 ±10% 的影响区间 */
  private static final long TTL_MS = 150;

  /** 等待过期的时间（含抖动裕量） */
  private static final long WAIT_MS = TTL_MS * 3;

  @Test
  @DisplayName("expireAfterWrite：到期后读取返回 null 且底层条目被移除")
  void expiredEntryShouldReturnNullAndBeRemoved() {
    Cache<String, String> cache =
        YdszCache.<String, String>newBuilder()
            .maximumSize(100)
            .expireAfterWrite(TTL_MS, TimeUnit.MILLISECONDS)
            .build();

    cache.put("a", "1");
    assertThat(cache.getIfPresent("a")).isEqualTo("1");
    assertThat(cache.containsKey("a")).isTrue();

    sleepQuietly(WAIT_MS);

    assertThat(cache.getIfPresent("a")).as("过期后应返回 null").isNull();
    assertThat(cache.containsKey("a")).as("过期键视为不存在").isFalse();
    // 读时惰性过期应同步移除底层条目（不含已过期条目的悬挂数据）
    assertThat(cache.estimatedSize()).as("过期条目移除后容量应归零").isZero();
  }

  @Test
  @DisplayName("cleanUp：主动清理已过期条目，无需等待读取触发")
  void cleanUpShouldPurgeExpiredEntriesProactively() {
    Cache<String, String> cache =
        YdszCache.<String, String>newBuilder()
            .maximumSize(100)
            .expireAfterWrite(TTL_MS, TimeUnit.MILLISECONDS)
            .build();

    cache.put("a", "1");
    cache.put("b", "2");
    sleepQuietly(WAIT_MS);
    // 未发生任何读取，过期条目仍占容量
    assertThat(cache.estimatedSize()).isEqualTo(2);

    cache.cleanUp();

    assertThat(cache.estimatedSize()).as("cleanUp 后过期条目应被回收").isZero();
  }

  @Test
  @DisplayName("未过期条目不受清理影响")
  void cleanUpShouldKeepFreshEntries() {
    Cache<String, String> cache =
        YdszCache.<String, String>newBuilder()
            .maximumSize(100)
            .expireAfterWrite(TTL_MS * 4, TimeUnit.MILLISECONDS)
            .build();

    cache.put("fresh", "v");
    sleepQuietly(TTL_MS);
    cache.cleanUp();

    assertThat(cache.getIfPresent("fresh")).isEqualTo("v");
  }

  /** 静默休眠（测试辅助，不抛检查异常） */
  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
