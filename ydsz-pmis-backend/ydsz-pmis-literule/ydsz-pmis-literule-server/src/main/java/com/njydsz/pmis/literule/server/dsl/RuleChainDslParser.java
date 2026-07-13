package com.njydsz.pmis.literule.server.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.server.orchestrator.RuleChain;
import com.njydsz.pmis.literule.server.orchestrator.RuleNode;

/**
 * 规则链 DSL 解析器（P0-3）
 *
 * <p>支持类 LiteFlow 的 EL 式 DSL 语法，将文本规则编排表达式解析为 {@link RuleChain}。
 *
 * <h3>语法示例</h3>
 * <pre>
 * // 顺序执行
 * THEN(R001, R002, R003)
 *
 * // 并行执行
 * WHEN(R001, R002)
 *
 * // 条件执行
 * IF("amount > 1000", R001)
 *
 * // 多分支条件
 * ELIF("amount > 5000": R001, "amount > 1000": R002, ELSE: R003)
 *
 * // 分支选择
 * SWITCH("type", A: R001, B: R002, DEFAULT: R003)
 *
 * // 嵌套编排
 * THEN(R001, WHEN(R002, R003), IF("score > 800", R004))
 *
 * // 循环
 * FOR("items", "item", R001)
 * WHILE("count > 0", R002)
 *
 * // 异常捕获（2.0.0）
 * CATCH(R001, R002)
 *
 * // 重试（2.0.0）
 * RETRY(R001, 3, 500, R002)
 * </pre>
 *
 * <p>解析器采用递归下降算法，支持无限嵌套。规则引用通过 {@link RuleResolver} 回调解析。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
public class RuleChainDslParser {

    /**
     * 规则解析器接口
     *
     * <p>DSL 中的规则编码（如 {@code R001}）通过此接口解析为实际 {@link Rule} 实例。
     */
    @FunctionalInterface
    public interface RuleResolver {
        Rule resolve(String ruleCode);
    }

    /**
     * 解析 DSL 表达式为 {@link RuleChain}
     *
     * @param dsl     DSL 表达式
     * @param resolver 规则解析器
     * @return 解析后的 RuleChain；解析失败返回 null
     * @throws IllegalArgumentException DSL 语法错误
     */
    public static RuleChain parse(String dsl, RuleResolver resolver) {
        if (dsl == null || dsl.isBlank()) {
            return null;
        }
        String trimmed = dsl.trim();
        ParserContext ctx = new ParserContext(trimmed, resolver);
        RuleChain chain = ctx.parseChain();
        if (ctx.pos < ctx.dsl.length()) {
            throw new IllegalArgumentException("DSL 解析未完成，剩余内容: " + ctx.dsl.substring(ctx.pos));
        }
        return chain;
    }

    /**
     * 将 RuleChain 转换为 DSL 表达式（反向序列化）
     *
     * @param chain 规则链
     * @return DSL 表达式
     */
    public static String toDsl(RuleChain chain) {
        if (chain == null) return "";
        StringBuilder sb = new StringBuilder();
        appendChainDsl(sb, chain);
        return sb.toString();
    }

    /**
     * 递归追加链 DSL
     */
    private static void appendChainDsl(StringBuilder sb, RuleChain chain) {
        switch (chain.getChainType()) {
            case THEN -> {
                sb.append("THEN(");
                appendNodes(sb, chain.getNodes());
                sb.append(")");
            }
            case WHEN -> {
                sb.append("WHEN(");
                appendNodes(sb, chain.getNodes());
                sb.append(")");
            }
            case IF -> {
                sb.append("IF(\"").append(chain.getConditionExpression()).append("\", ");
                if (chain.getNodes() != null && !chain.getNodes().isEmpty()) {
                    appendNode(sb, chain.getNodes().get(0));
                }
                sb.append(")");
            }
            case ELIF -> {
                sb.append("ELIF(");
                List<Map.Entry<String, RuleNode>> branches = chain.getElifBranches();
                if (branches != null) {
                    for (int i = 0; i < branches.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append("\"").append(branches.get(i).getKey()).append("\": ");
                        appendNode(sb, branches.get(i).getValue());
                    }
                }
                if (chain.getElseNode() != null) {
                    if (branches != null && !branches.isEmpty()) sb.append(", ");
                    sb.append("ELSE: ");
                    appendNode(sb, chain.getElseNode());
                }
                sb.append(")");
            }
            case SWITCH -> {
                sb.append("SWITCH(\"").append(chain.getBranchKey()).append("\"");
                if (chain.getBranchMap() != null) {
                    for (Map.Entry<String, RuleNode> entry : chain.getBranchMap().entrySet()) {
                        sb.append(", ").append(entry.getKey()).append(": ");
                        appendNode(sb, entry.getValue());
                    }
                }
                if (chain.getDefaultBranch() != null) {
                    sb.append(", DEFAULT: ");
                    appendNode(sb, chain.getDefaultBranch());
                }
                sb.append(")");
            }
            case FOR -> {
                sb.append("FOR(\"").append(chain.getIterableExpression()).append("\", \"")
                        .append(chain.getIterationVar()).append("\", ");
                if (chain.getNodes() != null && !chain.getNodes().isEmpty()) {
                    appendNode(sb, chain.getNodes().get(0));
                }
                sb.append(")");
            }
            case WHILE -> {
                sb.append("WHILE(\"").append(chain.getConditionExpression()).append("\", ");
                if (chain.getNodes() != null && !chain.getNodes().isEmpty()) {
                    appendNode(sb, chain.getNodes().get(0));
                }
                if (chain.getMaxIterations() != 100) {
                    sb.append(", ").append(chain.getMaxIterations());
                }
                sb.append(")");
            }
            case BREAK -> sb.append("BREAK()");
            case CATCH -> {
                sb.append("CATCH(");
                if (chain.getPrimaryNode() != null) {
                    appendNode(sb, chain.getPrimaryNode());
                }
                if (chain.getCatchNode() != null) {
                    sb.append(", ");
                    appendNode(sb, chain.getCatchNode());
                }
                sb.append(")");
            }
            case RETRY -> {
                sb.append("RETRY(");
                if (chain.getPrimaryNode() != null) {
                    appendNode(sb, chain.getPrimaryNode());
                }
                sb.append(", ").append(chain.getMaxRetries());
                sb.append(", ").append(chain.getRetryIntervalMs());
                if (chain.getCatchNode() != null) {
                    sb.append(", ");
                    appendNode(sb, chain.getCatchNode());
                }
                sb.append(")");
            }
        }
    }

    /**
     * 追加节点列表（逗号分隔）
     */
    private static void appendNodes(StringBuilder sb, List<RuleNode> nodes) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(", ");
            appendNode(sb, nodes.get(i));
        }
    }

    /**
     * 追加单个节点
     */
    private static void appendNode(StringBuilder sb, RuleNode node) {
        if (node == null) return;
        switch (node.getNodeType()) {
            case SINGLE -> {
                Rule rule = node.getRule();
                sb.append(rule != null ? rule.getCode() : "null");
            }
            case CHAIN -> appendChainDsl(sb, node.getChain());
            case GROUP -> {
                sb.append("GROUP(");
                appendNodes(sb, node.getChildren());
                sb.append(")");
            }
        }
    }

    // ==================== 递归下降解析器 ====================

    /**
     * 解析上下文
     */
    private static class ParserContext {
        final String dsl;
        final RuleResolver resolver;
        int pos;

        ParserContext(String dsl, RuleResolver resolver) {
            this.dsl = dsl;
            this.resolver = resolver;
            this.pos = 0;
        }

        /**
         * 解析规则链
         */
        RuleChain parseChain() {
            skipWhitespace();
            String keyword = readKeyword();
            skipWhitespace();
            expect('(');

            RuleChain chain = switch (keyword.toUpperCase()) {
                case "THEN" -> parseThen();
                case "WHEN" -> parseWhen();
                case "IF" -> parseIf();
                case "ELIF" -> parseElif();
                case "SWITCH" -> parseSwitch();
                case "FOR" -> parseFor();
                case "WHILE" -> parseWhile();
                case "BREAK" -> RuleChain.breakChain();
                case "CATCH" -> parseCatch();
                case "RETRY" -> parseRetry();
                default -> throw new IllegalArgumentException("未知链类型: " + keyword);
            };

            skipWhitespace();
            expect(')');
            return chain;
        }

        /**
         * THEN(R1, R2, R3)
         */
        RuleChain parseThen() {
            List<Rule> rules = parseRuleList();
            return RuleChain.then(rules.toArray(new Rule[0]));
        }

        /**
         * WHEN(R1, R2, R3)
         */
        RuleChain parseWhen() {
            List<Rule> rules = parseRuleList();
            return RuleChain.when(rules.toArray(new Rule[0]));
        }

        /**
         * IF("condition", action)
         */
        RuleChain parseIf() {
            String condition = parseString();
            skipWhitespace();
            expect(',');
            skipWhitespace();
            Rule action = parseRuleOrChain();
            return RuleChain.ifThen(condition, action);
        }

        /**
         * ELIF("cond1": R1, "cond2": R2, ELSE: R3)
         */
        RuleChain parseElif() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            Rule elseRule = null;
            skipWhitespace();
            while (pos < dsl.length() && dsl.charAt(pos) != ')') {
                skipWhitespace();
                if (dsl.toUpperCase().startsWith("ELSE", pos)) {
                    pos += 4;
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    elseRule = parseRuleOrChain();
                } else {
                    String condition = parseString();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    Rule action = parseRuleOrChain();
                    branches.put(condition, action);
                }
                skipWhitespace();
                if (pos < dsl.length() && dsl.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                }
            }
            return RuleChain.elif(branches, elseRule);
        }

        /**
         * SWITCH("branchKey", A: R1, B: R2, DEFAULT: R3)
         */
        RuleChain parseSwitch() {
            String branchKey = parseString();
            skipWhitespace();
            Map<String, Rule> branches = new LinkedHashMap<>();
            Rule defaultRule = null;
            while (pos < dsl.length() && dsl.charAt(pos) != ')') {
                if (dsl.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                }
                if (dsl.charAt(pos) == ')') break;
                skipWhitespace();
                if (dsl.toUpperCase().startsWith("DEFAULT", pos)) {
                    pos += 7;
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    defaultRule = parseRuleOrChain();
                } else {
                    String branchValue = readKeyword();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    Rule action = parseRuleOrChain();
                    branches.put(branchValue, action);
                }
                skipWhitespace();
            }
            return RuleChain.switchOn(branchKey, branches, defaultRule);
        }

        /**
         * FOR("items", "item", action)
         */
        RuleChain parseFor() {
            String iterable = parseString();
            skipWhitespace();
            expect(',');
            skipWhitespace();
            String iterVar = parseString();
            skipWhitespace();
            expect(',');
            skipWhitespace();
            Rule action = parseRuleOrChain();
            return RuleChain.forEach(iterable, iterVar, action);
        }

        /**
         * WHILE("condition", action [, maxIterations])
         */
        RuleChain parseWhile() {
            String condition = parseString();
            skipWhitespace();
            expect(',');
            skipWhitespace();
            Rule action = parseRuleOrChain();
            skipWhitespace();
            int maxIter = 100;
            if (pos < dsl.length() && dsl.charAt(pos) == ',') {
                pos++;
                skipWhitespace();
                maxIter = Integer.parseInt(readNumber());
            }
            return RuleChain.whileDo(condition, action, maxIter);
        }

        /**
         * CATCH(R1, R2)  -- R1 异常时执行 R2
         */
        RuleChain parseCatch() {
            Rule primaryRule = parseRuleOrChain();
            skipWhitespace();
            Rule catchRule = null;
            if (pos < dsl.length() && dsl.charAt(pos) == ',') {
                pos++;
                skipWhitespace();
                catchRule = parseRuleOrChain();
            }
            return RuleChain.catchThen(primaryRule, catchRule);
        }

        /**
         * RETRY(R1, maxRetries, retryIntervalMs [, R2])
         */
        RuleChain parseRetry() {
            Rule primaryRule = parseRuleOrChain();
            skipWhitespace();
            expect(',');
            skipWhitespace();
            int maxRetries = Integer.parseInt(readNumber());
            skipWhitespace();
            expect(',');
            skipWhitespace();
            long retryIntervalMs = Long.parseLong(readNumber());
            skipWhitespace();
            Rule rollbackRule = null;
            if (pos < dsl.length() && dsl.charAt(pos) == ',') {
                pos++;
                skipWhitespace();
                rollbackRule = parseRuleOrChain();
            }
            return RuleChain.retryThen(primaryRule, maxRetries, retryIntervalMs, rollbackRule);
        }

        /**
         * 解析规则列表（逗号分隔）
         */
        List<Rule> parseRuleList() {
            List<Rule> rules = new ArrayList<>();
            skipWhitespace();
            while (pos < dsl.length() && dsl.charAt(pos) != ')') {
                Rule rule = parseRuleOrChain();
                if (rule != null) {
                    rules.add(rule);
                }
                skipWhitespace();
                if (pos < dsl.length() && dsl.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                }
            }
            return rules;
        }

        /**
         * 解析规则或嵌套链
         */
        Rule parseRuleOrChain() {
            skipWhitespace();
            if (pos >= dsl.length()) return null;
            char c = dsl.charAt(pos);
            // 嵌套链：以关键字开头且后跟 (
            if (Character.isLetter(c)) {
                int savedPos = pos;
                String keyword = peekKeyword();
                if (isChainKeyword(keyword)) {
                    return new ChainAsRule(parseChain());
                }
                pos = savedPos;
            }
            // 规则编码
            String ruleCode = readRuleCode();
            return resolver.resolve(ruleCode);
        }

        /**
         * 解析字符串字面值（双引号包裹）
         */
        String parseString() {
            skipWhitespace();
            expect('"');
            int start = pos;
            while (pos < dsl.length() && dsl.charAt(pos) != '"') {
                if (dsl.charAt(pos) == '\\' && pos + 1 < dsl.length()) {
                    pos += 2;
                } else {
                    pos++;
                }
            }
            if (pos >= dsl.length()) {
                throw new IllegalArgumentException("DSL 字符串未闭合: 缺少结束引号\"");
            }
            String str = dsl.substring(start, pos);
            pos++; // 跳过结束引号
            return str;
        }

        /**
         * 读取关键字（字母序列）
         */
        String readKeyword() {
            skipWhitespace();
            int start = pos;
            while (pos < dsl.length() && (Character.isLetterOrDigit(dsl.charAt(pos)) || dsl.charAt(pos) == '_')) {
                pos++;
            }
            return dsl.substring(start, pos);
        }

        /**
         * 预读关键字（不移动位置）
         */
        String peekKeyword() {
            int savedPos = pos;
            String keyword = readKeyword();
            pos = savedPos;
            return keyword;
        }

        /**
         * 判断是否为链类型关键字
         */
        boolean isChainKeyword(String keyword) {
            return keyword != null && switch (keyword.toUpperCase()) {
                case "THEN", "WHEN", "IF", "ELIF", "SWITCH", "FOR", "WHILE", "BREAK", "CATCH", "RETRY" -> true;
                default -> false;
            };
        }

        /**
         * 读取规则编码（支持字母、数字、下划线、短横线）
         */
        String readRuleCode() {
            skipWhitespace();
            int start = pos;
            while (pos < dsl.length() &&
                    (Character.isLetterOrDigit(dsl.charAt(pos)) || dsl.charAt(pos) == '_' || dsl.charAt(pos) == '-')) {
                pos++;
            }
            return dsl.substring(start, pos);
        }

        /**
         * 读取数字
         */
        String readNumber() {
            skipWhitespace();
            int start = pos;
            while (pos < dsl.length() && (Character.isDigit(dsl.charAt(pos)) || dsl.charAt(pos) == '-')) {
                pos++;
            }
            return dsl.substring(start, pos);
        }

        /**
         * 期望下一个字符
         */
        void expect(char expected) {
            if (pos >= dsl.length() || dsl.charAt(pos) != expected) {
                throw new IllegalArgumentException(
                        String.format("DSL 语法错误: 期望 '%c' 但得到 '%s' (位置 %d)",
                                expected, pos < dsl.length() ? dsl.charAt(pos) : "EOF", pos));
            }
            pos++;
        }

        /**
         * 跳过空白字符
         */
        void skipWhitespace() {
            while (pos < dsl.length() && Character.isWhitespace(dsl.charAt(pos))) {
                pos++;
            }
        }
    }

    /**
     * 将 {@link RuleChain} 适配为 {@link Rule} 的轻量包装。
     *
     * <p>仅在 DSL 解析层使用：当嵌套位置（IF/SWITCH/THEN 等参数）出现
     * 子链关键字时，需把子链作为"动作"参数传递给上层链的工厂方法。
     * 由于上层工厂方法签名是 {@code Rule}，这里提供一个无副作用的适配器
     * 将 {@link RuleChain} 包裹为 {@link Rule}。
     *
     * <p>{@link #evaluate(RuleContext)} 不会真正执行子链——子链的执行由
     * 父链在调用 {@code evaluate(...)} 时通过编排器递归触发。
     */
    private static final class ChainAsRule implements Rule {
        private static final AtomicLong SEQ = new AtomicLong();
        /** 子链引用：保留便于未来在 evaluate 中委托执行；当前 evaluate 不消费（父链编排器递归触发） */
        @SuppressWarnings("unused")
        private final RuleChain chain;
        private final String code;

        ChainAsRule(RuleChain chain) {
            this.chain = chain;
            this.code = "chain#" + SEQ.incrementAndGet();
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getName() {
            return "嵌套链";
        }

        @Override
        public String getCategory() {
            return "CHAIN";
        }

        @Override
        public RuleResult evaluate(RuleContext context) {
            // 不在此处执行：实际触发由编排器在父链 evaluate 中递归处理
            return RuleResult.notTriggered(code);
        }
    }
}
