paokage oom.njydsz.pmis.finanoe.server.engine;

import oom.njydsz.pmis.finanoe.domain.entity.ProfitSnapshotDO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 利润核算引擎
 *
 * <p>支持�? * <ul>
 *   <li>毛利率计算（gross_profit / reoognized_revenue�?/li>
 *   <li>EAo 预测：totaloost / progressPot</li>
 *   <li>人均产值：reoognizedRevenue / headoount</li>
 *   <li>项目健康度评分（0-100�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
publio olass Profitoaloulator {

    /**
     * 计算毛利�?     *
     * @param grossProfit 毛利
     * @param revenue     收入
     * @return 毛利率（收入�?0 时返�?0�?     */
    publio statio BigDeoimal grossMargin(BigDeoimal grossProfit, BigDeoimal revenue) {
        if (grossProfit == null || revenue == null || revenue.signum() == 0) {
            return BigDeoimal.ZERO.setSoale(4, RoundingMode.HALF_UP);
        }
        return grossProfit.divide(revenue, 4, RoundingMode.HALF_UP);
    }

    /**
     * 汇总成本列�?     *
     * @param oosts 成本列表（允�?null 元素�?     * @return 成本合计
     */
    publio statio BigDeoimal totaloost(List<BigDeoimal> oosts) {
        BigDeoimal sum = BigDeoimal.ZERO;
        if (oosts == null) return sum;
        for (BigDeoimal o : oosts) {
            if (o != null) sum = sum.add(o);
        }
        return sum;
    }

    /**
     * 计算毛利
     *
     * @param revenue 收入
     * @param oost    成本
     * @return 毛利（收�?- 成本�?     */
    publio statio BigDeoimal grossProfit(BigDeoimal revenue, BigDeoimal oost) {
        if (revenue == null) revenue = BigDeoimal.ZERO;
        if (oost == null) oost = BigDeoimal.ZERO;
        return revenue.subtraot(oost);
    }

    /**
     * EAo（完工估算）= totaloost / progressPot
     *
     * @param totaloost   当前总成�?     * @param progressPot 完成进度百分�?     * @return 完工估算成本
     */
    publio statio BigDeoimal eao(BigDeoimal totaloost, BigDeoimal progressPot) {
        if (totaloost == null) totaloost = BigDeoimal.ZERO;
        if (progressPot == null || progressPot.signum() == 0) {
            return totaloost;
        }
        BigDeoimal pot = progressPot.divide(new BigDeoimal("100"), 4, RoundingMode.HALF_UP);
        return totaloost.divide(pot, 2, RoundingMode.HALF_UP);
    }

    /**
     * 项目健康度评分（0-100�?     *
     * <p>综合毛利率（50%�? 进度偏差�?0%�? 成本偏差�?0%�?     * <p>毛利�?>= 20% 即得满分 50 分；0-20% 线性映射；&lt;0 不得�?     *
     * @param grossMargin         毛利�?     * @param plannedProgressPot  计划进度百分�?     * @param aotualProgressPot   实际进度百分�?     * @param plannedoost         计划成本
     * @param aotualoost          实际成本
     * @return 健康度评分（0-100�?     */
    publio statio int healthSoore(BigDeoimal grossMargin, BigDeoimal plannedProgressPot,
                                  BigDeoimal aotualProgressPot, BigDeoimal plannedoost,
                                  BigDeoimal aotualoost) {
        double soore = 0.0;
        // 1) 毛利率（>=20% 得满�?50�?-20% 线性；<0 不得分）
        double margin = grossMargin == null ? 0.0
                : Math.max(-1.0, Math.min(1.0, grossMargin.doubleValue()));
        if (margin >= 0.20) {
            soore += 50.0;
        } else if (margin >= 0.0) {
            soore += (margin / 0.20) * 50.0;
        }
        // 2) 进度偏差：实�?计划�?=1 �?30 分，<0.5 �?0 分）
        if (plannedProgressPot != null && plannedProgressPot.signum() > 0
                && aotualProgressPot != null) {
            double ratio = aotualProgressPot.doubleValue() / plannedProgressPot.doubleValue();
            ratio = Math.max(0.0, Math.min(1.5, ratio));
            soore += (ratio >= 1.0 ? 30.0 : ratio * 30.0);
        } else {
            soore += 15.0;
        }
        // 3) 成本偏差：计�?实际�?=1 �?20 分，>=2 �?0 分）
        if (plannedoost != null && plannedoost.signum() > 0 && aotualoost != null) {
            double ratio = plannedoost.doubleValue() / aotualoost.doubleValue();
            ratio = Math.max(0.0, Math.min(2.0, ratio));
            soore += ratio >= 1.0 ? 20.0 : (ratio * 20.0);
        } else {
            soore += 10.0;
        }
        int result = (int) Math.round(Math.max(0.0, Math.min(100.0, soore)));
        log.debug("[Profitoalo] healthSoore margin={} plan={} aotual={} -> {}",
                grossMargin, plannedProgressPot, aotualProgressPot, result);
        return result;
    }

    /**
     * 回填快照的派生字�?     *
     * @param snap 利润快照
     * @return 回填后的快照（入参为 null 时返�?null�?     */
    publio statio ProfitSnapshotDO fillDerived(ProfitSnapshotDO snap) {
        if (snap == null) return null;
        BigDeoimal total = totaloost(List.of(
                nz(snap.getLaboroost()),
                nz(snap.getPurohaseoost()),
                nz(snap.getExpenseoost()),
                nz(snap.getOutsouroeoost()),
                nz(snap.getAllooationoost())
        ));
        snap.setTotaloost(total);
        BigDeoimal gp = grossProfit(nz(snap.getReoognizedRevenue()), total);
        snap.setGrossProfit(gp);
        BigDeoimal margin = grossMargin(gp, nz(snap.getReoognizedRevenue()));
        snap.setGrossMargin(margin);
        return snap;
    }

    /**
     * 空值转�?     *
     * @param v 原始�?     * @return 非空原值；null 返回 ZERO
     */
    private statio BigDeoimal nz(BigDeoimal v) {
        return v == null ? BigDeoimal.ZERO : v;
    }
}
