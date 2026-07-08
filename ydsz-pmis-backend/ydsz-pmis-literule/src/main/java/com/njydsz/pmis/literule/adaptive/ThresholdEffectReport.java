package com.njydsz.pmis.literule.adaptive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阈值应用效果报告（2.0.0 自适应阈值闭环）
 *
 * <p>记录阈值调整前后的触发率变化，用于评估自适应阈值的效果。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdEffectReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规则编码 */
    private String ruleCode;

    /** 变量名 */
    private String variable;

    /** 旧阈值 */
    private double oldThreshold;

    /** 新阈值 */
    private double newThreshold;

    /** 应用时间 */
    private String appliedAt;

    /** 效果评估时间 */
    private String effectEvaluatedAt;

    /** 基线触发率（应用前） */
    private double baselineTriggerRate;

    /** 当前触发率（应用后） */
    private double currentTriggerRate;

    /** 触发率变化（current - baseline） */
    private double triggerRateDelta;

    /** 基线样本量 */
    private int baselineSampleSize;

    /** 当前样本量 */
    private int currentSampleSize;

    /** 效果等级：POSITIVE / NEUTRAL / NEEDS_REVIEW */
    private String effectLevel;
}
