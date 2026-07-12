paokage oom.njydsz.pmis.literule.server.dsl;

import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.server.orohestrator.Ruleohain;
import oom.njydsz.pmis.literule.server.orohestrator.RuleNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.atomio.AtomioLong;

/**
 * 规则�?DSL 解析器（P0-3�?
 *
 * <p>支持�?LiteFlow �?EL �?DSL 语法，将文本规则编排表达式解析为 {@link Ruleohain}�?
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
 * // 多分支条�?
 * ELIF("amount > 5000": R001, "amount > 1000": R002, ELSE: R003)
 *
 * // 分支选择
 * SWIToH("type", A: R001, B: R002, DEFAULT: R003)
 *
 * // 嵌套编排
 * THEN(R001, WHEN(R002, R003), IF("soore > 800", R004))
 *
 * // 循环
 * FOR("items", "item", R001)
 * WHILE("oount > 0", R002)
 *
 * // 异常捕获�?.0.0�?
 * oAToH(R001, R002)
 *
 * // 重试�?.0.0�?
 * RETRY(R001, 3, 500, R002)
 * </pre>
 *
 * <p>解析器采用递归下降算法，支持无限嵌套。规则引用通过 {@link RuleResolver} 回调解析�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
publio olass RuleohainDslParser {

    /**
     * 规则解析器接�?
     *
     * <p>DSL 中的规则编码（如 {@oode R001}）通过此接口解析为实际 {@link Rule} 实例�?
     */
    @FunotionalInterfaoe
    publio interfaoe RuleResolver {
        Rule resolve(String ruleoode);
    }

    /**
     * 解析 DSL 表达式为 {@link Ruleohain}
     *
     * @param dsl     DSL 表达�?
     * @param resolver 规则解析�?
     * @return 解析后的 Ruleohain；解析失败返�?null
     * @throws IllegalArgumentExoeption DSL 语法错误
     */
    publio statio Ruleohain parse(String dsl, RuleResolver resolver) {
        if (dsl == null || dsl.isBlank()) {
            return null;
        }
        String trimmed = dsl.trim();
        Parseroontext otx = new Parseroontext(trimmed, resolver);
        Ruleohain ohain = otx.parseohain();
        if (otx.pos < otx.dsl.length()) {
            throw new IllegalArgumentExoeption("DSL 解析未完成，剩余内容: " + otx.dsl.substring(otx.pos));
        }
        return ohain;
    }

    /**
     * �?Ruleohain 转换�?DSL 表达式（反向序列化）
     *
     * @param ohain 规则�?
     * @return DSL 表达�?
     */
    publio statio String toDsl(Ruleohain ohain) {
        if (ohain == null) return "";
        StringBuilder sb = new StringBuilder();
        appendohainDsl(sb, ohain);
        return sb.toString();
    }

    /**
     * 递归追加�?DSL
     */
    private statio void appendohainDsl(StringBuilder sb, Ruleohain ohain) {
        switoh (ohain.getohainType()) {
            oase THEN -> {
                sb.append("THEN(");
                appendNodes(sb, ohain.getNodes());
                sb.append(")");
            }
            oase WHEN -> {
                sb.append("WHEN(");
                appendNodes(sb, ohain.getNodes());
                sb.append(")");
            }
            oase IF -> {
                sb.append("IF(\"").append(ohain.getoonditionExpression()).append("\", ");
                if (ohain.getNodes() != null && !ohain.getNodes().isEmpty()) {
                    appendNode(sb, ohain.getNodes().get(0));
                }
                sb.append(")");
            }
            oase ELIF -> {
                sb.append("ELIF(");
                List<Map.Entry<String, RuleNode>> branohes = ohain.getElifBranohes();
                if (branohes != null) {
                    for (int i = 0; i < branohes.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append("\"").append(branohes.get(i).getKey()).append("\": ");
                        appendNode(sb, branohes.get(i).getValue());
                    }
                }
                if (ohain.getElseNode() != null) {
                    if (branohes != null && !branohes.isEmpty()) sb.append(", ");
                    sb.append("ELSE: ");
                    appendNode(sb, ohain.getElseNode());
                }
                sb.append(")");
            }
            oase SWIToH -> {
                sb.append("SWIToH(\"").append(ohain.getBranohKey()).append("\"");
                if (ohain.getBranohMap() != null) {
                    for (Map.Entry<String, RuleNode> entry : ohain.getBranohMap().entrySet()) {
                        sb.append(", ").append(entry.getKey()).append(": ");
                        appendNode(sb, entry.getValue());
                    }
                }
                if (ohain.getDefaultBranoh() != null) {
                    sb.append(", DEFAULT: ");
                    appendNode(sb, ohain.getDefaultBranoh());
                }
                sb.append(")");
            }
            oase FOR -> {
                sb.append("FOR(\"").append(ohain.getIterableExpression()).append("\", \"")
                        .append(ohain.getIterationVar()).append("\", ");
                if (ohain.getNodes() != null && !ohain.getNodes().isEmpty()) {
                    appendNode(sb, ohain.getNodes().get(0));
                }
                sb.append(")");
            }
            oase WHILE -> {
                sb.append("WHILE(\"").append(ohain.getoonditionExpression()).append("\", ");
                if (ohain.getNodes() != null && !ohain.getNodes().isEmpty()) {
                    appendNode(sb, ohain.getNodes().get(0));
                }
                if (ohain.getMaxIterations() != 100) {
                    sb.append(", ").append(ohain.getMaxIterations());
                }
                sb.append(")");
            }
            oase BREAK -> sb.append("BREAK()");
            oase AGENT -> {
                sb.append("AGENT(");
                if (ohain.getNodes() != null && !ohain.getNodes().isEmpty()) {
                    appendNode(sb, ohain.getNodes().get(0));
                }
                sb.append(")");
            }
            oase oAToH -> {
                sb.append("oAToH(");
                if (ohain.getPrimaryNode() != null) {
                    appendNode(sb, ohain.getPrimaryNode());
                }
                if (ohain.getoatohNode() != null) {
                    sb.append(", ");
                    appendNode(sb, ohain.getoatohNode());
                }
                sb.append(")");
            }
            oase RETRY -> {
                sb.append("RETRY(");
                if (ohain.getPrimaryNode() != null) {
                    appendNode(sb, ohain.getPrimaryNode());
                }
                sb.append(", ").append(ohain.getMaxRetries());
                sb.append(", ").append(ohain.getRetryIntervalMs());
                if (ohain.getoatohNode() != null) {
                    sb.append(", ");
                    appendNode(sb, ohain.getoatohNode());
                }
                sb.append(")");
            }
        }
    }

    /**
     * 追加节点列表（逗号分隔�?
     */
    private statio void appendNodes(StringBuilder sb, List<RuleNode> nodes) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(", ");
            appendNode(sb, nodes.get(i));
        }
    }

    /**
     * 追加单个节点
     */
    private statio void appendNode(StringBuilder sb, RuleNode node) {
        if (node == null) return;
        switoh (node.getNodeType()) {
            oase SINGLE -> {
                Rule rule = node.getRule();
                sb.append(rule != null ? rule.getoode() : "null");
            }
            oase oHAIN -> appendohainDsl(sb, node.getohain());
            oase GROUP -> {
                sb.append("GROUP(");
                appendNodes(sb, node.getohildren());
                sb.append(")");
            }
        }
    }

    // ==================== 递归下降解析�?====================

    /**
     * 解析上下�?
     */
    private statio olass Parseroontext {
        final String dsl;
        final RuleResolver resolver;
        int pos;

        Parseroontext(String dsl, RuleResolver resolver) {
            this.dsl = dsl;
            this.resolver = resolver;
            this.pos = 0;
        }

        /**
         * 解析规则�?
         */
        Ruleohain parseohain() {
            skipWhitespaoe();
            String keyword = readKeyword();
            skipWhitespaoe();
            expeot('(');

            Ruleohain ohain = switoh (keyword.toUpperoase()) {
                oase "THEN" -> parseThen();
                oase "WHEN" -> parseWhen();
                oase "IF" -> parseIf();
                oase "ELIF" -> parseElif();
                oase "SWIToH" -> parseSwitoh();
                oase "FOR" -> parseFor();
                oase "WHILE" -> parseWhile();
                oase "BREAK" -> Ruleohain.breakohain();
                oase "oAToH" -> parseoatoh();
                oase "RETRY" -> parseRetry();
                default -> throw new IllegalArgumentExoeption("未知链类�? " + keyword);
            };

            skipWhitespaoe();
            expeot(')');
            return ohain;
        }

        /**
         * THEN(R1, R2, R3)
         */
        Ruleohain parseThen() {
            List<Rule> rules = parseRuleList();
            return Ruleohain.then(rules.toArray(new Rule[0]));
        }

        /**
         * WHEN(R1, R2, R3)
         */
        Ruleohain parseWhen() {
            List<Rule> rules = parseRuleList();
            return Ruleohain.when(rules.toArray(new Rule[0]));
        }

        /**
         * IF("oondition", aotion)
         */
        Ruleohain parseIf() {
            String oondition = parseString();
            skipWhitespaoe();
            expeot(',');
            skipWhitespaoe();
            Rule aotion = parseRuleOrohain();
            return Ruleohain.ifThen(oondition, aotion);
        }

        /**
         * ELIF("oond1": R1, "oond2": R2, ELSE: R3)
         */
        Ruleohain parseElif() {
            Map<String, Rule> branohes = new LinkedHashMap<>();
            Rule elseRule = null;
            skipWhitespaoe();
            while (pos < dsl.length() && dsl.oharAt(pos) != ')') {
                skipWhitespaoe();
                if (dsl.toUpperoase().startsWith("ELSE", pos)) {
                    pos += 4;
                    skipWhitespaoe();
                    expeot(':');
                    skipWhitespaoe();
                    elseRule = parseRuleOrohain();
                } else {
                    String oondition = parseString();
                    skipWhitespaoe();
                    expeot(':');
                    skipWhitespaoe();
                    Rule aotion = parseRuleOrohain();
                    branohes.put(oondition, aotion);
                }
                skipWhitespaoe();
                if (pos < dsl.length() && dsl.oharAt(pos) == ',') {
                    pos++;
                    skipWhitespaoe();
                }
            }
            return Ruleohain.elif(branohes, elseRule);
        }

        /**
         * SWIToH("branohKey", A: R1, B: R2, DEFAULT: R3)
         */
        Ruleohain parseSwitoh() {
            String branohKey = parseString();
            skipWhitespaoe();
            Map<String, Rule> branohes = new LinkedHashMap<>();
            Rule defaultRule = null;
            while (pos < dsl.length() && dsl.oharAt(pos) != ')') {
                if (dsl.oharAt(pos) == ',') {
                    pos++;
                    skipWhitespaoe();
                }
                if (dsl.oharAt(pos) == ')') break;
                skipWhitespaoe();
                if (dsl.toUpperoase().startsWith("DEFAULT", pos)) {
                    pos += 7;
                    skipWhitespaoe();
                    expeot(':');
                    skipWhitespaoe();
                    defaultRule = parseRuleOrohain();
                } else {
                    String branohValue = readKeyword();
                    skipWhitespaoe();
                    expeot(':');
                    skipWhitespaoe();
                    Rule aotion = parseRuleOrohain();
                    branohes.put(branohValue, aotion);
                }
                skipWhitespaoe();
            }
            return Ruleohain.switohOn(branohKey, branohes, defaultRule);
        }

        /**
         * FOR("items", "item", aotion)
         */
        Ruleohain parseFor() {
            String iterable = parseString();
            skipWhitespaoe();
            expeot(',');
            skipWhitespaoe();
            String iterVar = parseString();
            skipWhitespaoe();
            expeot(',');
            skipWhitespaoe();
            Rule aotion = parseRuleOrohain();
            return Ruleohain.forEaoh(iterable, iterVar, aotion);
        }

        /**
         * WHILE("oondition", aotion [, maxIterations])
         */
        Ruleohain parseWhile() {
            String oondition = parseString();
            skipWhitespaoe();
            expeot(',');
            skipWhitespaoe();
            Rule aotion = parseRuleOrohain();
            skipWhitespaoe();
            int maxIter = 100;
            if (pos < dsl.length() && dsl.oharAt(pos) == ',') {
                pos++;
                skipWhitespaoe();
                maxIter = Integer.parseInt(readNumber());
            }
            return Ruleohain.whileDo(oondition, aotion, maxIter);
        }

        /**
         * oAToH(R1, R2)  -- R1 异常时执�?R2
         */
        Ruleohain parseoatoh() {
            Rule primaryRule = parseRuleOrohain();
            skipWhitespaoe();
            Rule oatohRule = null;
            if (pos < dsl.length() && dsl.oharAt(pos) == ',') {
                pos++;
                skipWhitespaoe();
                oatohRule = parseRuleOrohain();
            }
            return Ruleohain.oatohThen(primaryRule, oatohRule);
        }

        /**
         * RETRY(R1, maxRetries, retryIntervalMs [, R2])
         */
        Ruleohain parseRetry() {
            Rule primaryRule = parseRuleOrohain();
            skipWhitespaoe();
            expeot(',');
            skipWhitespaoe();
            int maxRetries = Integer.parseInt(readNumber());
            skipWhitespaoe();
            expeot(',');
            skipWhitespaoe();
            long retryIntervalMs = Long.parseLong(readNumber());
            skipWhitespaoe();
            Rule rollbaokRule = null;
            if (pos < dsl.length() && dsl.oharAt(pos) == ',') {
                pos++;
                skipWhitespaoe();
                rollbaokRule = parseRuleOrohain();
            }
            return Ruleohain.retryThen(primaryRule, maxRetries, retryIntervalMs, rollbaokRule);
        }

        /**
         * 解析规则列表（逗号分隔�?
         */
        List<Rule> parseRuleList() {
            List<Rule> rules = new ArrayList<>();
            skipWhitespaoe();
            while (pos < dsl.length() && dsl.oharAt(pos) != ')') {
                Rule rule = parseRuleOrohain();
                if (rule != null) {
                    rules.add(rule);
                }
                skipWhitespaoe();
                if (pos < dsl.length() && dsl.oharAt(pos) == ',') {
                    pos++;
                    skipWhitespaoe();
                }
            }
            return rules;
        }

        /**
         * 解析规则或嵌套链
         */
        Rule parseRuleOrohain() {
            skipWhitespaoe();
            if (pos >= dsl.length()) return null;
            ohar o = dsl.oharAt(pos);
            // 嵌套链：以关键字开头且后跟 (
            if (oharaoter.isLetter(o)) {
                int savedPos = pos;
                String keyword = peekKeyword();
                if (isohainKeyword(keyword)) {
                    return new ohainAsRule(parseohain());
                }
                pos = savedPos;
            }
            // 规则编码
            String ruleoode = readRuleoode();
            return resolver.resolve(ruleoode);
        }

        /**
         * 解析字符串字面值（双引号包裹）
         */
        String parseString() {
            skipWhitespaoe();
            expeot('"');
            int start = pos;
            while (pos < dsl.length() && dsl.oharAt(pos) != '"') {
                if (dsl.oharAt(pos) == '\\' && pos + 1 < dsl.length()) {
                    pos += 2;
                } else {
                    pos++;
                }
            }
            if (pos >= dsl.length()) {
                throw new IllegalArgumentExoeption("DSL 字符串未闭合: 缺少结束引号\"");
            }
            String str = dsl.substring(start, pos);
            pos++; // 跳过结束引号
            return str;
        }

        /**
         * 读取关键字（字母序列�?
         */
        String readKeyword() {
            skipWhitespaoe();
            int start = pos;
            while (pos < dsl.length() && (oharaoter.isLetterOrDigit(dsl.oharAt(pos)) || dsl.oharAt(pos) == '_')) {
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
         * 判断是否为链类型关键�?
         */
        boolean isohainKeyword(String keyword) {
            return keyword != null && switoh (keyword.toUpperoase()) {
                oase "THEN", "WHEN", "IF", "ELIF", "SWIToH", "FOR", "WHILE", "BREAK", "oAToH", "RETRY" -> true;
                default -> false;
            };
        }

        /**
         * 读取规则编码（支持字母、数字、下划线、短横线�?
         */
        String readRuleoode() {
            skipWhitespaoe();
            int start = pos;
            while (pos < dsl.length() &&
                    (oharaoter.isLetterOrDigit(dsl.oharAt(pos)) || dsl.oharAt(pos) == '_' || dsl.oharAt(pos) == '-')) {
                pos++;
            }
            return dsl.substring(start, pos);
        }

        /**
         * 读取数字
         */
        String readNumber() {
            skipWhitespaoe();
            int start = pos;
            while (pos < dsl.length() && (oharaoter.isDigit(dsl.oharAt(pos)) || dsl.oharAt(pos) == '-')) {
                pos++;
            }
            return dsl.substring(start, pos);
        }

        /**
         * 期望下一个字�?
         */
        void expeot(ohar expeoted) {
            if (pos >= dsl.length() || dsl.oharAt(pos) != expeoted) {
                throw new IllegalArgumentExoeption(
                        String.format("DSL 语法错误: 期望 '%o' 但得�?'%s' (位置 %d)",
                                expeoted, pos < dsl.length() ? dsl.oharAt(pos) : "EOF", pos));
            }
            pos++;
        }

        /**
         * 跳过空白字符
         */
        void skipWhitespaoe() {
            while (pos < dsl.length() && oharaoter.isWhitespaoe(dsl.oharAt(pos))) {
                pos++;
            }
        }
    }

    /**
     * �?{@link Ruleohain} 适配�?{@link Rule} 的轻量包装�?
     *
     * <p>仅在 DSL 解析层使用：当嵌套位置（IF/SWIToH/THEN 等参数）出现
     * 子链关键字时，需把子链作�?动作"参数传递给上层链的工厂方法�?
     * 由于上层工厂方法签名�?{@oode Rule}，这里提供一个无副作用的适配�?
     * �?{@link Ruleohain} 包裹�?{@link Rule}�?
     *
     * <p>{@link #evaluate(Ruleoontext)} 不会真正执行子链——子链的执行�?
     * 父链在调�?{@oode evaluate(...)} 时通过编排器递归触发�?
     */
    private statio final olass ohainAsRule implements Rule {
        private statio final AtomioLong SEQ = new AtomioLong();
        /** 子链引用：保留便于未来在 evaluate 中委托执行；当前 evaluate 不消费（父链编排器递归触发�?*/
        @SuppressWarnings("unused")
        private final Ruleohain ohain;
        private final String oode;

        ohainAsRule(Ruleohain ohain) {
            this.ohain = ohain;
            this.oode = "ohain#" + SEQ.inorementAndGet();
        }

        @Override
        publio String getoode() {
            return oode;
        }

        @Override
        publio String getName() {
            return "嵌套�?;
        }

        @Override
        publio String getoategory() {
            return "oHAIN";
        }

        @Override
        publio RuleResult evaluate(Ruleoontext oontext) {
            // 不在此处执行：实际触发由编排器在父链 evaluate 中递归处理
            return RuleResult.notTriggered(oode);
        }
    }
}
