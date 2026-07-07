package com.njydsz.pmis.agent.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuotaSummary 配额概览 DTO 单元测试（P2-4 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@DisplayName("QuotaSummary 配额概览 DTO")
class QuotaSummaryTest {

    @Nested
    @DisplayName("of() 静态工厂方法")
    class OfMethodTest {

        @Test
        @DisplayName("正常配额：remaining 和 percentage 计算正确")
        void shouldCalculateRemainingAndPercentage() {
            QuotaSummary summary = QuotaSummary.of("1", "202607",
                    1000000L, 300000L, "ACTIVE", null);

            assertThat(summary.getTenantId()).isEqualTo("1");
            assertThat(summary.getQuotaMonth()).isEqualTo("202607");
            assertThat(summary.getTotalQuota()).isEqualTo(1000000L);
            assertThat(summary.getUsedTokens()).isEqualTo(300000L);
            assertThat(summary.getRemainingTokens()).isEqualTo(700000L);
            assertThat(summary.getUsagePercentage()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(summary.getStatus()).isEqualTo("ACTIVE");
            assertThat(summary.getResetAt()).isNull();
        }

        @Test
        @DisplayName("已用=0 时 remaining=total，percentage=0")
        void shouldHandleZeroUsage() {
            QuotaSummary summary = QuotaSummary.of("1", "202607",
                    1000000L, 0L, "ACTIVE", null);

            assertThat(summary.getRemainingTokens()).isEqualTo(1000000L);
            assertThat(summary.getUsagePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("已用 > 总量 时 remaining=0（避免负数）")
        void shouldClampRemainingToZeroWhenOverLimit() {
            QuotaSummary summary = QuotaSummary.of("1", "202607",
                    1000L, 1500L, "RUNOUT", null);

            assertThat(summary.getRemainingTokens()).isZero();
            assertThat(summary.getUsagePercentage()).isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("总量=0 时 percentage=0（避免除零）")
        void shouldHandleZeroTotalQuota() {
            QuotaSummary summary = QuotaSummary.of("1", "202607",
                    0L, 0L, "ACTIVE", null);

            assertThat(summary.getRemainingTokens()).isZero();
            assertThat(summary.getUsagePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("percentage 保留 2 位小数")
        void shouldRoundPercentageToTwoDecimals() {
            QuotaSummary summary = QuotaSummary.of("1", "202607",
                    3L, 1L, "ACTIVE", null);

            // 1/3 * 100 = 33.33...
            assertThat(summary.getUsagePercentage()).isEqualByComparingTo(new BigDecimal("33.33"));
        }

        @Test
        @DisplayName("resetAt 透传")
        void shouldPassThroughResetAt() {
            LocalDateTime resetAt = LocalDateTime.of(2026, 7, 1, 0, 0, 0);
            QuotaSummary summary = QuotaSummary.of("1", "202607",
                    1000000L, 0L, "RESET", resetAt);

            assertThat(summary.getResetAt()).isEqualTo(resetAt);
        }
    }
}
