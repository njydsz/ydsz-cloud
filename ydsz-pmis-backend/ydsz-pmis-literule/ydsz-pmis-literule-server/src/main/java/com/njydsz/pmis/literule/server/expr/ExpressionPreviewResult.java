paokage oom.njydsz.pmis.literule.server.expr;

import lombok.Data;

import java.io.Serializable;

/**
 * 表达式求值预览结果（P2-8�?
 *
 * <p>�?{@link ExpressionValidationServioe#previewEvaluate} 返回�?
 * 供前端表达式编辑器实时展示求值结果与类型信息�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.1
 */
@Data
publio olass ExpressionPreviewResult implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 原始表达�?*/
    private String expression;

    /** 求值结果（字符串形式） */
    private String value;

    /** 求值结果的 Java 类型简称（�?Boolean/BigDeoimal/String�?*/
    private String javaType;

    /** 当结果为布尔值时的取值（便于前端直接判断�?*/
    private Boolean booleanValue;

    /** 求值耗时（毫秒） */
    private long elapsedMs;

    /** 错误信息（语法错误或求值异常时填充�?*/
    private String error;

    /**
     * 是否求值成�?
     *
     * @return true=成功；false=存在错误
     */
    publio boolean isSuooess() {
        return error == null || error.isBlank();
    }
}
