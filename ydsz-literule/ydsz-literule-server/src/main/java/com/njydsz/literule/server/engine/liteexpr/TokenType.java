package com.njydsz.literule.server.engine.liteexpr;

/**
 * LiteExpr 表达式引擎 — Token 类型枚举
 *
 * <p>定义自研表达式引擎的所有词法单元类型。Lexer 将源代码拆分为
 * {@link Token} 序列，每个 Token 携带一个 {@link TokenType}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public enum TokenType {

    // ===== 字面量 =====
    /** 整数字面量（如 42 / 0xFF / 100L） */
    INTEGER,
    /** 浮点字面量（如 3.14 / 1e-5 / 2.5BD） */
    DECIMAL,
    /** 字符串字面量（"..." / '...' / `...`） */
    STRING,
    /** 布尔字面量 true / false */
    BOOLEAN,
    /** null 字面量 */
    NULL,

    // ===== 标识符 =====
    /** 标识符（变量名、函数名，含 Unicode 中文） */
    IDENTIFIER,

    // ===== 运算符 =====
    PLUS,       // +
    MINUS,      // -
    STAR,       // *
    SLASH,      // /
    PERCENT,    // %
    EQ,         // ==
    NEQ,        // !=
    GT,         // >
    GTE,        // >=
    LT,         // <
    LTE,        // <=
    AND,        // && 或 and
    OR,         // || 或 or
    NOT,        // ! 或 not

    // ===== 分隔符 =====
    LPAREN,     // (
    RPAREN,     // )
    LBRACKET,   // [
    RBRACKET,   // ]
    LBRACE,     // {
    RBRACE,     // }
    COMMA,      // ,
    DOT,        // .
    COLON,      // :
    QUESTION,   // ?
    ARROW,      // ->

    // ===== 特殊 =====
    /** 字符串模板片段（反引号内的非变量部分，如 `Hello `） */
    TEMPLATE_STR,
    /** 字符串模板变量开始 ${ */
    TEMPLATE_VAR_START,
    /** 字符串模板变量结束 } */
    TEMPLATE_VAR_END,
    /** 源代码结束 */
    EOF;

    /**
     * 判断当前 Token 是否为二元运算符
     *
     * @return true=二元运算符
     */
    public boolean isBinaryOperator() {
        return switch (this) {
            case PLUS, MINUS, STAR, SLASH, PERCENT,
                 EQ, NEQ, GT, GTE, LT, LTE,
                 AND, OR -> true;
            default -> false;
        };
    }

    /**
     * 获取运算符优先级（数值越大优先级越高）
     *
     * @return 优先级；非运算符返回 0
     */
    public int precedence() {
        return switch (this) {
            case OR -> 20;
            case AND -> 30;
            case EQ, NEQ -> 40;
            case GT, GTE, LT, LTE -> 50;
            case PLUS, MINUS -> 60;
            case STAR, SLASH, PERCENT -> 70;
            default -> 0;
        };
    }
}
