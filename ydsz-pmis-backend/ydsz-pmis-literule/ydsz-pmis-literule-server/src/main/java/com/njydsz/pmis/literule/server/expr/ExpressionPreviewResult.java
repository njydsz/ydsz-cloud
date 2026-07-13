package com.njydsz.pmis.literule.server.expr;

import java.io.Serializable;

import lombok.Data;

/**
 * 表达式求值预览结果（P2-8）
 *
 * <p>由 {@link ExpressionValidationService#previewEvaluate} 返回，
 * 供前端表达式编辑器实时展示求值结果与类型信息。
 *
 * @author ydsz-pmis-team
 * @since 1.5.1
 */
@Data
public class ExpressionPreviewResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原始表达式 */
    private String expression;

    /** 求值结果（字符串形式） */
    private String value;

    /** 求值结果的 Java 类型简称（如 Boolean/BigDecimal/String） */
    private String javaType;

    /** 当结果为布尔值时的取值（便于前端直接判断） */
    private Boolean booleanValue;

    /** 求值耗时（毫秒） */
    private long elapsedMs;

    /** 错误信息（语法错误或求值异常时填充） */
    private String error;

    /**
     * 是否求值成功
     *
     * @return true=成功；false=存在错误
     */
    public boolean isSuccess() {
        return error == null || error.isBlank();
    }
}
