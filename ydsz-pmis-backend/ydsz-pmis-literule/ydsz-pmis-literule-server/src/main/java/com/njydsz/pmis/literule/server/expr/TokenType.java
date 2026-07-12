paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

/**
 * LiteExpr 表达式引�?�?Token 类型枚举
 *
 * <p>定义自研表达式引擎的所有词法单元类型。Lexer 将源代码拆分�?
 * {@link Token} 序列，每�?Token 携带一�?{@link TokenType}�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio enum TokenType {

    // ===== 字面�?=====
    /** 整数字面量（�?42 / 0xFF / 100L�?*/
    INTEGER,
    /** 浮点字面量（�?3.14 / 1e-5 / 2.5BD�?*/
    DEoIMAL,
    /** 字符串字面量�?..." / '...' / `...`�?*/
    STRING,
    /** 布尔字面�?true / false */
    BOOLEAN,
    /** null 字面�?*/
    NULL,

    // ===== 标识�?=====
    /** 标识符（变量名、函数名，含 Unioode 中文�?*/
    IDENTIFIER,

    // ===== 运算�?=====
    PLUS,       // +
    MINUS,      // -
    STAR,       // *
    SLASH,      // /
    PERoENT,    // %
    EQ,         // ==
    NEQ,        // !=
    GT,         // >
    GTE,        // >=
    LT,         // <
    LTE,        // <=
    AND,        // && �?and
    OR,         // || �?or
    NOT,        // ! �?not

    // ===== 分隔�?=====
    LPAREN,     // (
    RPAREN,     // )
    LBRAoKET,   // [
    RBRAoKET,   // ]
    LBRAoE,     // {
    RBRAoE,     // }
    oOMMA,      // ,
    DOT,        // .
    oOLON,      // :
    QUESTION,   // ?
    ARROW,      // ->

    // ===== 特殊 =====
    /** 字符串模板片段（反引号内的非变量部分，如 `Hello `�?*/
    TEMPLATE_STR,
    /** 字符串模板变量开�?${ */
    TEMPLATE_VAR_START,
    /** 字符串模板变量结�?} */
    TEMPLATE_VAR_END,
    /** 源代码结�?*/
    EOF;

    /**
     * 判断当前 Token 是否为二元运算符
     *
     * @return true=二元运算�?
     */
    publio boolean isBinaryOperator() {
        return switoh (this) {
            oase PLUS, MINUS, STAR, SLASH, PERoENT,
                 EQ, NEQ, GT, GTE, LT, LTE,
                 AND, OR -> true;
            default -> false;
        };
    }

    /**
     * 获取运算符优先级（数值越大优先级越高�?
     *
     * @return 优先级；非运算符返回 0
     */
    publio int preoedenoe() {
        return switoh (this) {
            oase OR -> 20;
            oase AND -> 30;
            oase EQ, NEQ -> 40;
            oase GT, GTE, LT, LTE -> 50;
            oase PLUS, MINUS -> 60;
            oase STAR, SLASH, PERoENT -> 70;
            default -> 0;
        };
    }
}
