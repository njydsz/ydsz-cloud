package com.njydsz.common.cache.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CacheStats 不可变快照测试。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class CacheStatsTest {

  @Nested
  @DisplayName("构造函数")
  class ConstructorTest {

    @Test
    @DisplayName("两参构造函数创建含 hit/miss 的快照")
    void twoArgConstructor_shouldSetHitAndMiss() {
      CacheStats stats = new CacheStats(10L, 5L);
      assertThat(stats.getHitCount()).isEqualTo(10L);
      assertThat(stats.getMissCount()).isEqualTo(5L);
      assertThat(stats.getEvictionCount()).isZero();
      assertThat(stats.getLoadCount()).isZero();
    }

    @Test
    @DisplayName("七参构造函数完整填充所有字段")
    void sevenArgConstructor_shouldFillAllFields() {
      CacheStats stats = new CacheStats(100L, 20L, 5L, 25L, 22L, 3L, 1_000_000L);
      assertThat(stats.getHitCount()).isEqualTo(100L);
      assertThat(stats.getMissCount()).isEqualTo(20L);
      assertThat(stats.getEvictionCount()).isEqualTo(5L);
      assertThat(stats.getLoadCount()).isEqualTo(25L);
      assertThat(stats.getLoadSuccessCount()).isEqualTo(22L);
      assertThat(stats.getLoadExceptionCount()).isEqualTo(3L);
      assertThat(stats.getTotalLoadTimeNanos()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("EMPTY 常量为全零快照")
    void empty_shouldBeAllZeros() {
      assertThat(CacheStats.EMPTY.getHitCount()).isZero();
      assertThat(CacheStats.EMPTY.getMissCount()).isZero();
      assertThat(CacheStats.EMPTY.getEvictionCount()).isZero();
    }
  }

  @Nested
  @DisplayName("Builder 模式")
  class BuilderTest {

    @Test
    @DisplayName("Builder 构建默认全零快照")
    void builder_shouldCreateZeroStatsByDefault() {
      CacheStats stats = CacheStats.builder().build();
      assertThat(stats.getHitCount()).isZero();
      assertThat(stats.getMissCount()).isZero();
      assertThat(stats.getEvictionCount()).isZero();
      assertThat(stats.getLoadCount()).isZero();
    }

    @Test
    @DisplayName("Builder 链式设置所有字段")
    void builder_shouldSetAllFields() {
      CacheStats stats =
          CacheStats.builder()
              .hitCount(50L)
              .missCount(10L)
              .evictionCount(3L)
              .loadCount(15L)
              .loadSuccessCount(12L)
              .loadExceptionCount(3L)
              .totalLoadTimeNanos(500_000L)
              .build();

      assertThat(stats.getHitCount()).isEqualTo(50L);
      assertThat(stats.getMissCount()).isEqualTo(10L);
      assertThat(stats.getEvictionCount()).isEqualTo(3L);
      assertThat(stats.getLoadCount()).isEqualTo(15L);
      assertThat(stats.getLoadSuccessCount()).isEqualTo(12L);
      assertThat(stats.getLoadExceptionCount()).isEqualTo(3L);
      assertThat(stats.getTotalLoadTimeNanos()).isEqualTo(500_000L);
    }
  }

  @Nested
  @DisplayName("计算指标")
  class CalculatedMetricsTest {

    @Test
    @DisplayName("getHitRate 命中率 = hits / (hits + misses)")
    void getHitRate_shouldCalculateCorrectly() {
      CacheStats stats = new CacheStats(80L, 20L);
      assertThat(stats.getHitRate()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("getMissRate = 1 - hitRate")
    void getMissRate_shouldComplementHitRate() {
      CacheStats stats = new CacheStats(60L, 40L);
      assertThat(stats.getMissRate()).isEqualTo(0.4);
    }

    @Test
    @DisplayName("getAverageLoadPenalty = totalLoadTime / loadSuccessCount")
    void getAverageLoadPenalty_shouldCalculateCorrectly() {
      CacheStats stats = new CacheStats(0L, 0L, 0L, 0L, 5L, 0L, 1_000_000L);
      assertThat(stats.getAverageLoadPenalty()).isEqualTo(200_000.0);
    }

    @Test
    @DisplayName("getLoadSuccessRate = loadSuccess / loadCount")
    void getLoadSuccessRate_shouldCalculateCorrectly() {
      CacheStats stats = new CacheStats(0L, 0L, 0L, 10L, 8L, 2L, 0L);
      assertThat(stats.getLoadSuccessRate()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("getTotalAccessCount = hit + miss")
    void getTotalAccessCount_shouldSumHitsAndMisses() {
      CacheStats stats = new CacheStats(70L, 30L);
      assertThat(stats.getTotalAccessCount()).isEqualTo(100L);
    }
  }

  @Nested
  @DisplayName("合并与差值")
  class MergeAndDiffTest {

    @Test
    @DisplayName("plus 合并两个快照")
    void plus_shouldSumAllFields() {
      CacheStats a = new CacheStats(10L, 5L, 1L, 6L, 5L, 1L, 100L);
      CacheStats b = new CacheStats(20L, 10L, 2L, 12L, 10L, 2L, 200L);

      CacheStats merged = a.plus(b);
      assertThat(merged.getHitCount()).isEqualTo(30L);
      assertThat(merged.getMissCount()).isEqualTo(15L);
      assertThat(merged.getEvictionCount()).isEqualTo(3L);
      assertThat(merged.getTotalLoadTimeNanos()).isEqualTo(300L);
    }

    @Test
    @DisplayName("minus 计算差值，不会为负")
    void minus_shouldNotBeNegative() {
      CacheStats a = new CacheStats(30L, 15L, 3L, 18L, 15L, 3L, 300L);
      CacheStats b = new CacheStats(10L, 5L, 1L, 6L, 5L, 1L, 100L);

      CacheStats diff = a.minus(b);
      assertThat(diff.getHitCount()).isEqualTo(20L);
      assertThat(diff.getMissCount()).isEqualTo(10L);
      assertThat(diff.getEvictionCount()).isEqualTo(2L);
      assertThat(diff.getTotalLoadTimeNanos()).isEqualTo(200L);
    }

    @Test
    @DisplayName("minus 差值小于 0 时返回 0")
    void minus_shouldClampAtZero() {
      CacheStats a = new CacheStats(5L, 2L);
      CacheStats b = new CacheStats(10L, 8L);

      CacheStats diff = a.minus(b);
      assertThat(diff.getHitCount()).isZero();
      assertThat(diff.getMissCount()).isZero();
    }
  }

  @Nested
  @DisplayName("相等性")
  class EqualityTest {

    @Test
    @DisplayName("相同字段的快照应相等")
    void equalStats_shouldBeEqual() {
      CacheStats a = new CacheStats(10L, 5L, 1L, 6L, 5L, 1L, 100L);
      CacheStats b = new CacheStats(10L, 5L, 1L, 6L, 5L, 1L, 100L);
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("toString 包含关键指标")
    void toString_shouldContainKeyMetrics() {
      CacheStats stats = new CacheStats(80L, 20L);
      String str = stats.toString();
      assertThat(str).contains("hitCount=80").contains("missCount=20");
    }
  }
}
