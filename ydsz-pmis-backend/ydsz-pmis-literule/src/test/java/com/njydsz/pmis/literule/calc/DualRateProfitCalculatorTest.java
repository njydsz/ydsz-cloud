package com.njydsz.pmis.literule.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("DualRateProfitCalculator 双费率利润")
class DualRateProfitCalculatorTest {

    @Test
    @DisplayName("单职级 L8 报价4500 成本2800 工时10天")
    void singleLevel() {
        DualRateProfitCalculator.ProfitResult r = DualRateProfitCalculator.calculate(
                new BigDecimal("4500"), new BigDecimal("2800"), new BigDecimal("10"));
        assertThat(r.externalRevenue).isEqualByComparingTo("45000");
        assertThat(r.internalCost).isEqualByComparingTo("28000");
        assertThat(r.grossProfit).isEqualByComparingTo("17000");
        // 17000/45000 = 0.3778
        assertThat(r.grossMargin.doubleValue()).isEqualTo(0.3778, within(0.001));
        assertThat(r.blendedRate).isEqualByComparingTo("4500");
    }

    @Test
    @DisplayName("混合职级 L5+L8 加权")
    void blended() {
        List<DualRateProfitCalculator.BlendedInput> items = List.of(
                DualRateProfitCalculator.BlendedInput.of("L5", new BigDecimal("2600"), new BigDecimal("1800"), new BigDecimal("20")),
                DualRateProfitCalculator.BlendedInput.of("L8", new BigDecimal("4000"), new BigDecimal("2800"), new BigDecimal("10"))
        );
        DualRateProfitCalculator.ProfitResult r = DualRateProfitCalculator.calculateBlended(items);
        // rev = 2600*20 + 4000*10 = 52000 + 40000 = 92000
        // cost = 1800*20 + 2800*10 = 36000 + 28000 = 64000
        // profit = 28000
        // margin = 28000/92000 = 0.3043
        assertThat(r.externalRevenue).isEqualByComparingTo("92000");
        assertThat(r.internalCost).isEqualByComparingTo("64000");
        assertThat(r.grossProfit).isEqualByComparingTo("28000");
        assertThat(r.grossMargin.doubleValue()).isEqualTo(0.3043, within(0.001));
        assertThat(r.expectedHours).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("空输入 零值兜底")
    void emptyInput() {
        DualRateProfitCalculator.ProfitResult r = DualRateProfitCalculator.calculateBlended(List.of());
        assertThat(r.externalRevenue.signum()).isZero();
        assertThat(r.grossMargin.signum()).isZero();
    }

    @Test
    @DisplayName("null 参数不抛异常")
    void nullSafe() {
        DualRateProfitCalculator.ProfitResult r = DualRateProfitCalculator.calculate(
                null, null, new BigDecimal("10"));
        assertThat(r.externalRevenue.signum()).isZero();
        assertThat(r.internalCost.signum()).isZero();
        assertThat(r.grossProfit.signum()).isZero();
    }

    @Test
    @DisplayName("marginAchieved 达成判断")
    void marginAchievedCheck() {
        BigDecimal actual = new BigDecimal("0.30");
        BigDecimal target = new BigDecimal("0.25");
        assertThat(DualRateProfitCalculator.marginAchieved(actual, target)).isTrue();
        assertThat(DualRateProfitCalculator.marginAchieved(new BigDecimal("0.20"), target)).isFalse();
        assertThat(DualRateProfitCalculator.marginAchieved(null, target)).isFalse();
    }
}
