package com.njydsz.literule.server.expr.liteexpr;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * LiteExpr 词法分析器
 *
 * <p>将表达式源代码字符串扫描为 {@link Token} 序列。支持：
 * <ul>
 *   <li>整数（十进制 / 十六进制 0x / 八进制 0 / long 后缀 L/l）</li>
 *   <li>浮点数（小数点 / 科学计数法 / BigDecimal 后缀 BD/bd）</li>
 *   <li>字符串（双引号 / 单引号 / 反引号模板字符串）</li>
 *   <li>布尔值 true/false</li>
 *   <li>null</li>
 *   <li>关键字 and/or/not（等价于 &&/||/!）</li>
 *   <li>行注释 // 和块注释 /* *\/</li>
 *   <li>Unicode 标识符（中文变量名）</li>
 *   <li>箭头运算符 -></li>
 * </ul>
 *
 * <p>每个 Token 携带精确的行列位置，用于前端错误高亮。
 *
 * @since 1.0.0
 */

/**
 * ExprLexer 类。
 *
 * <p>所属包：{@code com.njydsz.literule.server.expr.liteexpr}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExprLexer {

    private final String source;
    private final int length;

    private int pos = 0;
    private int line = 1;
    private int column = 1;

    /** 是否在模板字符串内（反引号） */
    private boolean inTemplate = false;

    public ExprLexer(String source) {
        this.source = source == null ? "" : source;
        this.length = this.source.length();
    }

    /**
     * 扫描全部 Token
     *
     * @return Token 列表（末尾含 EOF）
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>(64);
        while (pos < length) {
            if (inTemplate) {
                tokens.addAll(scanTemplatePart());
            } else {
                Token token = nextToken();
                if (token != null) {
                    tokens.add(token);
                }
            }
        }
        tokens.add(Token.eof(line, column, pos));
        return tokens;
    }

    /**
     * 扫描下一个 Token（跳过空白和注释）
     *
     * @return Token；空白/注释返回 null
     */
    private Token nextToken() {
        skipWhitespaceAndComments();
        if (pos >= length) return null;

        char c = peek();
        int startLine = line;
        int startCol = column;
        int startPos = pos;

        // 字符串
        if (c == '"' || c == '\'') {
            return scanString(c, startLine, startCol, startPos);
        }
        // 模板字符串（反引号）
        if (c == '`') {
            inTemplate = true;
            advance();
            return Token.of(TokenType.TEMPLATE_STR, "`", startLine, startCol, startPos);
        }
        // 数字
        if (Character.isDigit(c)) {
            return scanNumber(startLine, startCol, startPos);
        }
        // 标识符 / 关键字
        if (Character.isLetter(c) || c == '_' || c == '$' || isUnicodeLetter(c)) {
            return scanIdentifier(startLine, startCol, startPos);
        }

        // 运算符 / 分隔符
        return scanOperator(startLine, startCol, startPos);
    }

    /**
     * 扫描模板字符串的一部分
     *
     * <p>模板字符串 `Hello ${name}!` 会被拆分为：
     * TEMPLATE_STR("Hello "), TEMPLATE_VAR_START, IDENTIFIER("name"), TEMPLATE_VAR_END, TEMPLATE_STR("!")
     * 最后的 ` 产生一个 STRING 类型的合并结果（在 Parser 中处理）。
     */
    private List<Token> scanTemplatePart() {
        List<Token> tokens = new ArrayList<>(4);
        int startLine = line;
        int startCol = column;
        int startPos = pos;
        StringBuilder sb = new StringBuilder();

        while (pos < length) {
            char c = peek();
            if (c == '`') {
                // 模板字符串结束
                if (!sb.isEmpty()) {
                    tokens.add(new Token(TokenType.TEMPLATE_STR, sb.toString(), sb.toString(), startLine, startCol, startPos));
                }
                tokens.add(Token.of(TokenType.TEMPLATE_STR, "`", line, column, pos));
                advance();
                inTemplate = false;
                return tokens;
            }
            if (c == '$' && pos + 1 < length && source.charAt(pos + 1) == '{') {
                // 变量插值开始
                if (!sb.isEmpty()) {
                    tokens.add(new Token(TokenType.TEMPLATE_STR, sb.toString(), sb.toString(), startLine, startCol, startPos));
                }
                tokens.add(Token.of(TokenType.TEMPLATE_VAR_START, "${", line, column, pos));
                pos += 2;
                column += 2;
                // 扫描到 } 为止（支持嵌套表达式，简单版只到 }）
                List<Token> innerTokens = scanUntilBrace();
                tokens.addAll(innerTokens);
                continue;
            }
            if (c == '\\') {
                // 转义
                advance();
                if (pos < length) {
                    sb.append(unescapeChar(peek()));
                    advance();
                }
            } else {
                sb.append(c);
                advance();
            }
        }
        // 未闭合的模板字符串
        if (!sb.isEmpty()) {
            tokens.add(new Token(TokenType.TEMPLATE_STR, sb.toString(), sb.toString(), startLine, startCol, startPos));
        }
        return tokens;
    }

    /**
     * 扫描到 } 为止的内部 Token（模板变量内部）
     */
    private List<Token> scanUntilBrace() {
        List<Token> tokens = new ArrayList<>(8);
        int braceDepth = 0;
        while (pos < length) {
            skipWhitespaceAndComments();
            if (pos >= length) break;
            char c = peek();
            if (c == '}' && braceDepth == 0) {
                tokens.add(Token.of(TokenType.TEMPLATE_VAR_END, "}", line, column, pos));
                advance();
                return tokens;
            }
            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            Token token = nextToken();
            if (token != null) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * 扫描字符串字面量
     */
    private Token scanString(char quote, int startLine, int startCol, int startPos) {
        advance(); // 跳过开头引号
        StringBuilder sb = new StringBuilder();
        while (pos < length && peek() != quote) {
            char c = peek();
            if (c == '\\') {
                advance();
                if (pos < length) {
                    sb.append(unescapeChar(peek()));
                    advance();
                }
            } else if (c == '\n') {
                throw new LiteExprException("字符串不能跨行（请使用反引号模板字符串）", startLine, startCol);
            } else {
                sb.append(c);
                advance();
            }
        }
        if (pos >= length) {
            throw new LiteExprException("字符串未闭合，缺少 '" + quote + "'", startLine, startCol);
        }
        advance(); // 跳过结尾引号
        return new Token(TokenType.STRING, sb.toString(), sb.toString(), startLine, startCol, startPos);
    }

    /**
     * 扫描数字字面量
     */
    private Token scanNumber(int startLine, int startCol, int startPos) {
        StringBuilder sb = new StringBuilder();
        boolean isDecimal = false;
        boolean isHex = false;

        // 十六进制
        if (peek() == '0' && pos + 1 < length && (source.charAt(pos + 1) == 'x' || source.charAt(pos + 1) == 'X')) {
            sb.append(peek()); advance();
            sb.append(peek()); advance();
            isHex = true;
            while (pos < length && isHexDigit(peek())) {
                sb.append(peek()); advance();
            }
        } else {
            // 十进制
            while (pos < length && Character.isDigit(peek())) {
                sb.append(peek()); advance();
            }
            // 小数部分
            if (pos < length && peek() == '.') {
                isDecimal = true;
                sb.append(peek()); advance();
                while (pos < length && Character.isDigit(peek())) {
                    sb.append(peek()); advance();
                }
            }
            // 科学计数法
            if (pos < length && (peek() == 'e' || peek() == 'E')) {
                isDecimal = true;
                sb.append(peek()); advance();
                if (pos < length && (peek() == '+' || peek() == '-')) {
                    sb.append(peek()); advance();
                }
                while (pos < length && Character.isDigit(peek())) {
                    sb.append(peek()); advance();
                }
            }
        }

        // 后缀
        boolean isBigDecimal = false;
        boolean isLong = false;
        if (pos < length) {
            // BD / bd → BigDecimal
            if (pos + 1 < length && (source.charAt(pos) == 'B' || source.charAt(pos) == 'b')
                    && (source.charAt(pos + 1) == 'D' || source.charAt(pos + 1) == 'd')) {
                isBigDecimal = true;
                isDecimal = true;
                pos += 2;
                column += 2;
            } else if (peek() == 'L' || peek() == 'l') {
                isLong = true;
                advance();
            }
        }

        String lexeme = sb.toString();
        Object literal;
        if (isBigDecimal) {
            literal = new BigDecimal(lexeme);
        } else if (isHex) {
            literal = isLong ? Long.parseLong(lexeme.substring(2), 16) : Integer.parseInt(lexeme.substring(2), 16);
        } else if (isDecimal) {
            literal = new BigDecimal(lexeme);
        } else if (isLong) {
            literal = Long.parseLong(lexeme);
        } else {
            // 尝试 int，溢出则 long
            try {
                literal = Integer.parseInt(lexeme);
            } catch (NumberFormatException e) {
                literal = Long.parseLong(lexeme);
            }
        }

        TokenType type = isDecimal ? TokenType.DECIMAL : TokenType.INTEGER;
        return new Token(type, lexeme, literal, startLine, startCol, startPos);
    }

    /**
     * 扫描标识符 / 关键字
     */
    private Token scanIdentifier(int startLine, int startCol, int startPos) {
        StringBuilder sb = new StringBuilder();
        while (pos < length && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '$' || isUnicodeLetter(peek()))) {
            sb.append(peek());
            advance();
        }
        String lexeme = sb.toString();

        // 关键字判断
        return switch (lexeme) {
            case "true"  -> new Token(TokenType.BOOLEAN, lexeme, Boolean.TRUE, startLine, startCol, startPos);
            case "false" -> new Token(TokenType.BOOLEAN, lexeme, Boolean.FALSE, startLine, startCol, startPos);
            case "null", "nil" -> new Token(TokenType.NULL, lexeme, null, startLine, startCol, startPos);
            case "and", "AND" -> new Token(TokenType.AND, lexeme, null, startLine, startCol, startPos);
            case "or", "OR"   -> new Token(TokenType.OR, lexeme, null, startLine, startCol, startPos);
            case "not", "NOT" -> new Token(TokenType.NOT, lexeme, null, startLine, startCol, startPos);
            default -> new Token(TokenType.IDENTIFIER, lexeme, null, startLine, startCol, startPos);
        };
    }

    /**
     * 扫描运算符 / 分隔符
     */
    private Token scanOperator(int startLine, int startCol, int startPos) {
        char c = peek();
        char next = (pos + 1 < length) ? source.charAt(pos + 1) : '\0';

        // 双字符运算符
        if (c == '=' && next == '=') { advance(); advance(); return Token.of(TokenType.EQ, "==", startLine, startCol, startPos); }
        if (c == '!' && next == '=') { advance(); advance(); return Token.of(TokenType.NEQ, "!=", startLine, startCol, startPos); }
        if (c == '>' && next == '=') { advance(); advance(); return Token.of(TokenType.GTE, ">=", startLine, startCol, startPos); }
        if (c == '<' && next == '=') { advance(); advance(); return Token.of(TokenType.LTE, "<=", startLine, startCol, startPos); }
        if (c == '&' && next == '&') { advance(); advance(); return Token.of(TokenType.AND, "&&", startLine, startCol, startPos); }
        if (c == '|' && next == '|') { advance(); advance(); return Token.of(TokenType.OR, "||", startLine, startCol, startPos); }
        if (c == '-' && next == '>') { advance(); advance(); return Token.of(TokenType.ARROW, "->", startLine, startCol, startPos); }

        // 单字符运算符
        TokenType type = switch (c) {
            case '+' -> TokenType.PLUS;
            case '-' -> TokenType.MINUS;
            case '*' -> TokenType.STAR;
            case '/' -> TokenType.SLASH;
            case '%' -> TokenType.PERCENT;
            case '>' -> TokenType.GT;
            case '<' -> TokenType.LT;
            case '!' -> TokenType.NOT;
            case '(' -> TokenType.LPAREN;
            case ')' -> TokenType.RPAREN;
            case '[' -> TokenType.LBRACKET;
            case ']' -> TokenType.RBRACKET;
            case '{' -> TokenType.LBRACE;
            case '}' -> TokenType.RBRACE;
            case ',' -> TokenType.COMMA;
            case '.' -> TokenType.DOT;
            case ':' -> TokenType.COLON;
            case '?' -> TokenType.QUESTION;
            default -> throw new LiteExprException("无法识别的字符: '" + c + "' (ASCII=" + (int) c + ")", startLine, startCol);
        };
        advance();
        return Token.of(type, String.valueOf(c), startLine, startCol, startPos);
    }

    // ===== 辅助方法 =====

    private void skipWhitespaceAndComments() {
        while (pos < length) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                advance();
            } else if (c == '/' && pos + 1 < length && source.charAt(pos + 1) == '/') {
                // 行注释
                while (pos < length && peek() != '\n') advance();
            } else if (c == '/' && pos + 1 < length && source.charAt(pos + 1) == '*') {
                // 块注释
                advance(); advance(); // /*
                while (pos < length && !(peek() == '*' && pos + 1 < length && source.charAt(pos + 1) == '/')) {
                    advance();
                }
                if (pos < length) { advance(); advance(); } // */
            } else {
                break;
            }
        }
    }

    private char peek() {
        return source.charAt(pos);
    }

    private void advance() {
        if (pos < length) {
            char c = source.charAt(pos);
            pos++;
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean isUnicodeLetter(char c) {
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private char unescapeChar(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            case '`' -> '`';
            case '0' -> '\0';
            default -> c;
        };
    }
}
