package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * RuleConflictInfo 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleConflictInfoVO {

    /** ruleA */
    private String ruleA;

    /** ruleAName */
    private String ruleAName;

    /** ruleB */
    private String ruleB;

    /** ruleBName */
    private String ruleBName;

    /** overlapFields */
    private List<String> overlapFields;

    /** severity */
    private String severity;

}
