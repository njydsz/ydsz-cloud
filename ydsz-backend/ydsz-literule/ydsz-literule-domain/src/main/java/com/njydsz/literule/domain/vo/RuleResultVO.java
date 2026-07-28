package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * RuleResult 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleResultVO {

    /** ruleCode */
    private String ruleCode;

    /** ruleName */
    private String ruleName;

    /** category */
    private String category;

    /** triggered */
    private boolean triggered;

    /** severity */
    private String severity;

    /** title */
    private String title;

    /** description */
    private String description;

    /** currentValue */
    private String currentValue;

    /** threshold */
    private String threshold;

    /** scope */
    private String scope;

    /** triggeredAt */
    private LocalDateTime triggeredAt;

    /** drilldownAvailable */
    private Boolean drilldownAvailable;

    /** elapsedMs */
    private long elapsedMs;

    /** canaryBucket */
    private String canaryBucket;

}
