package com.njydsz.pmis.execution.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 双费率利润计算引擎
 *
 * <p>对外报价（Rate Card）× 投入人时 = 测算收入；
 * 对内成本费率 × 投入人时 = 内部成本；
 * 毛利率 = (测算收入 - 内部成本) / 测算收入。
 *
 * <p>支持混合职级加权平均计算。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class DualRateProfitCalculator {

    /**
     * 按单一职级计算
     *
     * @param externalRate 对外日/人时报价
     * @param internalCost 对内日/人时成本
     * @param hours        投入人时（或人天）
     * @return 利润测算结果
     */
    public static ProfitResult calculate(BigDecimal externalRate, BigDecimal internalCost, BigDecimal hours) {
        BigDecimal extN = nz(externalRate);
        BigDecimal costN = nz(internalCost);
        BigDecimal hoursN = nz(hours);
        BigDecimal rev = extN.multiply(hoursN).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = costN.multiply(hoursN).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = rev.subtract(cost);
        BigDecimal margin = rev.signum() == 0
                ? BigDecimal.ZERO
                : profit.divide(rev, 4, RoundingMode.HALF_UP);
        ProfitResult r = new ProfitResult();
        r.externalRevenue = rev;
        r.internalCost = cost;
        r.grossProfit = profit;
        r.grossMargin = margin;
        r.expectedHours = hoursN;
        r.blendedRate = rev.signum() == 0
                ? BigDecimal.ZERO
                : rev.divide(hoursN, 2, RoundingMode.HALF_UP);
        return r;
    }

    /**
     * 按混合职级加权计算
     *
     * @param items (职级/对外费率/对内成本/投入人时) 列表
     * @return 利润测算结果
     */
    public static ProfitResult calculateBlended(List<BlendedInput> items) {
        if (items == null || items.isEmpty()) {
            ProfitResult empty = new ProfitResult();
            empty.externalRevenue = BigDecimal.ZERO;
            empty.internalCost = BigDecimal.ZERO;
            empty.grossProfit = BigDecimal.ZERO;
            empty.grossMargin = BigDecimal.ZERO;
            empty.expectedHours = BigDecimal.ZERO;
            empty.blendedRate = BigDecimal.ZERO;
            return empty;
        }
        BigDecimal revSum = BigDecimal.ZERO;
        BigDecimal costSum = BigDecimal.ZERO;
        BigDecimal hourSum = BigDecimal.ZERO;
        for (BlendedInput it : items) {
            if (it == null) continue;
            BigDecimal h = nz(it.hours);
            revSum = revSum.add(nz(it.externalRate).multiply(h));
            costSum = costSum.add(nz(it.internalCost).multiply(h));
            hourSum = hourSum.add(h);
        }
        ProfitResult r = new ProfitResult();
        r.expectedHours = hourSum;
        r.externalRevenue = revSum.setScale(2, RoundingMode.HALF_UP);
        r.internalCost = costSum.setScale(2, RoundingMode.HALF_UP);
        r.grossProfit = r.externalRevenue.subtract(r.internalCost);
        r.grossMargin = r.externalRevenue.signum() == 0
                ? BigDecimal.ZERO
                : r.grossProfit.divide(r.externalRevenue, 4, RoundingMode.HALF_UP);
        r.blendedRate = hourSum.signum() == 0
                ? BigDecimal.ZERO
                : r.externalRevenue.divide(hourSum, 2, RoundingMode.HALF_UP);
        return r;
    }

    /**
     * 利润达成判断：实际毛利率 vs 目标毛利率
     */
    public static boolean marginAchieved(BigDecimal actual, BigDecimal target) {
        if (actual == null || target == null) return false;
        return actual.compareTo(target) >= 0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 混合输入
     */
    public static class BlendedInput {
        public String levelCode;
        public BigDecimal externalRate;
        public BigDecimal internalCost;
        public BigDecimal hours;

        public static BlendedInput of(String level, BigDecimal ext, BigDecimal cost, BigDecimal hours) {
            BlendedInput b = new BlendedInput();
            b.levelCode = level;
            b.externalRate = ext;
            b.internalCost = cost;
            b.hours = hours;
            return b;
        }
    }

    /**
     * 利润测算结果
     */
    public static class ProfitResult {
        public BigDecimal expectedHours;
        public BigDecimal externalRevenue;
        public BigDecimal internalCost;
        public BigDecimal grossProfit;
        public BigDecimal grossMargin;
        public BigDecimal blendedRate;
    }
}
