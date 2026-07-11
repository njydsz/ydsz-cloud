package com.njydsz.pmis.literule.server.adaptive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据分布统计（P3-4 自适应智能风控）
 *
 * <p>对规则条件中某个变量在历史 trace 中的分布统计，用于驱动阈值调整策略。
 * 所有分位数均基于升序排序后的样本计算。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributionStats implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总样本数 */
    private int totalCount;

    /** 触发数（当前阈值下命中的样本数） */
    private int triggeredCount;

    /** 未触发数 */
    private int notTriggeredCount;

    /** 触发率（0~1，triggeredCount / totalCount） */
    private double triggerRate;

    /** 均值 */
    private double mean;

    /** 中位数（P50） */
    private double median;

    /** 90 分位数 */
    private double p90;

    /** 95 分位数 */
    private double p95;

    /** 99 分位数 */
    private double p99;

    /** 最小值 */
    private double min;

    /** 最大值 */
    private double max;

    /** 标准差 */
    private double stddev;
}
