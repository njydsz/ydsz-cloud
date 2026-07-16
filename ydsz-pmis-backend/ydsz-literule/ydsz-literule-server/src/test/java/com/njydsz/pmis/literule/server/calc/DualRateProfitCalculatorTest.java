package com.njydsz.literule.server.calc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DualRateProfitCalculator} 单元测试：覆盖单一职级计算、混合费率计算与利润率达成判断。
 *
 * @since 1.0.0
 */
@DisplayName("双费率利润计算引擎 DualRateProfitCalculator 测试")
class DualRateProfitCalculatorTest {

    @Nested
    @DisplayName("单一职级 calculate 计算")
    class CalculateCases {

        @Test
        @DisplayName("正常计算：对外1000/成本600/工时10 → 收入10000 成本6000 利润4000 毛利率40%")
        void shouldCalculateNormalProfit() {
            DualRateProfitCalculator.ProfitResult result = DualRateProfitCalculator.calculate(
                    new BigDecimal("1000"),
                    new BigDecimal("600"),
                    new BigDecimal("10"));

            assertThat(result.externalRevenue).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(result.internalCost).isEqualByComparingTo(new BigDecimal("6000"));
            assertThat(result.grossProfit).isEqualByComparingTo(new BigDecimal("4000"));
            assertThat(result.grossMargin).isEqualByComparingTo(new BigDecimal("0.4"));
            assertThat(result.expectedHours).isEqualByComparingTo(new BigDecimal("10"));
            assertThat(result.blendedRate).isEqualByComparingTo(new BigDecimal("1000"));
        }

