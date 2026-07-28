package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * ExpressionPreviewResult 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ExpressionPreviewResultVO {

    /** expression */
    private String expression;

    /** value */
    private String value;

    /** javaType */
    private String javaType;

    /** booleanValue */
    private Boolean booleanValue;

    /** elapsedMs */
    private long elapsedMs;

    /** error */
    private String error;

}
