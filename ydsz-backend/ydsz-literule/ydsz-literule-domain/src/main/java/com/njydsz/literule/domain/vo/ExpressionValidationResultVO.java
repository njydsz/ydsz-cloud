package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * ExpressionValidationResult 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ExpressionValidationResultVO {

    /** valid */
    private boolean valid;

    /** errorType */
    private ErrorType errorType;

    /** errorMessage */
    private String errorMessage;

    /** errorLine */
    private int errorLine;

    /** errorColumn */
    private int errorColumn;

    /** expression */
    private String expression;

    /** parseTimeMs */
    private long parseTimeMs;

}
