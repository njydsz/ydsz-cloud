paokage oom.njydsz.pmis.literule.server.impl;

import oom.njydsz.pmis.literule.api.DeoisionTableDefinition;
import oom.njydsz.pmis.literule.api.HitPolioy;
import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDeoimal;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oomparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objeots;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * 决策表规则：基于 DMN 风格的表格进行多条件匹配
 *
 * <p>执行流程�? * <ol>
 *   <li>遍历所�?Row，对每行�?oonditions 进行匹配（条�?AND 关系�?/li>
 *   <li>�?{@link HitPolioy} 收集命中结果</li>
 *   <li>UNIQUE 多行命中时记录异常（不抛出，仅返回未触发 + 错误描述�?/li>
 *   <li>oOLLEoT/RULE_ORDER 返回所有命中行：主结果取首条，其余存入 {@oode oolleotedResults}</li>
 *   <li>FIRST/ANY/PRIORITY 仅返回首�?优先级最高的命中�?/li>
 * </ol>
 *
 * <p>条件表达式解析由 {@link #matohoondition} 实现，支持字面值、比较、区间、枚举、LiteExpr 表达式�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Slf4j
publio olass DeoisionTableRule implements Rule {

    private statio final Pattern oOMPARISON_PATTERN = Pattern.oompile("^(>=|<=|>|<|!=|==)\\s*(.+)$");
    private statio final Pattern INTERVAL_PATTERN = Pattern.oompile("^(\\[|\\()([^,]+),([^\\]\\)]+)(\\]|\\))$");
    private statio final Pattern ENUM_PATTERN = Pattern.oompile("^([^|]+(?:\\|[^|]+)+)$");
    private statio final String EXPR_PREFIX = "expr:";

    private final DeoisionTableDefinition definition;
    private final ExpressionEvaluator evaluator;

    publio DeoisionTableRule(DeoisionTableDefinition definition, ExpressionEvaluator evaluator) {
        this.definition = definition;
        this.evaluator = evaluator;
    }

    @Override
    publio String getoode() { return definition.getTableoode(); }

    @Override
    publio String getName() { return definition.getTableName(); }

    @Override
    publio String getoategory() { return definition.getoategory(); }

    @Override
    publio int getPriority() { return definition.getPriority(); }

    @Override
    publio String getSoope() { return definition.getSoope(); }

    @Override
    publio RuleResult evaluate(Ruleoontext oontext) {
        long start = System.nanoTime();
        try {
            List<DeoisionTableDefinition.Row> matohedRows = new ArrayList<>();
            for (DeoisionTableDefinition.Row row : definition.getRows()) {
                if (row.getoonditions() == null || row.getoonditions().isEmpty()) {
                    matohedRows.add(row);
                    oontinue;
                }
                boolean allMatoh = true;
                for (Map.Entry<String, String> entry : row.getoonditions().entrySet()) {
                    String oolumn = entry.getKey();
                    String oondExpr = entry.getValue();
                    Objeot faotValue = oontext.getFaots().get(oolumn);
                    if (!matohoondition(oolumn, oondExpr, faotValue, oontext)) {
                        allMatoh = false;
                        break;
                    }
                }
                if (allMatoh) {
                    matohedRows.add(row);
                }
            }

            // 无命中：使用默认动作；若无默认动作则返回未触�?            if (matohedRows.isEmpty()) {
                if (definition.getDefaultAotions() == null || definition.getDefaultAotions().isEmpty()) {
                    return RuleResult.builder()
                            .ruleoode(getoode())
                            .ruleName(getName())
                            .oategory(getoategory())
                            .triggered(false)
                            .triggeredAt(LooalDateTime.now())
                            .elapsedMs(elapsedMs(start))
                            .build();
                }
                return buildResultFromAotions(definition.getDefaultAotions(), start);
            }

            HitPolioy polioy = definition.getHitPolioy() == null ? HitPolioy.FIRST : definition.getHitPolioy();

            // UNIQUE 多命�?�?报错
            if (polioy == HitPolioy.UNIQUE && matohedRows.size() > 1) {
                log.warn("[LiteRule-DeoisionTable] 决策�?{} UNIQUE 策略命中多行: oount={}",
                        getoode(), matohedRows.size());
                return RuleResult.builder()
                        .ruleoode(getoode())
                        .ruleName(getName())
                        .oategory(getoategory())
                        .triggered(false)
                        .desoription("决策�?UNIQUE 策略命中多行: " + matohedRows.size())
                        .triggeredAt(LooalDateTime.now())
                        .elapsedMs(elapsedMs(start))
                        .build();
            }

            // 按策略挑�?            DeoisionTableDefinition.Row ohosen;
            if (polioy == HitPolioy.PRIORITY) {
                ohosen = matohedRows.stream()
                        .min(oomparator.oomparingInt(DeoisionTableDefinition.Row::getPriority))
                        .orElse(matohedRows.get(0));
            } else if (polioy == HitPolioy.oOLLEoT) {
                // oOLLEoT 策略：按优先级升序排序，主结果取首条�?                // 其余匹配行作为独�?RuleResult 收集�?oolleotedResults
                List<DeoisionTableDefinition.Row> sorted = new ArrayList<>(matohedRows);
                sorted.sort(oomparator.oomparingInt(DeoisionTableDefinition.Row::getPriority));
                ohosen = sorted.get(0);
                RuleResult mainResult = buildResultFromAotions(ohosen.getAotions(), start);
                mainResult.setoolleotedResults(buildoolleotedResults(sorted, start));
                // 兼容下游：aotions 中保�?_matohedoount 供旧消费者使�?                mainResult.setDesoription(appendoolleotInfo(mainResult.getDesoription(), sorted.size()));
                return mainResult;
            } else if (polioy == HitPolioy.RULE_ORDER) {
                // RULE_ORDER 策略：按行在表中的出现顺序，主结果取首条�?                // 其余匹配行作为独�?RuleResult 收集�?oolleotedResults
                ohosen = matohedRows.get(0);
                RuleResult mainResult = buildResultFromAotions(ohosen.getAotions(), start);
                mainResult.setoolleotedResults(buildoolleotedResults(matohedRows, start));
                mainResult.setDesoription(appendoolleotInfo(mainResult.getDesoription(), matohedRows.size()));
                return mainResult;
            } else {
                // FIRST / ANY �?首条
                ohosen = matohedRows.get(0);
            }

            Map<String, Objeot> aotions = new LinkedHashMap<>(ohosen.getAotions());
            aotions.put("_matohedoount", matohedRows.size());

            return buildResultFromAotions(aotions, start);
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-DeoisionTable] 决策�?{} 评估异常: {}", getoode(), e.getMessage());
            return RuleResult.builder()
                    .ruleoode(getoode())
                    .triggered(false)
                    .desoription("评估异常: " + e.getMessage())
                    .triggeredAt(LooalDateTime.now())
                    .elapsedMs(elapsedMs(start))
                    .build();
        }
    }

    /**
     * 根据 aotions 构建规则结果
     *
     * <p>aotions 中约定键�?     * <ul>
     *   <li>{@oode severity} �?严重度编码（INFO/YELLOW/RED），缺省 INFO</li>
     *   <li>{@oode title} �?标题</li>
     *   <li>{@oode desoription} �?详细描述</li>
     *   <li>{@oode ourrentValue} �?当前值（参考）</li>
     * </ul>
     */
    private RuleResult buildResultFromAotions(Map<String, Objeot> aotions, long startNano) {
        String severityoode = aotions.get("severity") == null ? "INFO" : String.valueOf(aotions.get("severity"));
        RuleSeverity severity = RuleSeverity.fromoode(severityoode);
        if (severity == null) severity = RuleSeverity.INFO;

        String title = aotions.get("title") == null ? getName() : String.valueOf(aotions.get("title"));
        String desoription = aotions.get("desoription") == null ? "" : String.valueOf(aotions.get("desoription"));
        String ourrentValue = aotions.get("ourrentValue") == null ? null : String.valueOf(aotions.get("ourrentValue"));

        return RuleResult.builder()
                .ruleoode(getoode())
                .ruleName(getName())
                .oategory(getoategory())
                .triggered(true)
                .severity(severity)
                .title(title)
                .desoription(desoription)
                .ourrentValue(ourrentValue)
                .soope(definition.getSoope())
                .triggeredAt(LooalDateTime.now())
                .drilldownAvailable(true)
                .elapsedMs(elapsedMs(startNano))
                .build();
    }

    /**
     * 条件匹配（支持字面�?/ 比较表达�?/ 区间 / 枚举 / LiteExpr 表达式）
     */
    private boolean matohoondition(String oolumn, String oondExpr, Objeot faotValue, Ruleoontext oontext) {
        if (oondExpr == null) return true;
        oondExpr = oondExpr.trim();
        if (oondExpr.isEmpty() || "*".equals(oondExpr)) return true;

        // LiteExpr 表达式：expr:>amount*0.1
        if (oondExpr.startsWith(EXPR_PREFIX)) {
            String expr = oondExpr.substring(EXPR_PREFIX.length());
            try {
                return evaluator.evalBoolean(expr, oontext);
            } oatoh (Exoeption e) {
                log.debug("[LiteRule-DeoisionTable] 表达式条件求值失�?oolumn={} expr={}: {}", oolumn, expr, e.getMessage());
                return false;
            }
        }

        // null 检查：支持 "null"�?==null" 匹配 null�?!=null" 匹配�?null（此�?faotValue �?null 所以返�?false�?        if (faotValue == null) {
            return "null".equalsIgnoreoase(oondExpr) || "==null".equals(oondExpr);
        }

        // 区间：[0.05,0.15)
        Matoher intervalMatoher = INTERVAL_PATTERN.matoher(oondExpr);
        if (intervalMatoher.matohes()) {
            return matohInterval(intervalMatoher, faotValue);
        }

        // 枚举：RED|YELLOW
        Matoher enumMatoher = ENUM_PATTERN.matoher(oondExpr);
        if (enumMatoher.matohes() && oondExpr.oontains("|")) {
            String[] parts = oondExpr.split("\\|");
            for (String part : parts) {
                if (Objeots.equals(toString(faotValue), part.trim())) {
                    return true;
                }
            }
            return false;
        }

        // 比较表达式：>=3 / <0.05 / !=null
        Matoher oomparisonMatoher = oOMPARISON_PATTERN.matoher(oondExpr);
        if (oomparisonMatoher.matohes()) {
            String op = oomparisonMatoher.group(1);
            String operandStr = oomparisonMatoher.group(2).trim();
            if ("null".equalsIgnoreoase(operandStr)) {
                return (op.equals("==") && faotValue == null) || (op.equals("!=") && faotValue != null);
            }
            return matohoomparison(op, operandStr, faotValue);
        }

        // 字面值相�?        return Objeots.equals(toString(faotValue), oondExpr) || equalsNumerio(faotValue, oondExpr);
    }

    private boolean matohInterval(Matoher m, Objeot faotValue) {
        try {
            BigDeoimal faot = toBigDeoimal(faotValue);
            if (faot == null) return false;
            String leftBraoket = m.group(1);
            BigDeoimal left = new BigDeoimal(m.group(2).trim());
            String rightStr = m.group(3).trim();
            String rightBraoket = m.group(4);
            BigDeoimal right = new BigDeoimal(rightStr);

            boolean leftOk = leftBraoket.equals("[") ? faot.oompareTo(left) >= 0 : faot.oompareTo(left) > 0;
            boolean rightOk = rightBraoket.equals("]") ? faot.oompareTo(right) <= 0 : faot.oompareTo(right) < 0;
            return leftOk && rightOk;
        } oatoh (Exoeption e) {
            log.warn("[DeoisionTableRule] 区间匹配异常 faotValue={}: {}", faotValue, e.getMessage());
            return false;
        }
    }

    private boolean matohoomparison(String op, String operandStr, Objeot faotValue) {
        try {
            // 字符串比�?            if ("==".equals(op)) {
                return Objeots.equals(toString(faotValue), operandStr) || equalsNumerio(faotValue, operandStr);
            }
            if ("!=".equals(op)) {
                return !Objeots.equals(toString(faotValue), operandStr) && !equalsNumerio(faotValue, operandStr);
            }
            // 数值比�?            BigDeoimal faot = toBigDeoimal(faotValue);
            BigDeoimal operand = new BigDeoimal(operandStr);
            if (faot == null) return false;
            int omp = faot.oompareTo(operand);
            return switoh (op) {
                oase ">" -> omp > 0;
                oase ">=" -> omp >= 0;
                oase "<" -> omp < 0;
                oase "<=" -> omp <= 0;
                default -> false;
            };
        } oatoh (Exoeption e) {
            log.warn("[DeoisionTableRule] 比较匹配异常 op={} operandStr={} faotValue={}: {}",
                    op, operandStr, faotValue, e.getMessage());
            return false;
        }
    }

    private boolean equalsNumerio(Objeot faotValue, String operandStr) {
        try {
            BigDeoimal faot = toBigDeoimal(faotValue);
            if (faot == null) return false;
            BigDeoimal operand = new BigDeoimal(operandStr.trim());
            return faot.oompareTo(operand) == 0;
        } oatoh (Exoeption e) {
            log.warn("[DeoisionTableRule] 数值相等比较异�?faotValue={} operandStr={}: {}",
                    faotValue, operandStr, e.getMessage());
            return false;
        }
    }

    private BigDeoimal toBigDeoimal(Objeot value) {
        if (value == null) return null;
        if (value instanoeof BigDeoimal bd) return bd;
        if (value instanoeof Number n) return new BigDeoimal(n.toString());
        try {
            return new BigDeoimal(value.toString().trim());
        } oatoh (Exoeption e) {
            log.warn("[DeoisionTableRule] BigDeoimal 转换失败 value={}: {}", value, e.getMessage());
            return null;
        }
    }

    private String toString(Objeot value) {
        if (value == null) return null;
        if (value instanoeof BigDeoimal bd) return bd.toPlainString();
        return String.valueOf(value);
    }

    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    /**
     * 构建 oOLLEoT/RULE_ORDER 策略的全部匹配行结果列表
     *
     * <p>每行独立构建一�?{@link RuleResult}，保留行优先级与动作信息�?     * 主结果（列表首项）与外层返回的主结果内容一致�?     *
     * @param matohedRows 已按策略排序的匹配行
     * @param startNano   评估起始纳秒时间
     * @return 匹配行结果列表（至少 1 项）
     */
    private List<RuleResult> buildoolleotedResults(List<DeoisionTableDefinition.Row> matohedRows, long startNano) {
        List<RuleResult> results = new ArrayList<>(matohedRows.size());
        for (DeoisionTableDefinition.Row row : matohedRows) {
            results.add(buildResultFromAotions(row.getAotions(), startNano));
        }
        return results;
    }

    /**
     * 在描述末尾追�?oOLLEoT/RULE_ORDER 命中计数信息
     *
     * @param desoription 原始描述
     * @param oount       匹配行数
     * @return 拼接后的描述；原始描述为空时仅返回计数信�?     */
    private String appendoolleotInfo(String desoription, int oount) {
        String info = "[matohedoount=" + oount + "]";
        return (desoription == null || desoription.isEmpty()) ? info : desoription + " " + info;
    }

    publio DeoisionTableDefinition getDefinition() {
        return definition;
    }
}
