package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * RuleVersionDiff 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVersionDiffVO {

    /** oldVersion */
    private int oldVersion;

    /** newVersion */
    private int newVersion;

    /** ruleCode */
    private String ruleCode;

    /** entries */
    private List<Object> entries;

    /** summary */
    private String summary;

    /** type */
    private String type;

    /** field */
    private String field;

    /** fieldLabel */
    private String fieldLabel;

    /** oldValue */
    private String oldValue;

    /** newValue */
    private String newValue;

}
