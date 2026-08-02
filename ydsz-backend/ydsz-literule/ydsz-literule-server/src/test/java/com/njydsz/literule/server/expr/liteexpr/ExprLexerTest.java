package com.njydsz.literule.server.expr.liteexpr;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ExprLexer} 词法分析器单元测试：覆盖字面量、关键字、运算符、
 * 模板字符串、注释、Unicode 标识符、错误处理等核心词法场景。
 *
 * <p>词法分析器是 LiteExpr 表达式引擎的第一阶段，正确性直接决定后续解析与求值。
 * 本测试聚焦：Token 类型识别、字面值解析、位置信息准确性、异常场景。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@DisplayName("LiteExpr 词法分析器 ExprLexer 测试")
class ExprLexerTest {

    /** 工具方法：取首个非 EOF Token */
    private Token firstToken(String source) {
        List<Token> tokens = new ExprLexer(source).tokenize();
        return tokens.stream()
                .filter(t -> t.type() != TokenType.EOF)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未生成任何 Token: " + source));
    }

    /** 工具方法：取全部非 EOF Token */
    private List<Token> tokensOf(String source) {
        return new ExprLexer(source).tokenize().stream()
                .filter(t -> t.type() != TokenType.EOF)
                .toList();
    }

    @Nested
    @DisplayName("数字字面量")
    class NumberLiterals {

        @Test
        @DisplayName("十进制整数解析为 Integer")
        void shouldParseDecimalInteger() {
            Token token = firstToken("42");
            assertThat(token.type()).isEqualTo(TokenType.INTEGER);
            assertThat(token.literal()).isEqualTo(42);
            assertThat(token.lexeme()).isEqualTo("42");
        }

        @Test
        @DisplayName("long 后缀 L 解析为 Long")
        void shouldParseLongSuffix() {
            Token token = firstToken("100L");
            assertThat(token.type()).isEqualTo(TokenType.INTEGER);
            assertThat(token.literal()).isEqualTo(100L);
        }

        @Test
        @DisplayName("long 后缀小写 l 解析为 Long")
        void shouldParseLongLowerSuffix() {
            Token token = firstToken("999l");
            assertThat(token.type()).isEqualTo(TokenType.INTEGER);
            assertThat(token.literal()).isEqualTo(999L);
        }

        @Test
        @DisplayName("int 溢出时降级为 Long")
        void shouldFallbackToLongWhenIntOverflow() {
            Token token = firstToken("99999999999");
            assertThat(token.type()).isEqualTo(TokenType.INTEGER);
            assertThat(token.literal()).isInstanceOf(Long.class);
            assertThat(token.literal()).isEqualTo(99999999999L);
        }

        @Test
        @DisplayName("十六进制 0x 前缀解析为 Integer")
        void shouldParseHexInteger() {
            Token token = firstToken("0xFF");
            assertThat(token.type()).isEqualTo(TokenType.INTEGER);
            assertThat(token.literal()).isEqualTo(255);
        }

        @Test
        @DisplayName("十六进制大写 0X 前缀解析为 Integer")
        void shouldParseHexUpperCasePrefix() {
            Token token = firstToken("0X1A");
            assertThat(token.type()).isEqualTo(TokenType.INTEGER);
            assertThat(token.literal()).isEqualTo(26);
        }

        @Test
        @DisplayName("十六进制 + L 后缀解析为 Long")
        void shouldParseHexLong() {
            Token token = firstToken("0xDEADBEEFL");
            assertThat(token.type()).isEqualTo(TokenType.INTEGER);
            assertThat(token.literal()).isEqualTo(0xDEADBEEFL);
        }

        @Test
        @DisplayName("小数解析为 DECIMAL 类型 BigDecimal 字面值")
        void shouldParseDecimalNumber() {
            Token token = firstToken("3.14");
            assertThat(token.type()).isEqualTo(TokenType.DECIMAL);
            assertThat(token.literal()).isInstanceOf(BigDecimal.class);
            assertThat(token.literal()).isEqualTo(new BigDecimal("3.14"));
        }

