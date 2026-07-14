package com.njydsz.pmis.finance.server.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.finance.domain.entity.ProfitSnapshotDO;

/**
 * {@link ProfitCalculator} 单元测试
 *
 * <p>覆盖利润核算引擎的全部核心计算方法，包括边界值与 null 安全。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("利润核算引擎 ProfitCalculator")
class ProfitCalculatorTest {

    @Nested
    @DisplayName("grossMargin 毛利率")
    class GrossMarginTest {

        @Test
        @DisplayName("正常毛利率计算")
        void shouldCalculateGrossMargin() {
            BigDecimal result = ProfitCalculator.grossMargin(
                    new BigDecimal("30"), new BigDecimal("100"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("0.3000"));
        }

        @Test
        @DisplayName("收入为零时返回 0")
        void shouldReturnZeroWhenRevenueIsZero() {
            BigDecimal result = ProfitCalculator.grossMargin(
                    new BigDecimal("30"), BigDecimal.ZERO);
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("参数为 null 时返回 0")
        void shouldReturnZeroWhenNull() {
            assertThat(ProfitCalculator.grossMargin(null, new BigDecimal("100")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(ProfitCalculator.grossMargin(new BigDecimal("30"), null))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("负毛利场景")
        void shouldHandleNegativeProfit() {
            BigDecimal result = ProfitCalculator.grossMargin(
                    new BigDecimal("-20"), new BigDecimal("100"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("-0.2000"));
        }
    }

    @Nested
    @DisplayName("totalCost 成本汇总")
    class TotalCostTest {

        @Test
        @DisplayName("正常列表求和")
        void shouldSumCosts() {
            BigDecimal result = ProfitCalculator.totalCost(
                    List.of(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30")));
            assertThat(result).isEqualByComparingTo(new BigDecimal("60"));
        }

        @Test
        @DisplayName("列表包含 null 元素时跳过")
        void shouldSkipNullElements() {
            BigDecimal result = ProfitCalculator.totalCost(
                    Arrays.asList(new BigDecimal("10"), null, new BigDecimal("30")));
            assertThat(result).isEqualByComparingTo(new BigDecimal("40"));
        }

        @Test
        @DisplayName("null 列表返回 0")
        void shouldReturnZeroForNullList() {
            assertThat(ProfitCalculator.totalCost(null))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("空列表返回 0")
        void shouldReturnZeroForEmptyList() {
            assertThat(ProfitCalculator.totalCost(List.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("grossProfit 毛利")
    class GrossProfitTest {

        @Test
        @DisplayName("正常毛利计算")
        void shouldCalculateGrossProfit() {
            BigDecimal result = ProfitCalculator.grossProfit(
                    new BigDecimal("100"), new BigDecimal("60"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("40"));
        }

        @Test
        @DisplayName("revenue 为 null 时按 0 处理")
        void shouldHandleNullRevenue() {
            BigDecimal result = ProfitCalculator.grossProfit(null, new BigDecimal("60"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("-60"));
        }

        @Test
        @DisplayName("cost 为 null 时按 0 处理")
        void shouldHandleNullCost() {
            BigDecimal result = ProfitCalculator.grossProfit(new BigDecimal("100"), null);
            assertThat(result).isEqualByComparingTo(new BigDecimal("100"));
        }

        @Test
        @DisplayName("两者均 null 时返回 0")
        void shouldReturnZeroWhenBothNull() {
            assertThat(ProfitCalculator.grossProfit(null, null))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("eac 完工估算")
    class EacTest {

        @Test
        @DisplayName("正常 EAC 计算：totalCost / progressPct")
        void shouldCalculateEac() {
            // 80 / (40/100) = 80 / 0.4 = 200.00
            BigDecimal result = ProfitCalculator.eac(
                    new BigDecimal("80"), new BigDecimal("40"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("进度为 0 时返回 totalCost")
        void shouldReturnTotalCostWhenProgressIsZero() {
            BigDecimal result = ProfitCalculator.eac(
                    new BigDecimal("80"), BigDecimal.ZERO);
            assertThat(result).isEqualByComparingTo(new BigDecimal("80"));
        }

        @Test
        @DisplayName("进度为 null 时返回 totalCost")
        void shouldReturnTotalCostWhenProgressIsNull() {
            BigDecimal result = ProfitCalculator.eac(new BigDecimal("80"), null);
            assertThat(result).isEqualByComparingTo(new BigDecimal("80"));
        }

        @Test
        @DisplayName("totalCost 为 null 时按 0 处理")
        void shouldHandleNullTotalCost() {
            BigDecimal result = ProfitCalculator.eac(null, new BigDecimal("40"));
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }

    @Nested
    @DisplayName("healthScore 健康度评分")
    class HealthScoreTest {

        @Test
        @DisplayName("满分场景：毛利率>=20% + 进度达标 + 成本达标")
        void shouldReturnFullScore() {
            // margin=0.3(>=0.2 →50) + actual/plan=100/100=1(→30) + plan/actual=100/100=1(→20) = 100
            int score = ProfitCalculator.healthScore(
                    new BigDecimal("0.30"),
                    new BigDecimal("100"), new BigDecimal("100"),
                    new BigDecimal("100"), new BigDecimal("100"));
            assertThat(score).isEqualTo(100);
        }

        @Test
        @DisplayName("低毛利率但仍>0 时线性得分")
        void shouldLinearScoreForLowMargin() {
            // margin=0.10(0-20% 线性 → 0.10/0.20*50=25) + 进度满 30 + 成本满 20 = 75
            int score = ProfitCalculator.healthScore(
                    new BigDecimal("0.10"),
                    new BigDecimal("100"), new BigDecimal("100"),
                    new BigDecimal("100"), new BigDecimal("100"));
            assertThat(score).isEqualTo(75);
        }

        @Test
        @DisplayName("负毛利率不得分")
        void shouldZeroScoreForNegativeMargin() {
            // margin=-0.5(→0) + 进度满 30 + 成本满 20 = 50
            int score = ProfitCalculator.healthScore(
                    new BigDecimal("-0.50"),
                    new BigDecimal("100"), new BigDecimal("100"),
                    new BigDecimal("100"), new BigDecimal("100"));
            assertThat(score).isEqualTo(50);
        }

        @Test
        @DisplayName("进度偏差大时扣分")
        void shouldPenalizeScheduleDelay() {
            // margin=0.3(→50) + actual/plan=50/100=0.5(→15) + 成本满 20 = 85
            int score = ProfitCalculator.healthScore(
                    new BigDecimal("0.30"),
                    new BigDecimal("100"), new BigDecimal("50"),
                    new BigDecimal("100"), new BigDecimal("100"));
            assertThat(score).isEqualTo(85);
        }

        @Test
        @DisplayName("成本超支时扣分")
        void shouldPenalizeCostOverrun() {
            // margin=0.3(→50) + 进度满 30 + plan/actual=100/200=0.5(→10) = 90
            int score = ProfitCalculator.healthScore(
                    new BigDecimal("0.30"),
                    new BigDecimal("100"), new BigDecimal("100"),
                    new BigDecimal("100"), new BigDecimal("200"));
            assertThat(score).isEqualTo(90);
        }

        @Test
        @DisplayName("全部 null 参数使用默认中间分")
        void shouldUseDefaultsWhenAllNull() {
            // margin=null(→0) + 进度 null(→15) + 成本 null(→10) = 25
            int score = ProfitCalculator.healthScore(null, null, null, null, null);
            assertThat(score).isEqualTo(25);
        }

        @Test
        @DisplayName("评分限制在 0-100 范围内")
        void shouldClampToRange() {
            // 极端高毛利率不超 100
            int score = ProfitCalculator.healthScore(
                    new BigDecimal("1.00"),
                    new BigDecimal("10"), new BigDecimal("100"),
                    new BigDecimal("100"), new BigDecimal("1"));
            assertThat(score).isBetween(0, 100);
        }
    }

    @Nested
    @DisplayName("fillDerived 回填派生字段")
    class FillDerivedTest {

        @Test
        @DisplayName("正常回填 totalCost/grossProfit/grossMargin")
        void shouldFillDerivedFields() {
            ProfitSnapshotDO snap = new ProfitSnapshotDO();
            snap.setRecognizedRevenue(new BigDecimal("100"));
            snap.setLaborCost(new BigDecimal("20"));
            snap.setPurchaseCost(new BigDecimal("10"));
            snap.setExpenseCost(new BigDecimal("5"));
            snap.setOutsourceCost(new BigDecimal("5"));
            snap.setAllocationCost(new BigDecimal("10"));

            ProfitCalculator.fillDerived(snap);

            // totalCost = 20+10+5+5+10 = 50
            assertThat(snap.getTotalCost()).isEqualByComparingTo(new BigDecimal("50"));
            // grossProfit = 100 - 50 = 50
            assertThat(snap.getGrossProfit()).isEqualByComparingTo(new BigDecimal("50"));
            // grossMargin = 50/100 = 0.5000
            assertThat(snap.getGrossMargin()).isEqualByComparingTo(new BigDecimal("0.5000"));
        }

        @Test
        @DisplayName("null 成本字段按 0 处理")
        void shouldHandleNullCostFields() {
            ProfitSnapshotDO snap = new ProfitSnapshotDO();
            snap.setRecognizedRevenue(new BigDecimal("100"));
            // 只设置 laborCost，其他为 null
            snap.setLaborCost(new BigDecimal("30"));

            ProfitCalculator.fillDerived(snap);

            assertThat(snap.getTotalCost()).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(snap.getGrossProfit()).isEqualByComparingTo(new BigDecimal("70"));
            assertThat(snap.getGrossMargin()).isEqualByComparingTo(new BigDecimal("0.7000"));
        }

        @Test
        @DisplayName("入参为 null 时返回 null")
        void shouldReturnNullForNullInput() {
            assertThat(ProfitCalculator.fillDerived(null)).isNull();
        }
    }
}
