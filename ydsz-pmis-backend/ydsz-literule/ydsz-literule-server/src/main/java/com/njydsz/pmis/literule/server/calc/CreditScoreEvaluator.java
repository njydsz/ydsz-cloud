package com.njydsz.literule.server.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.extern.slf4j.Slf4j;

/**
 * 客户信用评分引擎（零依赖纯计算组件，从 execution 模块迁移至 literule）
 *
 * <p>评分公式（满分 100）：
 * <ul>
 *   <li>回款及时率 60 pts：及时回款占比 * 60</li>
 *   <li>合同规模 25 pts：log10(累计合同金额 / 1万 + 1) / log10(1000+1) * 25，封顶 25</li>
 *   <li>合作次数 15 pts：min(合作合同数, 10) / 10 * 15</li>
 *   <li>逾期扣分：每次 -5，最低 0</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
public class CreditScoreEvaluator {

    /** log10(1001) 常量，用于合同规模得分归一化 */
    private static final BigDecimal LOG_1001;

    static {
        LOG_1001 = BigDecimal.valueOf(Math.log10(1001));
    }

    /**
     * 计算客户信用分
     *
     * @param onTimeRate         回款及时率（0-1）
     * @param totalContractAmount 累计合同金额
     * @param contractCount      合作合同数
     * @param overdueCount       逾期次数
     * @return 信用分（0-100）
     */
    public static int score(BigDecimal onTimeRate, BigDecimal totalContractAmount,
                            int contractCount, int overdueCount) {
        if (onTimeRate == null) onTimeRate = BigDecimal.ZERO;
        if (totalContractAmount == null) totalContractAmount = BigDecimal.ZERO;

        // 新客户（无合作历史）默认 A 级，附加 30 pts 基础分（信任起步）
        int base = (contractCount == 0 && totalContractAmount.signum() == 0
                && overdueCount == 0) ? 30 : 0;

        // 1) 及时率 60 pts
        BigDecimal timely = onTimeRate.multiply(new BigDecimal(60))
                .setScale(2, RoundingMode.HALF_UP);

        // 2) 合同规模 25 pts
        double amount = totalContractAmount.doubleValue();
        double logValue = Math.log10(amount / 10000.0 + 1.0);
        double ratio = Math.min(1.0, logValue / LOG_1001.doubleValue());
        BigDecimal scale = BigDecimal.valueOf(ratio * 25).setScale(2, RoundingMode.HALF_UP);

        // 3) 合作次数 15 pts
        double cntRatio = Math.min(1.0, contractCount / 10.0);
        BigDecimal times = BigDecimal.valueOf(cntRatio * 15).setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = BigDecimal.valueOf(base).add(timely).add(scale).add(times);

        // 4) 逾期扣分
        int penalty = Math.min(overdueCount, 12) * 5;
        int result = total.subtract(BigDecimal.valueOf(penalty)).intValue();
        if (result < 0) result = 0;
        if (result > 100) result = 100;
        return result;
    }
}
