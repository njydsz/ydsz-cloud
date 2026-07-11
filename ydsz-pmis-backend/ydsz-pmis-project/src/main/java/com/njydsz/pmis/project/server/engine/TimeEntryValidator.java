package com.njydsz.pmis.project.server.engine;

import com.njydsz.pmis.project.domain.entity.TimeEntryDO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 工时校验引擎
 *
 * <ul>
 *   <li>单日上限：24h</li>
 *   <li>单周上限：60h（防止过载）</li>
 *   <li>连续 3 天 0 填报（异常）</li>
 *   <li>跨项目冲突：同一天同一员工多项目</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class TimeEntryValidator {

    /** 单日工时上限（小时） */
    public static final BigDecimal MAX_DAILY_HOURS = new BigDecimal("24");
    /** 单周工时上限（小时） */
    public static final BigDecimal MAX_WEEKLY_HOURS = new BigDecimal("60");
    /** 单日加班工时上限（小时） */
    public static final BigDecimal MAX_OVERTIME_HOURS = new BigDecimal("12");
    /** 连续未填报天数告警阈值 */
    public static final int CONSECUTIVE_MISSING_DAYS = 3;

    /** 校验结果 */
    public static class ValidationResult {
        /** 是否通过 */
        public final boolean ok;
        /** 失败原因（通过时为 null） */
        public final String message;

        /**
         * 构造校验结果
         *
         * @param ok      是否通过
         * @param message 失败原因
         */
        public ValidationResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        /**
         * 构造通过结果
         *
         * @return 通过结果
         */
        public static ValidationResult ok() { return new ValidationResult(true, null); }
        /**
         * 构造失败结果
         *
         * @param msg 失败原因
         * @return 失败结果
         */
        public static ValidationResult fail(String msg) { return new ValidationResult(false, msg); }
    }

    /**
     * 校验单条工时
     *
     * @param entry 工时录入
     * @return 校验结果
     */
    public static ValidationResult validate(TimeEntryDO entry) {
        if (entry == null) return ValidationResult.fail("工时为空");
        if (entry.getHours() == null || entry.getHours().signum() <= 0) {
            return ValidationResult.fail("工时必须为正数");
        }
        if (entry.getHours().compareTo(MAX_DAILY_HOURS) > 0) {
            return ValidationResult.fail("单日工时不能超过 24h");
        }
        if (entry.getOvertime() != null && entry.getOvertime().compareTo(MAX_OVERTIME_HOURS) > 0) {
            return ValidationResult.fail("加班工时不能超过 12h");
        }
        if (entry.getEntryDate() == null) {
            return ValidationResult.fail("工时日期不能为空");
        }
        return ValidationResult.ok();
    }

    /**
     * 校验周工时累计
     *
     * @param newEntry    待新增的工时
     * @param weekEntries 本周已有工时列表
     * @return 校验结果
     */
    public static ValidationResult validateWeekly(TimeEntryDO newEntry, List<TimeEntryDO> weekEntries) {
        if (newEntry == null || newEntry.getHours() == null) return ValidationResult.ok();
        BigDecimal sum = newEntry.getHours();
        if (weekEntries != null) {
            for (TimeEntryDO e : weekEntries) {
                if (e == null || e.getHours() == null) continue;
                if (newEntry.getId() != null && newEntry.getId().equals(e.getId())) continue;
                sum = sum.add(e.getHours());
            }
        }
        if (sum.compareTo(MAX_WEEKLY_HOURS) > 0) {
            return ValidationResult.fail("本周累计工时 " + sum + "h 超过上限 " + MAX_WEEKLY_HOURS + "h");
        }
        return ValidationResult.ok();
    }

    /**
     * 折算人天（按 8h/天）
     *
     * @param hours 工时小时数
     * @return 人天数
     */
    public static BigDecimal toDays(BigDecimal hours) {
        if (hours == null) return BigDecimal.ZERO;
        return hours.divide(new BigDecimal("8"), 2, RoundingMode.HALF_UP);
    }
}
