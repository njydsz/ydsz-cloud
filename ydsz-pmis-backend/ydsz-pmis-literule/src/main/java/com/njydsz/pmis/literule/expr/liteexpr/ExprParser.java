package com.njydsz.pmis.literule.expr.liteexpr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LiteExpr 递归下降解析器（Pratt Parser 风格）
 *
 * <p>将 {@link Token} 序列解析为 {@link ExprNode} AST。支持：
 * <ul>
 *   <li>运算符优先级解析（|| → && → ==/!= → 比较 → 加减 → 乘除模 → 一元 → 后缀 → primary）</li>
 *   <li>三元表达式 (cond ? a : b)</li>
 *   <li>函数调用 name(args...)</li>
 *   <li>属性访问 a.b.c（链式）</li>
 *   <li>索引访问 a[0] / map["key"]</li>
 *   <li>列表字面量 [1, 2, 3]</li>
 *   <li>字典字面量 {key: value}</li>
 *   <li>Lambda x -> expr</li>
 *   <li>模板字符串 `Hello ${name}`</li>
 *   <li>括号分组 (expr)</li>
 * </ul>
 *
 * <p>解析错误抛出 {@link LiteExprException}，携带行列位置。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class ExprParser {

    private final List<Token> tokens;
    private int current = 0;

    public ExprParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * 解析表达式
     *
     * @return AST 根节点
     * @throws LiteExprException 语法错误
     */
    public ExprNode parse() {
        if (tokens.isEmpty() || (tokens.size() == 1 && tokens.get(0).type() == TokenType.EOF)) {
            throw new LiteExprException("表达式为空", 1, 1);
        }
        ExprNode node = parseExpression();
        if (!isAtEnd()) {
            Token unexpected = peek();
            throw new LiteExprException("意外的 Token: '" + unexpected.lexeme() + "'", unexpected.line(), unexpected.column());
        }
        return node;
    }

    // ===== 优先级解析链 =====

    /**
     * 表达式入口：三元表达式
     *
     * <p>语法: logicOr ( '?' expression ':' expression )?
     */
    private ExprNode parseExpression() {
        ExprNode cond = parseLogicOr();
        if (match(TokenType.QUESTION)) {
            Token question = previous();
            ExprNode thenExpr = parseExpression();
            consume(TokenType.COLON, "三元表达式缺少 ':'");
            ExprNode elseExpr = parseExpression();
            return new TernaryNode(cond, thenExpr, elseExpr, question.line(), question.column());
        }
        return cond;
    }

    /**
     * 逻辑或 (|| / or)
     */
    private ExprNode parseLogicOr() {
        ExprNode left = parseLogicAnd();
        while (match(TokenType.OR)) {
            Token op = previous();
            ExprNode right = parseLogicAnd();
            left = new BinaryOpNode(op.lexeme(), left, right, op.line(), op.column());
        }
        return left;
    }

    /**
     * 逻辑与 (&& / and)
     */
    private ExprNode parseLogicAnd() {
        ExprNode left = parseEquality();
        while (match(TokenType.AND)) {
            Token op = previous();
            ExprNode right = parseEquality();
            left = new BinaryOpNode(op.lexeme(), left, right, op.line(), op.column());
        }
        return left;
    }

    /**
     * 相等 (== !=)
     */
    private ExprNode parseEquality() {
        ExprNode left = parseComparison();
        while (match(TokenType.EQ, TokenType.NEQ)) {
            Token op = previous();
            ExprNode right = parseComparison();
            left = new BinaryOpNode(op.lexeme(), left, right, op.line(), op.column());
        }
        return left;
    }

    /**
     * 比较 (> >= < <=)
     */
    private ExprNode parseComparison() {
        ExprNode left = parseAdditive();
        while (match(TokenType.GT, TokenType.GTE, TokenType.LT, TokenType.LTE)) {
            Token op = previous();
            ExprNode right = parseAdditive();
            left = new BinaryOpNode(op.lexeme(), left, right, op.line(), op.column());
        }
        return left;
    }

    /**
     * 加减 (+ -)
     */
    private ExprNode parseAdditive() {
        ExprNode left = parseMultiplicative();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token op = previous();
            ExprNode right = parseMultiplicative();
            left = new BinaryOpNode(op.lexeme(), left, right, op.line(), op.column());
        }
        return left;
    }

    /**
     * 乘除模 (* / %)
     */
    private ExprNode parseMultiplicative() {
        ExprNode left = parseUnary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token op = previous();
            ExprNode right = parseUnary();
            left = new BinaryOpNode(op.lexeme(), left, right, op.line(), op.column());
        }
        return left;
    }

    /**
     * 一元 (! -)
     */
    private ExprNode parseUnary() {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            Token op = previous();
            ExprNode operand = parseUnary();
            return new UnaryOpNode(op.lexeme(), operand, op.line(), op.column());
        }
        return parsePostfix();
    }

    /**
     * 后缀运算：属性访问 . 、索引 [] 、函数调用 ()
     */
    private ExprNode parsePostfix() {
        ExprNode node = parsePrimary();
        while (true) {
            if (match(TokenType.DOT)) {
                Token dot = previous();
                Token name = consume(TokenType.IDENTIFIER, "属性访问后需要标识符");
                node = new MemberAccessNode(node, name.lexeme(), dot.line(), dot.column());
            } else if (match(TokenType.LBRACKET)) {
                Token bracket = previous();
                ExprNode index = parseExpression();
                consume(TokenType.RBRACKET, "索引访问缺少 ']'");
                node = new IndexNode(node, index, bracket.line(), bracket.column());
            } else if (match(TokenType.LPAREN)) {
                // 函数调用：支持 name(args) 和 obj.method(args)
                Token paren = previous();
                List<ExprNode> args = parseArguments();
                consume(TokenType.RPAREN, "函数调用缺少 ')'");
                if (node instanceof VariableNode vn) {
                    node = new FunctionCallNode(vn.name(), args, vn.line(), vn.column());
                } else if (node instanceof MemberAccessNode man) {
                    // 方法调用：obj.method(args) → 函数名 = "obj.method"
                    String methodName = buildMemberChain(man);
                    node = new FunctionCallNode(methodName, args, man.line(), man.column());
                } else {
                    throw new LiteExprException("不能对非函数表达式进行调用", node.line(), node.column());
                }
            } else {
                break;
            }
        }
        return node;
    }

    /**
     * 基本表达式：字面量、变量、分组、列表、字典、Lambda
     */
    private ExprNode parsePrimary() {
        Token token = peek();

        // 字面量
        if (match(TokenType.INTEGER, TokenType.DECIMAL)) {
            Token t = previous();
            return new LiteralNode(t.literal(), t.line(), t.column());
        }
        if (match(TokenType.STRING)) {
            Token t = previous();
            return new LiteralNode(t.literal(), t.line(), t.column());
        }
        if (match(TokenType.BOOLEAN)) {
            Token t = previous();
            return new LiteralNode(t.literal(), t.line(), t.column());
        }
        if (match(TokenType.NULL)) {
            Token t = previous();
            return new LiteralNode(null, t.line(), t.column());
        }

        // 标识符（变量）或 Lambda
        if (match(TokenType.IDENTIFIER)) {
            Token id = previous();
            // Lambda: x -> body
            if (match(TokenType.ARROW)) {
                ExprNode body = parseExpression();
                return new LambdaNode(id.lexeme(), body, id.line(), id.column());
            }
            return new VariableNode(id.lexeme(), id.line(), id.column());
        }

        // 括号分组
        if (match(TokenType.LPAREN)) {
            Token paren = previous();
            ExprNode expr = parseExpression();
            consume(TokenType.RPAREN, "括号分组缺少 ')'");
            // 分组节点直接返回内部表达式（位置信息使用括号位置）
            return expr;
        }

        // 列表 [1, 2, 3]
        if (match(TokenType.LBRACKET)) {
            return parseList();
        }

        // 字典 {key: value}
        if (match(TokenType.LBRACE)) {
            return parseMap();
        }

        // 模板字符串
        if (match(TokenType.TEMPLATE_STR)) {
            return parseTemplateString();
        }

        throw new LiteExprException("意外的 Token: '" + token.lexeme() + "'（期望表达式）", token.line(), token.column());
    }

    /**
     * 解析函数参数列表
     */
    private List<ExprNode> parseArguments() {
        List<ExprNode> args = new ArrayList<>();
        if (check(TokenType.RPAREN)) return args;
        do {
            args.add(parseExpression());
        } while (match(TokenType.COMMA));
        return args;
    }

    /**
     * 解析列表字面量 [1, 2, 3]
     */
    private ExprNode parseList() {
        Token bracket = previous(); // '['
        List<ExprNode> elements = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
            do {
                elements.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RBRACKET, "列表缺少 ']'");
        return new ListNode(elements, bracket.line(), bracket.column());
    }

    /**
     * 解析字典字面量 {key: value, ...}
     */
    private ExprNode parseMap() {
        Token brace = previous(); // '{'
        Map<ExprNode, ExprNode> entries = new LinkedHashMap<>();
        if (!check(TokenType.RBRACE)) {
            do {
                ExprNode key = parseExpression();
                consume(TokenType.COLON, "字典条目缺少 ':'");
                ExprNode value = parseExpression();
                entries.put(key, value);
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RBRACE, "字典缺少 '}'");
        return new MapNode(entries, brace.line(), brace.column());
    }

    /**
     * 解析模板字符串
     *
     * <p>Lexer 已将 `Hello ${name}!` 拆分为 TEMPLATE_STR / TEMPLATE_VAR_START / expr / TEMPLATE_VAR_END / TEMPLATE_STR 序列。
     * Parser 将其合并为 TemplateStringNode。
     */
    private ExprNode parseTemplateString() {
        Token start = previous(); // 第一个 TEMPLATE_STR
        List<ExprNode> parts = new ArrayList<>();
        // 如果第一个 TEMPLATE_STR 不是反引号开始标记（有前缀文本）
        if (!"`".equals(start.lexeme())) {
            parts.add(new LiteralNode(start.literal(), start.line(), start.column()));
        }

        // 循环解析 ${expr} 部分
        while (!isAtEnd()) {
            if (check(TokenType.TEMPLATE_VAR_START)) {
                advance(); // 跳过 ${
                ExprNode expr = parseExpression();
                consume(TokenType.TEMPLATE_VAR_END, "模板变量缺少 '}'");
                parts.add(expr);
                // 后面可能还有 TEMPLATE_STR
                if (check(TokenType.TEMPLATE_STR)) {
                    Token next = advance();
                    if ("`".equals(next.lexeme())) {
                        break; // 模板字符串结束
                    }
                    parts.add(new LiteralNode(next.literal(), next.line(), next.column()));
                }
            } else if (check(TokenType.TEMPLATE_STR)) {
                Token next = advance();
                if ("`".equals(next.lexeme())) {
                    break; // 模板字符串结束
                }
                parts.add(new LiteralNode(next.literal(), next.line(), next.column()));
            } else {
                break;
            }
        }

        return new TemplateStringNode(parts, start.line(), start.column());
    }

    // ===== Token 操作辅助方法 =====

    /**
     * 构建成员访问链的完整路径名（如 System.exit → "System.exit"）
     */
    private String buildMemberChain(MemberAccessNode man) {
        StringBuilder sb = new StringBuilder();
        buildMemberChain(man, sb);
        return sb.toString();
    }

    private void buildMemberChain(ExprNode node, StringBuilder sb) {
        if (node instanceof MemberAccessNode man) {
            buildMemberChain(man.target(), sb);
            sb.append('.').append(man.member());
        } else if (node instanceof VariableNode vn) {
            sb.append(vn.name());
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type() == type;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        Token current = peek();
        throw new LiteExprException(message + "（得到 '" + current.lexeme() + "'）", current.line(), current.column());
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }
}
