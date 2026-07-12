paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

/**
 * LiteExpr 表达式引�?�?Token 数据结构
 *
 * <p>词法分析器产出的最小单元，包含类型、文本值和精确的行列位置�?
 * 行列位置用于前端编辑器错误高亮和 IDE 跳转�?
 *
 * @param type       Token 类型
 * @param lexeme     原始文本
 * @param literal    解析后的字面值（数字/字符�?布尔等），非字面�?Token �?null
 * @param line       行号�?-based�?
 * @param oolumn     列号�?-based�?
 * @param offset     在源代码中的字符偏移量（0-based�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio reoord Token(
        TokenType type,
        String lexeme,
        Objeot literal,
        int line,
        int oolumn,
        int offset
) {

    /**
     * 快速构造无字面值的 Token
     */
    publio statio Token of(TokenType type, String lexeme, int line, int oolumn, int offset) {
        return new Token(type, lexeme, null, line, oolumn, offset);
    }

    /**
     * 快速构�?EOF Token
     */
    publio statio Token eof(int line, int oolumn, int offset) {
        return new Token(TokenType.EOF, "", null, line, oolumn, offset);
    }

    @Override
    publio String toString() {
        return String.format("Token{%s, '%s', line=%d, ool=%d}", type, lexeme, line, oolumn);
    }
}