        @Test
        @DisplayName("科学计数法解析为 DECIMAL")
        void shouldParseScientificNotation() {
            Token token = firstToken("1e-5");
            assertThat(token.type()).isEqualTo(TokenType.DECIMAL);
            assertThat(token.literal()).isInstanceOf(BigDecimal.class);
        }

        @Test
        @DisplayName("科学计数法大写 E + 正号")
        void shouldParseScientificUpperCase() {
            Token token = firstToken("2.5E+3");
            assertThat(token.type()).isEqualTo(TokenType.DECIMAL);
            assertThat(token.literal()).isEqualTo(new BigDecimal("2.5E+3"));
        }

        @Test
        @DisplayName("BD 后缀强制解析为 BigDecimal")
        void shouldParseBigDecimalSuffix() {
            Token token = firstToken("100BD");
            assertThat(token.type()).isEqualTo(TokenType.DECIMAL);
            assertThat(token.literal()).isInstanceOf(BigDecimal.class);
            assertThat(token.literal()).isEqualTo(new BigDecimal("100"));
        }

        @Test
        @DisplayName("bd 小写后缀也支持")
        void shouldParseBigDecimalLowerSuffix() {
            Token token = firstToken("3.14bd");
            assertThat(token.type()).isEqualTo(TokenType.DECIMAL);
            assertThat(token.literal()).isEqualTo(new BigDecimal("3.14"));
        }
    }

    @Nested
    @DisplayName("字符串与模板字符串")
    class StringLiterals {

        @Test
        @DisplayName("双引号字符串")
        void shouldParseDoubleQuotedString() {
            Token token = firstToken("\"hello\"");
            assertThat(token.type()).isEqualTo(TokenType.STRING);
            assertThat(token.literal()).isEqualTo("hello");
        }

        @Test
        @DisplayName("单引号字符串")
        void shouldParseSingleQuotedString() {
            Token token = firstToken("'world'");
            assertThat(token.type()).isEqualTo(TokenType.STRING);
            assertThat(token.literal()).isEqualTo("world");
        }

        @Test
        @DisplayName("字符串转义序列：\\n \\t \\\\ \\\"")
        void shouldParseEscapeSequences() {
            Token token = firstToken("\"a\\nb\\t c\\\\d\\\"e\"");
            assertThat(token.literal()).isEqualTo("a\nb\t c\\d\"e");
        }

        @Test
        @DisplayName("未闭合字符串抛出 LiteExprException")
        void shouldThrowOnUnclosedString() {
            ExprLexer lexer = new ExprLexer("\"unclosed");
            assertThatThrownBy(lexer::tokenize)
                    .isInstanceOf(LiteExprException.class)
                    .hasMessageContaining("字符串未闭合");
        }

        @Test
        @DisplayName("字符串跨行抛出异常并提示使用模板字符串")
        void shouldThrowOnMultilineString() {
            ExprLexer lexer = new ExprLexer("\"line1\nline2\"");
            assertThatThrownBy(lexer::tokenize)
                    .isInstanceOf(LiteExprException.class)
                    .hasMessageContaining("字符串不能跨行");
        }

        @Test
        @DisplayName("模板字符串 `Hello` 解析为 TEMPLATE_STR + 反引号边界")
        void shouldParseTemplateString() {
            List<Token> tokens = tokensOf("`Hello`");
            assertThat(tokens).hasSize(3);
            assertThat(tokens.get(0).type()).isEqualTo(TokenType.TEMPLATE_STR);
            assertThat(tokens.get(0).literal()).isEqualTo("Hello");
            // 第二个 TEMPLATE_STR 是反引号边界（空文本时 lexeme 为 `）
            assertThat(tokens.get(1).type()).isEqualTo(TokenType.TEMPLATE_STR);
        }

