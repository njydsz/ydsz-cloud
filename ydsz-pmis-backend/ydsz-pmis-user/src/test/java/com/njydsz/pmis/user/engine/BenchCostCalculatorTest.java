package com.njydsz.pmis.user.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BenchCostCalculator 引擎测试
 */
@DisplayName("BenchCostCalculator 闲置成本计算")
class BenchCostCalculatorTest {

    @Test
    @DisplayName("idleDays 同日返回 0")
    void sameDay() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThat(BenchCostCalculator.idleDays(d, d)).isEqualTo(0);
    }

    @Test
    @DisplayName("idleDays exitDate 为空用今天")
    void exitDateNull() {
        LocalDate d = LocalDate.now().minusDays(5);
        assertThat(BenchCostCalculator.idleDays(d, null)).isEqualTo(5);
    }

    @Test
    @DisplayName("idleDays exitDate 早于 benchDate 返回 0")
    void beforeBench() {
        LocalDate d = LocalDate.of(2026, 1, 10);
        assertThat(BenchCostCalculator.idleDays(d, LocalDate.of(2026, 1, 1))).isEqualTo(0);
    }

    @Test
    @DisplayName("idleDays benchDate 为空返回 0")
    void nullBench() {
        assertThat(BenchCostCalculator.idleDays(null, LocalDate.now())).isEqualTo(0);
    }

    @Test
    @DisplayName("totalIdleCost 累计")
    void totalCost() {
        assertThat(BenchCostCalculator.totalIdleCost(new BigDecimal("500"), 10))
                .isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("totalIdleCost dailyCost 为空视为 0")
    void nullDaily() {
        assertThat(BenchCostCalculator.totalIdleCost(null, 10))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("withinTrainingWindow 30 天内")
    void trainingWindow() {
        LocalDate recent = LocalDate.now().minusDays(20);
        assertThat(BenchCostCalculator.withinTrainingWindow(recent)).isTrue();
    }

    @Test
    @DisplayName("withinTrainingWindow 超过 30 天")
    void trainingWindowExceeded() {
        LocalDate old = LocalDate.now().minusDays(40);
        assertThat(BenchCostCalculator.withinTrainingWindow(old)).isFalse();
    }

    @Test
    @DisplayName("TRAINING_MAX_DAYS 阈值 30")
    void constant() {
        assertThat(BenchCostCalculator.TRAINING_MAX_DAYS).isEqualTo(30);
    }
}
