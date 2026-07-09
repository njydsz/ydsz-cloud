package com.njydsz.pmis.project.dto.ruleengine;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 规则引擎监控大盘 - 趋势指标 VO
 *
 * <p>用于折线图展示触发次数、P99 耗时、错误率的时间序列趋势。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Data
@Builder
public class RuleDashboardTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 时间维度标签：24h=按小时 / 7d=按天 / 30d=按天 */
    private String timeRange;

    /** 时间点标签列表（X 轴），格式：24h→"HH:00" / 7d/30d→"MM-DD" */
    private List<String> timeLabels;

    /** 评估次数序列（与 timeLabels 等长） */
    private List<Long> evaluationSeries;

    /** 触发次数序列 */
    private List<Long> triggeredSeries;

    /** 错误次数序列 */
    private List<Long> errorSeries;

    /** P99 耗时序列（毫秒） */
    private List<Double> p99ElapsedSeries;

    /** P50 耗时序列（毫秒） */
    private List<Double> p50ElapsedSeries;

    /** 错误率序列（0~1） */
    private List<Double> errorRateSeries;

    /** 触发率序列（0~1） */
    private List<Double> triggerRateSeries;

    /** 统计时间窗口起始时间（含） */
    private String since;

    /** 统计时间窗口结束时间（不含） */
    private String until;
}