        @Test
        @DisplayName("零工时：收入成本利润均为 0，毛利率 0")
        void shouldReturnZeroWhenHoursZero() {
            DualRateProfitCalculator.ProfitResult result = DualRateProfitCalculator.calculate(
                    new BigDecimal("1000"),
                    new BigDecimal("600"),
                    new BigDecimal("0"));

            assertThat(result.externalRevenue).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.internalCost).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.grossProfit).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.grossMargin).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.expectedHours).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.blendedRate).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("null 入参：全部按 0 处理")
        void shouldTreatNullAsZero() {
            DualRateProfitCalculator.ProfitResult result = DualRateProfitCalculator.calculate(
                    null,
                    null,
                    null);

            assertThat(result.externalRevenue).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.internalCost).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.grossProfit).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.grossMargin).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.expectedHours).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.blendedRate).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("零成本：毛利率 100%")
        void shouldReturnFullMarginWhenCostZero() {
            DualRateProfitCalculator.ProfitResult result = DualRateProfitCalculator.calculate(
                    new BigDecimal("1000"),
                    new BigDecimal("0"),
                    new BigDecimal("10"));

            assertThat(result.grossProfit).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(result.grossMargin).isEqualByComparingTo(new BigDecimal("1.0"));
        }

        @Test
        @DisplayName("负利润：成本高于对外费率 → 毛利率为负")
        void shouldReturnNegativeMargin() {
            DualRateProfitCalculator.ProfitResult result = DualRateProfitCalculator.calculate(
                    new BigDecimal("500"),
                    new BigDecimal("800"),
                    new BigDecimal("10"));

            assertThat(result.externalRevenue).isEqualByComparingTo(new BigDecimal("5000"));
            assertThat(result.internalCost).isEqualByComparingTo(new BigDecimal("8000"));
            assertThat(result.grossProfit).isEqualByComparingTo(new BigDecimal("-3000"));
            assertThat(result.grossMargin).isEqualByComparingTo(new BigDecimal("-0.6"));
        }
    }

    @Nested
    @DisplayName("混合职级 calculateBlended 计算")
    class BlendedCases {

        @Test
        @DisplayName("正常混合：两职级加权汇总")
        void shouldCalculateBlended() {
            // 职级A：对外1000/成本600/工时10 → 收入10000 成本6000
            // 职级B：对外1500/成本900/工时5  → 收入7500  成本4500
            // 合计：收入17500 成本10500 工时15 利润7000 毛利率40% 混合费率1166.67
            List<DualRateProfitCalculator.BlendedInput> items = List.of(
                    DualRateProfitCalculator.BlendedInput.of(
                            "A", new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("10")),
                    DualRateProfitCalculator.BlendedInput.of(
                            "B", new BigDecimal("1500"), new BigDecimal("900"), new BigDecimal("5")));

            DualRateProfitCalculator.ProfitResult result = DualRateProfitCalculator.calculateBlended(items);

            assertThat(result.externalRevenue).isEqualByComparingTo(new BigDecimal("17500"));
            assertThat(result.internalCost).isEqualByComparingTo(new BigDecimal("10500"));
            assertThat(result.grossProfit).isEqualByComparingTo(new BigDecimal("7000"));
            assertThat(result.grossMargin).isEqualByComparingTo(new BigDecimal("0.4"));
            assertThat(result.expectedHours).isEqualByComparingTo(new BigDecimal("15"));
            assertThat(result.blendedRate).isEqualByComparingTo(new BigDecimal("1166.67"));
        }

        @Test
        @DisplayName("空列表：全部为 0")
        void shouldReturnZeroForEmptyList() {
            DualRateProfitCalculator.ProfitResult result =
                    DualRateProfitCalculator.calculateBlended(Collections.emptyList());

            assertThat(result.externalRevenue).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.internalCost).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.grossProfit).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.grossMargin).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.expectedHours).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.blendedRate).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("null 列表：全部为 0")
        void shouldReturnZeroForNullList() {
            DualRateProfitCalculator.ProfitResult result =
                    DualRateProfitCalculator.calculateBlended(null);

            assertThat(result.externalRevenue).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.internalCost).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.grossProfit).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.grossMargin).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.expectedHours).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.blendedRate).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("包含 null 元素：跳过 null 项")
        void shouldSkipNullItems() {
            // 使用 Arrays.asList 以允许 null 元素（List.of 不允许 null）
            List<DualRateProfitCalculator.BlendedInput> items = Arrays.asList(
                    DualRateProfitCalculator.BlendedInput.of(
                            "A", new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("10")),
                    null);

            DualRateProfitCalculator.ProfitResult result = DualRateProfitCalculator.calculateBlended(items);

            // 仅计算职级A：收入10000 成本6000 利润4000
            assertThat(result.externalRevenue).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(result.internalCost).isEqualByComparingTo(new BigDecimal("6000"));
            assertThat(result.grossProfit).isEqualByComparingTo(new BigDecimal("4000"));
            assertThat(result.expectedHours).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        @DisplayName("单一元素：等价于 calculate")
        void shouldMatchCalculateForSingleItem() {
            List<DualRateProfitCalculator.BlendedInput> items = List.of(
                    DualRateProfitCalculator.BlendedInput.of(
                            "A", new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("10")));

            DualRateProfitCalculator.ProfitResult blended = DualRateProfitCalculator.calculateBlended(items);
            DualRateProfitCalculator.ProfitResult single = DualRateProfitCalculator.calculate(
                    new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("10"));

            assertThat(blended.externalRevenue).isEqualByComparingTo(single.externalRevenue);
            assertThat(blended.internalCost).isEqualByComparingTo(single.internalCost);
            assertThat(blended.grossProfit).isEqualByComparingTo(single.grossProfit);
            assertThat(blended.grossMargin).isEqualByComparingTo(single.grossMargin);
            assertThat(blended.expectedHours).isEqualByComparingTo(single.expectedHours);
            assertThat(blended.blendedRate).isEqualByComparingTo(single.blendedRate);
        }
    }

    @Nested
    @DisplayName("利润率达成判断 marginAchieved")
    class MarginAchievedCases {

        @Test
        @DisplayName("实际大于目标：达成")
        void shouldReturnTrueWhenActualGreater() {
            boolean achieved = DualRateProfitCalculator.marginAchieved(
                    new BigDecimal("0.4"),
                    new BigDecimal("0.3"));

            assertThat(achieved).isTrue();
        }

        @Test
        @DisplayName("实际小于目标：未达成")
        void shouldReturnFalseWhenActualLess() {
            boolean achieved = DualRateProfitCalculator.marginAchieved(
                    new BigDecimal("0.2"),
                    new BigDecimal("0.3"));

            assertThat(achieved).isFalse();
        }

        @Test
        @DisplayName("实际等于目标：达成")
        void shouldReturnTrueWhenEqual() {
            boolean achieved = DualRateProfitCalculator.marginAchieved(
                    new BigDecimal("0.3"),
                    new BigDecimal("0.3"));

            assertThat(achieved).isTrue();
        }

        @Test
        @DisplayName("actual 为 null：返回 false")
        void shouldReturnFalseWhenActualNull() {
            boolean achieved = DualRateProfitCalculator.marginAchieved(
                    null,
                    new BigDecimal("0.3"));

            assertThat(achieved).isFalse();
        }

        @Test
        @DisplayName("target 为 null：返回 false")
        void shouldReturnFalseWhenTargetNull() {
            boolean achieved = DualRateProfitCalculator.marginAchieved(
                    new BigDecimal("0.4"),
                    null);

            assertThat(achieved).isFalse();
        }
    }
}