        @Test
        @DisplayName("模板字符串变量插值 ${name} 拆分为多 Token")
        void shouldParseTemplateVarInterpolation() {
            List<Token> tokens = tokensOf("`Hello ${name}!`");
            // 期望: TEMPLATE_STR("Hello "), TEMPLATE_VAR_START, IDENTIFIER(name), TEMPLATE_VAR_END, TEMPLATE_STR("!")
            assertThat(tokens).isNotEmpty();
            assertThat(tokens).anyMatch(t -> t.type() == TokenType.TEMPLATE_VAR_START);
            assertThat(tokens).anyMatch(t -> t.type() == TokenType.IDENTIFIER && t.lexeme().equals("name"));
            assertThat(tokens).anyMatch(t -> t.type() == TokenType.TEMPLATE_VAR_END);
        }
    }

    @Nested
    @DisplayName("关键字与标识符")
    class KeywordsAndIdentifiers {

        @Test
        @DisplayName("true / false 解析为 BOOLEAN")
        void shouldParseBooleanKeywords() {
            Token trueToken = firstToken("true");
            assertThat(trueToken.type()).isEqualTo(TokenType.BOOLEAN);
            assertThat(trueToken.literal()).isEqualTo(Boolean.TRUE);

            Token falseToken = firstToken("false");
            assertThat(falseToken.type()).isEqualTo(TokenType.BOOLEAN);
            assertThat(falseToken.literal()).isEqualTo(Boolean.FALSE);
        }

        @Test
        @DisplayName("null / nil 解析为 NULL")
        void shouldParseNullKeywords() {
            Token nullToken = firstToken("null");
            assertThat(nullToken.type()).isEqualTo(TokenType.NULL);
            assertThat(nullToken.literal()).isNull();

            Token nilToken = firstToken("nil");
            assertThat(nilToken.type()).isEqualTo(TokenType.NULL);
        }

        @Test
        @DisplayName("and / AND / && 三种形式都解析为 AND")
        void shouldParseAndKeywordVariants() {
            assertThat(firstToken("and").type()).isEqualTo(TokenType.AND);
            assertThat(firstToken("AND").type()).isEqualTo(TokenType.AND);
            assertThat(firstToken("&&").type()).isEqualTo(TokenType.AND);
        }

        @Test
        @DisplayName("or / OR / || 三种形式都解析为 OR")
        void shouldParseOrKeywordVariants() {
            assertThat(firstToken("or").type()).isEqualTo(TokenType.OR);
            assertThat(firstToken("OR").type()).isEqualTo(TokenType.OR);
            assertThat(firstToken("||").type()).isEqualTo(TokenType.OR);
        }

        @Test
        @DisplayName("not / NOT / ! 三种形式都解析为 NOT")
        void shouldParseNotKeywordVariants() {
            assertThat(firstToken("not").type()).isEqualTo(TokenType.NOT);
            assertThat(firstToken("NOT").type()).isEqualTo(TokenType.NOT);
            assertThat(firstToken("!").type()).isEqualTo(TokenType.NOT);
        }

        @Test
        @DisplayName("Unicode 中文标识符")
        void shouldParseChineseIdentifier() {
            Token token = firstToken("用户名");
            assertThat(token.type()).isEqualTo(TokenType.IDENTIFIER);
            assertThat(token.lexeme()).isEqualTo("用户名");
        }

        @Test
        @DisplayName("下划线开头的标识符")
        void shouldParseUnderscoreIdentifier() {
            Token token = firstToken("_privateField");
            assertThat(token.type()).isEqualTo(TokenType.IDENTIFIER);
            assertThat(token.lexeme()).isEqualTo("_privateField");
        }

        @Test
        @DisplayName("$ 开头的标识符")
        void shouldParseDollarIdentifier() {
            Token token = firstToken("$var");
            assertThat(token.type()).isEqualTo(TokenType.IDENTIFIER);
            assertThat(token.lexeme()).isEqualTo("$var");
        }
    }

    @Nested
    @DisplayName("运算符与分隔符")
    class OperatorsAndDelimiters {

