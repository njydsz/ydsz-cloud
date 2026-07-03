package com.njydsz.pmis.iam.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Bench 闲置成本计算器
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class BenchCostCalculator {

    /** 培训转岗最大允许天数（超过则按闲置计） */
    public static final int TRAINING_MAX_DAYS = 30;

    /**
     * 计算闲置天数（入池到出池或当前）
     *
     * @param benchDate 入池日期
     * @param exitDate  出池日期（未出池传 null，按当前日期计算）
     * @return 闲置天数；入池日期为 null 或出池早于入池时返回 0
     */
    public static int idleDays(LocalDate benchDate, LocalDate exitDate) {
        if (benchDate == null) return 0;
        LocalDate to = exitDate != null ? exitDate : LocalDate.now();
        if (to.isBefore(benchDate)) return 0;
        return (int) ChronoUnit.DAYS.between(benchDate, to);
    }

    /**
     * 计算累计闲置成本
     *
     * @param dailyCost 每日成本
     * @param idleDays  闲置天数
     * @return 累计闲置成本（保留 2 位小数）
     */
    public static BigDecimal totalIdleCost(BigDecimal dailyCost, int idleDays) {
        if (dailyCost == null) dailyCost = BigDecimal.ZERO;
        return dailyCost.multiply(new BigDecimal(idleDays)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 培训期是否仍在可接受窗口内
     *
     * @param benchDate 入池日期
     * @return 闲置天数未超过 {@value #TRAINING_MAX_DAYS} 天返回 true
     */
    public static boolean withinTrainingWindow(LocalDate benchDate) {
        if (benchDate == null) return false;
        return idleDays(benchDate, LocalDate.now()) <= TRAINING_MAX_DAYS;
    }
}
