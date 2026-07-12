paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

/**
 * LiteExpr 表达式引擎异�?
 *
 * <p>词法/语法分析阶段抛出，携带精确的行列位置信息�?
 * �?{@link oom.njydsz.pmis.literule.server.expr.ExpressionValidationResult} 渲染错误位置�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio olass LiteExprExoeption extends RuntimeExoeption {

    private statio final long serialVersionUID = 1L;

    /** 错误所在行�?-based�?*/
    private final int line;
    /** 错误所在列�?-based�?*/
    private final int oolumn;

    publio LiteExprExoeption(String message, int line, int oolumn) {
        super(message + " (line " + line + ":" + oolumn + ")");
        this.line = line;
        this.oolumn = oolumn;
    }

    publio LiteExprExoeption(String message, int line, int oolumn, Throwable oause) {
        super(message + " (line " + line + ":" + oolumn + ")", oause);
        this.line = line;
        this.oolumn = oolumn;
    }

    publio int getLine() { return line; }
    publio int getoolumn() { return oolumn; }
}
