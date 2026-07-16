package com.njydsz.userinfo.server.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 资源利用率计算器
 *
 * <p>Billable Utilization = 已计费人时 / 投入人时 × 100%
 *
 * <p>过载判断：同时参与项目数 ≥ 3 → 过载
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class UtilizationCalculator {

    /** 过载阈值：同时参与活跃项目数 */
    public static final int OVERLOAD_PROJECT_THRESHOLD = 3;

    /** 健康利用率下限 */
    public static final BigDecimal HEALTHY_UTILIZATION = new BigDecimal("0.60");

    /**
     * 计算计费利用率
     *
     * @param billableHours 已计费人时
     * @param totalHours    投入人时
     * @return 计费利用率（0-1，保留 4 位小数）；投入人时为 0 时返回 0
     */
    public static BigDecimal billableUtilization(BigDecimal billableHours, BigDecimal totalHours) {
        if (billableHours == null) billableHours = BigDecimal.ZERO;
        if (totalHours == null || totalHours.signum() == 0) return BigDecimal.ZERO;
        return billableHours.divide(totalHours, 4, RoundingMode.HALF_UP);
    }

    /**
     * 是否过载
     *
     * @param activeProjectCount 活跃项目数
     * @return 达到过载阈值返回 true
     */
    public static boolean isOverloaded(int activeProjectCount) {
        return activeProjectCount >= OVERLOAD_PROJECT_THRESHOLD;
    }

    /**
     * 利用率健康度评级
     * <ul>
     *   <li>&lt; 60% LOW</li>
     *   <li>60%~85% NORMAL</li>
     *   <li>≥ 85% HIGH</li>
     * </ul>
     *
     * @param utilization 计费利用率
     * @return 评级 LOW/NORMAL/HIGH
     */
    public static String utilizationLevel(BigDecimal utilization) {
        if (utilization == null) return "LOW";
        if (utilization.compareTo(HEALTHY_UTILIZATION) < 0) return "LOW";
        if (utilization.compareTo(new BigDecimal("0.85")) < 0) return "NORMAL";
        return "HIGH";
    }
}
