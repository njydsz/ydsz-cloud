package com.njydsz.pmis.user.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UtilizationCalculator 利用率计算测试
 */
@DisplayName("UtilizationCalculator 利用率计算")
class UtilizationCalculatorTest {

    @Test
    @DisplayName("billableUtilization 正常分摊")
    void billableNormal() {
        BigDecimal r = UtilizationCalculator.billableUtilization(new BigDecimal("60"), new BigDecimal("80"));
        assertThat(r).isEqualByComparingTo(new BigDecimal("0.7500"));
    }

    @Test
    @DisplayName("billableUtilization 总工时 0 返回 0")
    void totalZero() {
        assertThat(UtilizationCalculator.billableUtilization(new BigDecimal("10"), BigDecimal.ZERO))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("billableUtilization billableHours 为空视为 0")
    void nullBillable() {
        assertThat(UtilizationCalculator.billableUtilization(null, new BigDecimal("10")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("isOverloaded 阈值 3")
    void overloaded() {
        assertThat(UtilizationCalculator.isOverloaded(2)).isFalse();
        assertThat(UtilizationCalculator.isOverloaded(3)).isTrue();
        assertThat(UtilizationCalculator.isOverloaded(5)).isTrue();
    }

    @Test
    @DisplayName("utilizationLevel <60% LOW")
    void levelLow() {
        assertThat(UtilizationCalculator.utilizationLevel(new BigDecimal("0.30"))).isEqualTo("LOW");
    }

    @Test
    @DisplayName("utilizationLevel 60-85% NORMAL")
    void levelNormal() {
        assertThat(UtilizationCalculator.utilizationLevel(new BigDecimal("0.60"))).isEqualTo("NORMAL");
        assertThat(UtilizationCalculator.utilizationLevel(new BigDecimal("0.80"))).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("utilizationLevel >=85% HIGH")
    void levelHigh() {
        assertThat(UtilizationCalculator.utilizationLevel(new BigDecimal("0.85"))).isEqualTo("HIGH");
        assertThat(UtilizationCalculator.utilizationLevel(new BigDecimal("0.99"))).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("utilizationLevel null/异常值降级 LOW")
    void levelFallback() {
        assertThat(UtilizationCalculator.utilizationLevel(null)).isEqualTo("LOW");
    }
}
