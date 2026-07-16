package com.njydsz.pmis.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.stats.CacheStats;

/**
 * CacheStats 单元测试
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>Builder 模式构建
 *   <li>命中率/未命中率计算
 *   <li>平均加载耗时计算
 *   <li>plus/minus 合并操作
 *   <li>equals/hashCode
 *   <li>EMPTY 常量
 * </ul>
 *
 * @since 1.0.0
 */
@DisplayName("CacheStats 单元测试")
class CacheStatsTest {

  @Test
  @DisplayName("Builder 模式构建统计快照")
  void shouldBuildWithBuilder() {
    CacheStats stats =
        CacheStats.builder()
            .hitCount(100)
            .missCount(20)
            .evictionCount(5)
            .loadCount(15)
            .loadSuccessCount(14)
            .loadExceptionCount(1)
            .totalLoadTimeNanos(3_000_000)
            .build();

    assertThat(stats.getHitCount()).isEqualTo(100);
    assertThat(stats.getMissCount()).isEqualTo(20);
    assertThat(stats.getEvictionCount()).isEqualTo(5);
    assertThat(stats.getLoadCount()).isEqualTo(15);
    assertThat(stats.getLoadSuccessCount()).isEqualTo(14);
    assertThat(stats.getLoadExceptionCount()).isEqualTo(1);
    assertThat(stats.getTotalLoadTimeNanos()).isEqualTo(3_000_000);
  }

  @Test
  @DisplayName("Builder 默认值为零")
  void shouldDefaultToZeros() {
    CacheStats stats = CacheStats.builder().build();

    assertThat(stats.getHitCount()).isZero();
    assertThat(stats.getMissCount()).isZero();
    assertThat(stats.getEvictionCount()).isZero();
    assertThat(stats.getLoadCount()).isZero();
    assertThat(stats.getLoadSuccessCount()).isZero();
    assertThat(stats.getLoadExceptionCount()).isZero();
    assertThat(stats.getTotalLoadTimeNanos()).isZero();
  }

  @Test
  @DisplayName("命中率计算正确")
  void shouldCalculateHitRate() {
    CacheStats stats = new CacheStats(80, 20);

    assertThat(stats.getHitRate()).isEqualTo(0.8);
    assertThat(stats.getMissRate()).isEqualTo(0.2);
    assertThat(stats.getTotalAccessCount()).isEqualTo(100);
  }

  @Test
  @DisplayName("零访问时命中率为 0")
  void shouldReturnZeroHitRateWhenNoAccess() {
    CacheStats stats = CacheStats.EMPTY;

    assertThat(stats.getHitRate()).isZero();
    assertThat(stats.getMissRate()).isZero();
    assertThat(stats.getTotalAccessCount()).isZero();
  }

  @Test
  @DisplayName("平均加载耗时计算正确")
  void shouldCalculateAverageLoadPenalty() {
    CacheStats stats =
        new CacheStats(10, 5, 0, 10, 10, 0, 5_000_000);

    assertThat(stats.getAverageLoadPenalty()).isEqualTo(500_000.0);
    assertThat(stats.getAverageLoadPenaltyPerAccess()).isEqualTo(5_000_000.0 / 15.0);
  }

  @Test
  @DisplayName("加载成功率计算正确")
  void shouldCalculateLoadSuccessRate() {
    CacheStats stats =
        new CacheStats(0, 0, 0, 20, 18, 2, 0);

    assertThat(stats.getLoadSuccessRate()).isEqualTo(0.9);
  }

  @Test
  @DisplayName("plus 合并两个统计快照")
  void shouldPlusMergeStats() {
    CacheStats s1 = new CacheStats(50, 10, 2, 5, 5, 0, 1_000_000);
    CacheStats s2 = new CacheStats(30, 5, 1, 3, 3, 0, 500_000);

    CacheStats merged = s1.plus(s2);

    assertThat(merged.getHitCount()).isEqualTo(80);
    assertThat(merged.getMissCount()).isEqualTo(15);
    assertThat(merged.getEvictionCount()).isEqualTo(3);
    assertThat(merged.getLoadCount()).isEqualTo(8);
    assertThat(merged.getLoadSuccessCount()).isEqualTo(8);
    assertThat(merged.getTotalLoadTimeNanos()).isEqualTo(1_500_000);
  }

  @Test
  @DisplayName("minus 计算差值且不会出现负数")
  void shouldMinusWithFloorAtZero() {
    CacheStats s1 = new CacheStats(50, 20, 5, 10, 8, 2, 2_000_000);
    CacheStats s2 = new CacheStats(60, 10, 8, 5, 5, 0, 1_000_000);

    CacheStats diff = s1.minus(s2);

    assertThat(diff.getHitCount()).isZero();
    assertThat(diff.getMissCount()).isEqualTo(10);
    assertThat(diff.getEvictionCount()).isZero();
    assertThat(diff.getLoadCount()).isEqualTo(5);
    assertThat(diff.getLoadSuccessCount()).isEqualTo(3);
    assertThat(diff.getLoadExceptionCount()).isEqualTo(2);
    assertThat(diff.getTotalLoadTimeNanos()).isEqualTo(1_000_000);
  }

  @Test
  @DisplayName("equals 和 hashCode 正确")
  void shouldEqualsAndHashCode() {
    CacheStats s1 = CacheStats.builder().hitCount(10).missCount(5).build();
    CacheStats s2 = CacheStats.builder().hitCount(10).missCount(5).build();
    CacheStats s3 = CacheStats.builder().hitCount(10).missCount(6).build();

    assertThat(s1).isEqualTo(s2);
    assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    assertThat(s1).isNotEqualTo(s3);
  }

  @Test
  @DisplayName("toString 包含关键指标")
  void shouldToStringContainMetrics() {
    CacheStats stats = new CacheStats(100, 50);

    String str = stats.toString();
    assertThat(str).contains("hitCount=100");
    assertThat(str).contains("missCount=50");
    assertThat(str).contains("hitRate=");
  }

  @Test
  @DisplayName("EMPTY 常量为零值快照")
  void shouldEmptyConstantBeZeros() {
    assertThat(CacheStats.EMPTY.getHitCount()).isZero();
    assertThat(CacheStats.EMPTY.getMissCount()).isZero();
    assertThat(CacheStats.EMPTY.getHitRate()).isZero();
    assertThat(CacheStats.EMPTY.getTotalAccessCount()).isZero();
  }
}
