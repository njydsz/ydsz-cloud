package com.njydsz.literule.server.engine.liteexpr;

/**
 * LiteExpr 表达式引擎 — Token 数据结构
 *
 * <p>词法分析器产出的最小单元，包含类型、文本值和精确的行列位置。
 * 行列位置用于前端编辑器错误高亮和 IDE 跳转。
 *
 * @param type       Token 类型
 * @param lexeme     原始文本
 * @param literal    解析后的字面值（数字/字符串/布尔等），非字面量 Token 为 null
 * @param line       行号（1-based）
 * @param column     列号（1-based）
 * @param offset     在源代码中的字符偏移量（0-based）
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public record Token(
        TokenType type,
        String lexeme,
        Object literal,
        int line,
        int column,
        int offset
) {

    /**
     * 快速构造无字面值的 Token
     */
    public static Token of(TokenType type, String lexeme, int line, int column, int offset) {
        return new Token(type, lexeme, null, line, column, offset);
    }

    /**
     * 快速构造 EOF Token
     */
    public static Token eof(int line, int column, int offset) {
        return new Token(TokenType.EOF, "", null, line, column, offset);
    }

    @Override
    public String toString() {
        return String.format("Token{%s, '%s', line=%d, col=%d}", type, lexeme, line, column);
    }
}
