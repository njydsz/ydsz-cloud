package com.njydsz.literule.domain.vo;

import java.util.Map;

import lombok.Data;

/**
 * RuleEngineStats 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleEngineStatsVO {

    /** totalEvaluations */
    private long totalEvaluations;

    /** totalTriggered */
    private long totalTriggered;

    /** totalErrors */
    private long totalErrors;

    /** totalElapsedMs */
    private long totalElapsedMs;

    /** registeredRules */
    private int registeredRules;

    /** lastEvaluatedRules */
    private int lastEvaluatedRules;

    /** perRuleStats */
    private Map<String, RuleStat> perRuleStats;

    /** executions */
    private long executions;

    /** triggered */
    private long triggered;

    /** errors */
    private long errors;

    /** totalElapsedMs */
    private long totalElapsedMs;

}