        @Test
        @DisplayName("双字符运算符 == != >= <= && || ->")
        void shouldParseDoubleCharOperators() {
            assertThat(firstToken("==").type()).isEqualTo(TokenType.EQ);
            assertThat(firstToken("!=").type()).isEqualTo(TokenType.NEQ);
            assertThat(firstToken(">=").type()).isEqualTo(TokenType.GTE);
            assertThat(firstToken("<=").type()).isEqualTo(TokenType.LTE);
            assertThat(firstToken("&&").type()).isEqualTo(TokenType.AND);
            assertThat(firstToken("||").type()).isEqualTo(TokenType.OR);
            assertThat(firstToken("->").type()).isEqualTo(TokenType.ARROW);
        }

        @Test
        @DisplayName("单字符运算符 + - * / % > < !")
        void shouldParseSingleCharOperators() {
            assertThat(firstToken("+").type()).isEqualTo(TokenType.PLUS);
            assertThat(firstToken("-").type()).isEqualTo(TokenType.MINUS);
            assertThat(firstToken("*").type()).isEqualTo(TokenType.STAR);
            assertThat(firstToken("/").type()).isEqualTo(TokenType.SLASH);
            assertThat(firstToken("%").type()).isEqualTo(TokenType.PERCENT);
            assertThat(firstToken(">").type()).isEqualTo(TokenType.GT);
            assertThat(firstToken("<").type()).isEqualTo(TokenType.LT);
            assertThat(firstToken("!").type()).isEqualTo(TokenType.NOT);
        }

        @Test
        @DisplayName("分隔符 ( ) [ ] { } , . : ?")
        void shouldParseDelimiters() {
            assertThat(firstToken("(").type()).isEqualTo(TokenType.LPAREN);
            assertThat(firstToken(")").type()).isEqualTo(TokenType.RPAREN);
            assertThat(firstToken("[").type()).isEqualTo(TokenType.LBRACKET);
            assertThat(firstToken("]").type()).isEqualTo(TokenType.RBRACKET);
            assertThat(firstToken("{").type()).isEqualTo(TokenType.LBRACE);
            assertThat(firstToken("}").type()).isEqualTo(TokenType.RBRACE);
            assertThat(firstToken(",").type()).isEqualTo(TokenType.COMMA);
            assertThat(firstToken(".").type()).isEqualTo(TokenType.DOT);
            assertThat(firstToken(":").type()).isEqualTo(TokenType.COLON);
            assertThat(firstToken("?").type()).isEqualTo(TokenType.QUESTION);
        }

        @Test
        @DisplayName("无法识别的字符抛出 LiteExprException 携带 ASCII 码")
        void shouldThrowOnUnknownChar() {
            ExprLexer lexer = new ExprLexer("@");
            assertThatThrownBy(lexer::tokenize)
                    .isInstanceOf(LiteExprException.class)
                    .hasMessageContaining("无法识别的字符")
                    .hasMessageContaining("ASCII=64");
        }
    }

    @Nested
    @DisplayName("注释与空白")
    class CommentsAndWhitespace {

        @Test
        @DisplayName("行注释 // 被跳过")
        void shouldSkipLineComment() {
            List<Token> tokens = tokensOf("// 这是注释\n42");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(TokenType.INTEGER);
        }

