package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.entity.ProfitSnapshotDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProfitCalculator 利润计算器测试")
class ProfitCalculatorTest {

    @Test
    @DisplayName("毛利率")
    void grossMargin() {
        assertThat(ProfitCalculator.grossMargin(new BigDecimal("200"), new BigDecimal("1000")))
                .isEqualByComparingTo("0.2000");
        assertThat(ProfitCalculator.grossMargin(null, new BigDecimal("1000")))
                .isEqualByComparingTo("0.0000");
        assertThat(ProfitCalculator.grossMargin(new BigDecimal("200"), BigDecimal.ZERO))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("总成本")
    void totalCost() {
        assertThat(ProfitCalculator.totalCost(List.of(
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300")
        ))).isEqualByComparingTo("600");
        assertThat(ProfitCalculator.totalCost(null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("毛利")
    void grossProfit() {
        assertThat(ProfitCalculator.grossProfit(new BigDecimal("1000"), new BigDecimal("600")))
                .isEqualByComparingTo("400");
    }

    @Test
    @DisplayName("EAC")
    void eac() {
        // totalCost=600, progress=50% -> EAC=1200
        assertThat(ProfitCalculator.eac(new BigDecimal("600"), new BigDecimal("50")))
                .isEqualByComparingTo("1200.00");
        assertThat(ProfitCalculator.eac(new BigDecimal("600"), BigDecimal.ZERO))
                .isEqualByComparingTo("600");
    }

    @Test
    @DisplayName("健康度评分-理想")
    void healthScoreIdeal() {
        int s = ProfitCalculator.healthScore(
                new BigDecimal("0.25"), new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("1000"), new BigDecimal("800"));
        // 50 (毛利率) + 30 (进度) + 20 (成本) = 100
        assertThat(s).isEqualTo(100);
    }

    @Test
    @DisplayName("健康度评分-差")
    void healthScoreBad() {
        int s = ProfitCalculator.healthScore(
                BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("30"),
                new BigDecimal("1000"), new BigDecimal("2000"));
        // 0 (毛利率) + 9 (进度30%) + 0 (成本超2倍) = 9
        assertThat(s).isLessThan(20);
    }

    @Test
    @DisplayName("回填派生字段")
    void fillDerived() {
        ProfitSnapshotDO s = new ProfitSnapshotDO();
        s.setRecognizedRevenue(new BigDecimal("1000"));
        s.setLaborCost(new BigDecimal("200"));
        s.setPurchaseCost(new BigDecimal("100"));
        ProfitCalculator.fillDerived(s);
        assertThat(s.getTotalCost()).isEqualByComparingTo("300");
        assertThat(s.getGrossProfit()).isEqualByComparingTo("700");
        assertThat(s.getGrossMargin()).isEqualByComparingTo("0.7000");
    }
}
