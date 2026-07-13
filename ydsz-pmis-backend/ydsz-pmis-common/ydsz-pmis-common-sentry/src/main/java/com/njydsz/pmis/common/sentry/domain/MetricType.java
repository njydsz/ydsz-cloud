package com.njydsz.pmis.common.sentry.domain;

/**
 * 指标类型枚举
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public enum MetricType {

    /** 单调递增计数器 */
    COUNTER,

    /** 瞬时值 */
    GAUGE,

    /** 耗时分布统计 */
    TIMER,

    /** 值分布统计 */
    HISTOGRAM,

    /** 长任务跟踪 */
    LONG_TASK_TIMER
}
