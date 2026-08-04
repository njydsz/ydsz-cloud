package com.remisoft.literule.domain.vo;

import java.util.Map;

import lombok.Data;

/**
 * 规则引擎运行统计视图对象（VO）。
 *
 * <p>用于前端展示规则引擎的运行时指标，包含累计评估/命中/错误次数、耗时、
 * 已注册规则数及单规则明细，支撑引擎健康度与性能监控。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class RuleEngineStatsVO {

    /** 累计评估次数（全部规则评估的总调用数） */
    private long totalEvaluations;

    /** 累计命中次数 */
    private long totalTriggered;

    /** 累计错误次数（评估抛出异常的次数） */
    private long totalErrors;

    /** 累计评估耗时（毫秒） */
    private long totalElapsedMs;

    /** 当前已注册规则数 */
    private int registeredRules;

    /** 最近一次评估涉及的规则数 */
    private int lastEvaluatedRules;

    /** 各规则统计明细（规则编码 → 统计对象） */
    private Map<String, Object> perRuleStats;

    /** 执行次数（与 totalEvaluations 并行的另一统计口径，用于交叉校验） */
    private long executions;

    /** 命中次数（与 totalTriggered 并行的另一统计口径） */
    private long triggered;

    /** 错误次数（与 totalErrors 并行的另一统计口径） */
    private long errors;

}
