package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * RuleVersion 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVersionVO {

    /** id */
    private String id;

    /** ruleCode */
    private String ruleCode;

    /** version */
    private int version;

    /** definitionJson */
    private String definitionJson;

    /** changeDesc */
    private String changeDesc;

    /** operator */
    private String operator;

    /** createdAt */
    private LocalDateTime createdAt;

}
