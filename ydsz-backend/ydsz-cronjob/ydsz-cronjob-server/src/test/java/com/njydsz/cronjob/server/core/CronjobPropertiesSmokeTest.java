package com.njydsz.cronjob.server.core;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.cronjob.server.config.CronjobProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最小冒烟级单测，补充 P0 测试覆盖缺口。
 *
 * <p>测试 {@link CronjobProperties#normalizeTtl(Duration)} 的 TTL 规整化逻辑：
 * <ul>
 *   <li>null / 零 / 负数 → 返回默认值</li>
 *   <li>低于下限 → 收敛到 min</li>
 *   <li>高于上限 → 收敛到 max</li>
 *   <li>区间内 → 原值返回</li>
 * </ul>
 *
 * <p>纯计算、无外部依赖（DB/Redis），直接 new CronjobProperties 即可。
 */
class CronjobPropertiesSmokeTest {

    private CronjobProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CronjobProperties();
    }

    @Nested
    @DisplayName("normalizeTtl - TTL 规整化")
    class NormalizeTtlTests {

        @Test
        @DisplayName("null 返回默认 TTL (5 分钟)")
        void nullValue_returnsDefault() {
            Duration result = properties.normalizeTtl(null);
            assertThat(result).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("零值和负数返回默认 TTL")
        void zeroOrNegative_returnsDefault() {
            assertThat(properties.normalizeTtl(Duration.ZERO)).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.normalizeTtl(Duration.ofSeconds(-1))).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("低于下限 (30s) 收敛到 min")
        void belowMin_clampedToMin() {
            Duration result = properties.normalizeTtl(Duration.ofSeconds(10));
            assertThat(result).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("高于上限 (24h) 收敛到 max")
        void aboveMax_clampedToMax() {
            Duration result = properties.normalizeTtl(Duration.ofHours(48));
            assertThat(result).isEqualTo(Duration.ofHours(24));
        }

        @Test
        @DisplayName("区间内原值返回")
        void withinRange_returnsAsIs() {
            Duration input = Duration.ofMinutes(10);
            Duration result = properties.normalizeTtl(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("边界值刚好等于 min 返回 min")
        void exactlyMin_returnsMin() {
            Duration result = properties.normalizeTtl(Duration.ofSeconds(30));
            assertThat(result).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("边界值刚好等于 max 返回 max")
        void exactlyMax_returnsMax() {
            Duration result = properties.normalizeTtl(Duration.ofHours(24));
            assertThat(result).isEqualTo(Duration.ofHours(24));
        }
    }

    @Nested
    @DisplayName("default values - 默认值验证")
    class DefaultValuesTests {

        @Test
        @DisplayName("默认配置项有合理值")
        void defaults_haveReasonableValues() {
            assertThat(properties.getJobLockTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getExecutor().getMaxConcurrent()).isEqualTo(16);
            assertThat(properties.getLeader().isEnabled()).isTrue();
            assertThat(properties.getScanner().getBatchSize()).isEqualTo(500);
        }
    }
}
