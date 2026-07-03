package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.ProfitSnapshotDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("利润核算引擎测试")
class ProfitCalculatorTest {

    @Test
    @DisplayName("毛利率计算 - 正常场景")
    void shouldCalculateGrossMargin() {
        BigDecimal margin = ProfitCalculator.grossMargin(
                new BigDecimal("300000"), new BigDecimal("1000000"));
        assertEquals(new BigDecimal("0.3000"), margin);
    }

    @Test
    @DisplayName("毛利率计算 - 收入为 0 返回 0")
    void shouldReturnZeroWhenRevenueIsZero() {
        BigDecimal margin = ProfitCalculator.grossMargin(
                new BigDecimal("100000"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.0000"), margin);
    }

    @Test
    @DisplayName("汇总成本列表")
    void shouldSumCosts() {
        List<BigDecimal> costs = List.of(
                new BigDecimal("10000"),
                new BigDecimal("20000"),
                new BigDecimal("15000"));
        BigDecimal total = ProfitCalculator.totalCost(costs);
        assertEquals(new BigDecimal("45000"), total);
    }

    @Test
    @DisplayName("汇总成本列表 - null 列表返回 0")
    void shouldReturnZeroWhenCostsIsNull() {
        BigDecimal total = ProfitCalculator.totalCost(null);
        assertEquals(BigDecimal.ZERO, total);
    }

    @Test
    @DisplayName("计算毛利 - 收入减成本")
    void shouldCalculateGrossProfit() {
        BigDecimal profit = ProfitCalculator.grossProfit(
                new BigDecimal("1000000"), new BigDecimal("700000"));
        assertEquals(new BigDecimal("300000"), profit);
    }

    @Test
    @DisplayName("EAC 完工估算计算")
    void shouldCalculateEac() {
        BigDecimal eac = ProfitCalculator.eac(
                new BigDecimal("500000"), new BigDecimal("50"));
        assertEquals(new BigDecimal("1000000.00"), eac);
    }

    @Test
    @DisplayName("EAC - 进度为 0 时返回总成本")
    void shouldReturnTotalCostWhenProgressIsZero() {
        BigDecimal eac = ProfitCalculator.eac(
                new BigDecimal("500000"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("500000"), eac);
    }

    @Test
    @DisplayName("项目健康度评分 - 满分场景")
    void shouldReturnFullHealthScore() {
        int score = ProfitCalculator.healthScore(
                new BigDecimal("0.25"), // 毛利率 >= 20%
                new BigDecimal("100"),  // 计划进度
                new BigDecimal("100"),  // 实际进度 >= 计划
                new BigDecimal("100000"), // 计划成本
                new BigDecimal("90000")); // 实际成本 <= 计划
        assertTrue(score >= 90, "健康度评分应接近满分，实际：" + score);
    }

    @Test
    @DisplayName("项目健康度评分 - 差场景")
    void shouldReturnLowHealthScore() {
        int score = ProfitCalculator.healthScore(
                new BigDecimal("-0.10"), // 负毛利率
                new BigDecimal("100"),
                new BigDecimal("30"),     // 实际进度远低于计划
                new BigDecimal("100000"),
                new BigDecimal("200000")); // 实际成本远超计划
        assertTrue(score < 50, "健康度评分应较低，实际：" + score);
    }

    @Test
    @DisplayName("回填快照派生字段")
    void shouldFillDerivedFields() {
        ProfitSnapshotDO snap = new ProfitSnapshotDO();
        snap.setRecognizedRevenue(new BigDecimal("1000000"));
        snap.setLaborCost(new BigDecimal("300000"));
        snap.setPurchaseCost(new BigDecimal("200000"));

        ProfitSnapshotDO result = ProfitCalculator.fillDerived(snap);
        assertNotNull(result);
        assertEquals(new BigDecimal("500000"), result.getTotalCost());
        assertEquals(new BigDecimal("500000"), result.getGrossProfit());
        assertEquals(new BigDecimal("0.5000"), result.getGrossMargin());
    }

    @Test
    @DisplayName("回填快照 - null 入参返回 null")
    void shouldReturnNullWhenSnapIsNull() {
        assertNull(ProfitCalculator.fillDerived(null));
    }
}