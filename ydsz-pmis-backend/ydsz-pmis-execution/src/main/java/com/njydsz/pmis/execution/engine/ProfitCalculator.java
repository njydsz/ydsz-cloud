package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.entity.ProfitSnapshotDO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 利润核算引擎
 *
 * <p>支持：
 * <ul>
 *   <li>毛利率计算（gross_profit / recognized_revenue）</li>
 *   <li>EAC 预测：totalCost / progressPct</li>
 *   <li>人均产值：recognizedRevenue / headcount</li>
 *   <li>项目健康度评分（0-100）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class ProfitCalculator {

    /**
     * 计算毛利率
     *
     * @param grossProfit 毛利
     * @param revenue     收入
     * @return 毛利率（收入为 0 时返回 0）
     */
    public static BigDecimal grossMargin(BigDecimal grossProfit, BigDecimal revenue) {
        if (grossProfit == null || revenue == null || revenue.signum() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return grossProfit.divide(revenue, 4, RoundingMode.HALF_UP);
    }

    /**
     * 汇总成本列表
     *
     * @param costs 成本列表（允许 null 元素）
     * @return 成本合计
     */
    public static BigDecimal totalCost(List<BigDecimal> costs) {
        BigDecimal sum = BigDecimal.ZERO;
        if (costs == null) return sum;
        for (BigDecimal c : costs) {
            if (c != null) sum = sum.add(c);
        }
        return sum;
    }

    /**
     * 计算毛利
     *
     * @param revenue 收入
     * @param cost    成本
     * @return 毛利（收入 - 成本）
     */
    public static BigDecimal grossProfit(BigDecimal revenue, BigDecimal cost) {
        if (revenue == null) revenue = BigDecimal.ZERO;
        if (cost == null) cost = BigDecimal.ZERO;
        return revenue.subtract(cost);
    }

    /**
     * EAC（完工估算）= totalCost / progressPct
     *
     * @param totalCost   当前总成本
     * @param progressPct 完成进度百分比
     * @return 完工估算成本
     */
    public static BigDecimal eac(BigDecimal totalCost, BigDecimal progressPct) {
        if (totalCost == null) totalCost = BigDecimal.ZERO;
        if (progressPct == null || progressPct.signum() == 0) {
            return totalCost;
        }
        BigDecimal pct = progressPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        return totalCost.divide(pct, 2, RoundingMode.HALF_UP);
    }

    /**
     * 项目健康度评分（0-100）
     *
     * <p>综合毛利率（50%）+ 进度偏差（30%）+ 成本偏差（20%）
     * <p>毛利率 >= 20% 即得满分 50 分；0-20% 线性映射；&lt;0 不得分
     *
     * @param grossMargin         毛利率
     * @param plannedProgressPct  计划进度百分比
     * @param actualProgressPct   实际进度百分比
     * @param plannedCost         计划成本
     * @param actualCost          实际成本
     * @return 健康度评分（0-100）
     */
    public static int healthScore(BigDecimal grossMargin, BigDecimal plannedProgressPct,
                                  BigDecimal actualProgressPct, BigDecimal plannedCost,
                                  BigDecimal actualCost) {
        double score = 0.0;
        // 1) 毛利率（>=20% 得满分 50；0-20% 线性；<0 不得分）
        double margin = grossMargin == null ? 0.0
                : Math.max(-1.0, Math.min(1.0, grossMargin.doubleValue()));
        if (margin >= 0.20) {
            score += 50.0;
        } else if (margin >= 0.0) {
            score += (margin / 0.20) * 50.0;
        }
        // 2) 进度偏差：实际/计划（>=1 得 30 分，<0.5 得 0 分）
        if (plannedProgressPct != null && plannedProgressPct.signum() > 0
                && actualProgressPct != null) {
            double ratio = actualProgressPct.doubleValue() / plannedProgressPct.doubleValue();
            ratio = Math.max(0.0, Math.min(1.5, ratio));
            score += (ratio >= 1.0 ? 30.0 : ratio * 30.0);
        } else {
            score += 15.0;
        }
        // 3) 成本偏差：计划/实际（<=1 得 20 分，>=2 得 0 分）
        if (plannedCost != null && plannedCost.signum() > 0 && actualCost != null) {
            double ratio = plannedCost.doubleValue() / actualCost.doubleValue();
            ratio = Math.max(0.0, Math.min(2.0, ratio));
            score += ratio >= 1.0 ? 20.0 : (ratio * 20.0);
        } else {
            score += 10.0;
        }
        int result = (int) Math.round(Math.max(0.0, Math.min(100.0, score)));
        log.debug("[ProfitCalc] healthScore margin={} plan={} actual={} -> {}",
                grossMargin, plannedProgressPct, actualProgressPct, result);
        return result;
    }

    /**
     * 回填快照的派生字段
     *
     * @param snap 利润快照
     * @return 回填后的快照（入参为 null 时返回 null）
     */
    public static ProfitSnapshotDO fillDerived(ProfitSnapshotDO snap) {
        if (snap == null) return null;
        BigDecimal total = totalCost(List.of(
                nz(snap.getLaborCost()),
                nz(snap.getPurchaseCost()),
                nz(snap.getExpenseCost()),
                nz(snap.getOutsourceCost()),
                nz(snap.getAllocationCost())
        ));
        snap.setTotalCost(total);
        BigDecimal gp = grossProfit(nz(snap.getRecognizedRevenue()), total);
        snap.setGrossProfit(gp);
        BigDecimal margin = grossMargin(gp, nz(snap.getRecognizedRevenue()));
        snap.setGrossMargin(margin);
        return snap;
    }

    /**
     * 空值转零
     *
     * @param v 原始值
     * @return 非空原值；null 返回 ZERO
     */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
