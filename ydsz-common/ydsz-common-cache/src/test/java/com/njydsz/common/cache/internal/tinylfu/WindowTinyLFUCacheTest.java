package com.njydsz.common.cache.internal.tinylfu;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.builder.CacheType;

/**
 * WindowTinyLFUCache 核心行为回归测试。
 *
 * <p>覆盖三类不变量：容量硬上限、clear 后内部计数一致性（P1 修复回归：此前 clear 漏重置 windowSize /
 * totalCount，Window 段容量治理永久失真）、构建器默认容量语义（P0 修复回归：maximumSize 未设置时不得退化为
 * initialCapacity=16 的小缓存）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class WindowTinyLFUCacheTest {

  /** 反射读取私有计数器字段（仅测试用，锁定期望的内部不变量） */
  private static long readCounter(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return ((java.util.concurrent.atomic.AtomicLong) field.get(target)).get();
  }

  @Test
  @DisplayName("容量硬上限：超过 maximumSize 后触发淘汰且条目数不超限")
  void capacityShouldBeEnforced() {
    WindowTinyLFUCache<String, String> cache = new WindowTinyLFUCache<>(100);
    for (int i = 0; i < 500; i++) {
      cache.put("key-" + i, "value-" + i);
    }
    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(100);
    // 已写入条目可正常读取（数据表与链表一致，无野指针导致的错乱）
    assertThat(cache.getIfPresent("key-499")).isEqualTo("value-499");
  }

  @Test
  @DisplayName("clear 后全部分段计数归零（windowSize/totalCount 泄漏回归）")
  void clearShouldResetAllCounters() throws Exception {
    WindowTinyLFUCache<String, String> cache = new WindowTinyLFUCache<>(50);
    for (int i = 0; i < 30; i++) {
      cache.put("key-" + i, "value-" + i);
    }
    assertThat(cache.estimatedSize()).isEqualTo(30);

    cache.clear();

    assertThat(cache.estimatedSize()).isZero();
    assertThat(cache.getIfPresent("key-0")).isNull();
    // P1 回归断言：clear 必须同步重置 windowSize 与 totalCount
    assertThat(readCounter(cache, "windowSize")).isZero();
    assertThat(readCounter(cache, "protectedSize")).isZero();
    assertThat(readCounter(cache, "sizeCounter")).isZero();

    // clear 后再次写入，容量治理按新计数执行（不残留旧状态）
    for (int i = 0; i < 10; i++) {
      cache.put("new-" + i, "v-" + i);
    }
    assertThat(cache.estimatedSize()).isEqualTo(10);
    assertThat(cache.getIfPresent("new-9")).isEqualTo("v-9");
  }

  @Test
  @DisplayName("命中/未命中统计与命中率口径正确")
  void statsShouldCountHitsAndMisses() {
    WindowTinyLFUCache<String, String> cache = new WindowTinyLFUCache<>(100);
    cache.put("a", "1");
    cache.getIfPresent("a");
    cache.getIfPresent("a");
    cache.getIfPresent("missing");

    assertThat(cache.getStats().getHitCount()).isEqualTo(2);
    assertThat(cache.getStats().getMissCount()).isEqualTo(1);
    assertThat(cache.getHitRate()).isEqualTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  @DisplayName("P0 回归：构建器未设置 maximumSize 时兜底容量不得退化为 16")
  void builderDefaultCapacityShouldNotDegradeTo16() {
    com.njydsz.common.cache.api.Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).build();
    for (int i = 0; i < 100; i++) {
      cache.put("key-" + i, "value-" + i);
    }
    // 修复前：误用 initialCapacity(16) 作容量，第 17 条起即触发淘汰
    assertThat(cache.estimatedSize()).isEqualTo(100);
  }
}
