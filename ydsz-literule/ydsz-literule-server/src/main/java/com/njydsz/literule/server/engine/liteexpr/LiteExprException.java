package com.njydsz.literule.server.expr.liteexpr;

import com.njydsz.common.exception.custom.BusinessException;

/**
 * LiteExpr 表达式引擎异常
 *
 * <p>词法/语法分析阶段抛出，携带精确的行列位置信息，
 * 供 {@link com.njydsz.literule.server.expr.ExpressionValidationResult} 渲染错误位置。
 *
 * <p>继承 {@link BusinessException}，纳入 common-exception 统一异常体系。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class LiteExprException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** 错误所在行（1-based） */
    private final int line;
    /** 错误所在列（1-based） */
    private final int column;

    public LiteExprException(String message, int line, int column) {
        super();
        this.setMessage(message + " (line " + line + ":" + column + ")");
        this.line = line;
        this.column = column;
    }

    public LiteExprException(String message, int line, int column, Throwable cause) {
        super();
        this.setMessage(message + " (line " + line + ":" + column + ")");
        this.line = line;
        this.column = column;
        this.initCause(cause);
    }

    public int getLine() { return line; }
    public int getColumn() { return column; }
}
