package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * 表达式校验结果视图对象（VO）。
 *
 * <p>用于前端展示表达式语法/语义校验结果，包含是否通过、错误类型与精确的位置
 * （行/列），辅助业务人员定位并修正表达式错误。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ExpressionValidationResultVO {

    /** 是否校验通过（true=合法可保存） */
    private boolean valid;

    /** 错误类型（如 SYNTAX_ERROR / UNDEFINED_VARIABLE / TYPE_MISMATCH） */
    private String errorType;

    /** 错误描述（中文说明） */
    private String errorMessage;

    /** 错误所在行号（从 1 开始，无错误为 0） */
    private int errorLine;

    /** 错误所在列号（从 1 开始，无错误为 0） */
    private int errorColumn;

    /** 被校验的表达式原文 */
    private String expression;

    /** 解析耗时（毫秒，用于性能评估） */
    private long parseTimeMs;

}