        @Test
        @DisplayName("块注释 /* */ 被跳过")
        void shouldSkipBlockComment() {
            List<Token> tokens = tokensOf("/* 块注释 */ 42");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).literal()).isEqualTo(42);
        }

        @Test
        @DisplayName("多行块注释跨行跳过")
        void shouldSkipMultilineBlockComment() {
            List<Token> tokens = tokensOf("/* 第一行\n第二行\n第三行 */ true");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(TokenType.BOOLEAN);
        }

        @Test
        @DisplayName("空字符串源代码只产生 EOF")
        void shouldProduceOnlyEofForEmptySource() {
            List<Token> all = new ExprLexer("").tokenize();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).type()).isEqualTo(TokenType.EOF);
        }

        @Test
        @DisplayName("null 源代码被当作空字符串处理")
        void shouldTreatNullAsEmpty() {
            List<Token> all = new ExprLexer(null).tokenize();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).type()).isEqualTo(TokenType.EOF);
        }

        @Test
        @DisplayName("纯空白符只产生 EOF")
        void shouldProduceOnlyEofForWhitespace() {
            List<Token> all = new ExprLexer("   \t\n  \r\n").tokenize();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).type()).isEqualTo(TokenType.EOF);
        }
    }

    @Nested
    @DisplayName("位置信息")
    class PositionTracking {

        @Test
        @DisplayName("首行首列 Token 起始位置为 (1,1)")
        void shouldTrackFirstTokenPosition() {
            Token token = firstToken("42");
            assertThat(token.line()).isEqualTo(1);
            assertThat(token.column()).isEqualTo(1);
            assertThat(token.offset()).isEqualTo(0);
        }

        @Test
        @DisplayName("换行后行号递增、列号重置")
        void shouldTrackPositionAfterNewline() {
            List<Token> tokens = tokensOf("// 注释\n  42");
            Token number = tokens.get(0);
            assertThat(number.line()).isEqualTo(2);
            assertThat(number.column()).isEqualTo(3);
        }

        @Test
        @DisplayName("多 Token 序列的列号累进")
        void shouldTrackProgressiveColumns() {
            List<Token> tokens = tokensOf("a + b");
            assertThat(tokens).hasSize(3);
            assertThat(tokens.get(0).column()).isEqualTo(1); // a
            assertThat(tokens.get(1).column()).isEqualTo(3); // +
            assertThat(tokens.get(2).column()).isEqualTo(5); // b
        }
    }

    @Nested
    @DisplayName("复杂表达式词法扫描")
    class ComplexExpressions {

        @Test
        @DisplayName("算术表达式 a + 1 * 2.5")
        void shouldTokenizeArithmeticExpression() {
            List<Token> tokens = tokensOf("a + 1 * 2.5");
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(TokenType.IDENTIFIER, TokenType.PLUS,
                            TokenType.INTEGER, TokenType.STAR, TokenType.DECIMAL);
        }

        @Test
        @DisplayName("比较与逻辑表达式 age >= 18 and active")
        void shouldTokenizeComparisonAndLogic() {
            List<Token> tokens = tokensOf("age >= 18 and active");
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(TokenType.IDENTIFIER, TokenType.GTE,
                            TokenType.INTEGER, TokenType.AND, TokenType.IDENTIFIER);
        }

        @Test
        @DisplayName("函数调用 max(a, b)")
        void shouldTokenizeFunctionCall() {
            List<Token> tokens = tokensOf("max(a, b)");
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(TokenType.IDENTIFIER, TokenType.LPAREN,
                            TokenType.IDENTIFIER, TokenType.COMMA, TokenType.IDENTIFIER,
                            TokenType.RPAREN);
        }

        @Test
        @DisplayName("三元表达式 cond ? 1 : 0")
        void shouldTokenizeTernary() {
            List<Token> tokens = tokensOf("cond ? 1 : 0");
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(TokenType.IDENTIFIER, TokenType.QUESTION,
                            TokenType.INTEGER, TokenType.COLON, TokenType.INTEGER);
        }

        @Test
        @DisplayName("Lambda 表达式 x -> x + 1")
        void shouldTokenizeLambda() {
            List<Token> tokens = tokensOf("x -> x + 1");
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(TokenType.IDENTIFIER, TokenType.ARROW,
                            TokenType.IDENTIFIER, TokenType.PLUS, TokenType.INTEGER);
        }

        @Test
        @DisplayName("列表字面量 [1, 2, 3]")
        void shouldTokenizeListLiteral() {
            List<Token> tokens = tokensOf("[1, 2, 3]");
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(TokenType.LBRACKET, TokenType.INTEGER, TokenType.COMMA,
                            TokenType.INTEGER, TokenType.COMMA, TokenType.INTEGER, TokenType.RBRACKET);
        }

        @Test
        @DisplayName("属性访问 a.b.c")
        void shouldTokenizeMemberAccess() {
            List<Token> tokens = tokensOf("a.b.c");
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(TokenType.IDENTIFIER, TokenType.DOT,
                            TokenType.IDENTIFIER, TokenType.DOT, TokenType.IDENTIFIER);
        }
    }
}
