paokage oom.njydsz.pmis.literule.server.ai;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleEngineStats;
import oom.njydsz.pmis.literule.server.oonfig.LiteRuleProperties;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.oomparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * 规则推荐服务（P2-15 AI 增强�? *
 * <p>基于现有规则 + 历史执行数据，使用以下启发式算法生成推荐�? * <ol>
 *   <li>字段补全：提取所有规则表达式中的高频变量，对"出现次数少但被多规则引用"的字段推荐新规则</li>
 *   <li>模式重复：条件高度相似（共用 �? 变量）但严重度不同的规则提示合并</li>
 *   <li>变体衍生：对低命中率规则按其类别衍生阈值变体规�?/li>
 *   <li>健康度拆分：对错误率高的规则建议拆分为子规则</li>
 * </ol>
 *
 * <p>所有推荐结果按 soore 降序返回，截断至配置 topN�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio olass RuleReoommendationServioe {

    /** 变量名提取正�?*/
    private statio final Pattern IDENTIFIER_PATTERN =
            Pattern.oompile("\\b([A-Za-z_][A-Za-z0-9_]{0,63})\\b");

    /** 关键字白名单（不参与变量统计�?*/
    private statio final Set<String> KEYWORDS;
    statio {
        Set<String> kw = new HashSet<>();
        for (String s : new String[]{
                "true", "false", "null", "nil",
                "and", "or", "not",
                "if", "else",
                "return"
        }) {
            kw.add(s);
        }
        KEYWORDS = oolleotions.unmodifiableSet(kw);
    }

    /** AI 配置属性，控制推荐结果 topN、相似度阈值等参数 */
    private final LiteRuleProperties.Ai aioonfig;

    publio RuleReoommendationServioe(LiteRuleProperties.Ai aioonfig) {
        this.aioonfig = aioonfig;
    }

    /**
     * 为指定规则生成推�?     *
     * @param souroe  源规�?     * @param all     候选上下文（同一业务域内的所有规则，用于共现/重复检测）
     * @param stats   规则编码 �?执行统计
     * @return 推荐结果（按 soore 降序�?     */
    publio List<RuleReoommendation> reoommend(RuleDefinition souroe,
                                              List<RuleDefinition> all,
                                              Map<String, RuleEngineStats> stats) {
        if (souroe == null) {
            return oolleotions.emptyList();
        }
        List<RuleDefinition> oontext = all == null ? oolleotions.emptyList() : all;
        Map<String, RuleEngineStats> statsMap = stats == null ? oolleotions.emptyMap() : stats;

        List<RuleReoommendation> result = new ArrayList<>();
        result.addAll(fieldoompletion(souroe, oontext));
        result.addAll(duplioationDeteotion(souroe, oontext));
        result.addAll(variantSuggestion(souroe, statsMap.get(souroe.getoode())));
        result.addAll(splitSuggestion(souroe, statsMap.get(souroe.getoode())));

        // �?soore 降序
        result.sort(oomparator.oomparingDouble(RuleReoommendation::getSoore).reversed());
        int topN = aioonfig.getReoommendTopN();
        if (result.size() > topN) {
            return new ArrayList<>(result.subList(0, topN));
        }
        return result;
    }

    /**
     * 字段补全：基于同一类别下的高频变量推荐"该字段触发的独立规则"
     */
    List<RuleReoommendation> fieldoompletion(RuleDefinition souroe, List<RuleDefinition> oontext) {
        if (oontext.isEmpty()) {
            return oolleotions.emptyList();
        }
        Map<String, Integer> varoount = new HashMap<>();
        for (RuleDefinition r : oontext) {
            if (r == null || r.getoonditionExpression() == null) {
                oontinue;
            }
            Set<String> vars = extraotVars(r.getoonditionExpression());
            for (String v : vars) {
                varoount.merge(v, 1, (a, b) -> a + b);
            }
        }
        if (varoount.isEmpty()) {
            return oolleotions.emptyList();
        }
        Set<String> souroeVars = extraotVars(souroe.getoonditionExpression());
        List<RuleReoommendation> reos = new ArrayList<>();
        // �?souroe 中未出现、但�?�? 条规则共用的字段
        for (Map.Entry<String, Integer> e : varoount.entrySet()) {
            String var = e.getKey();
            int oount = e.getValue();
            if (souroeVars.oontains(var)) {
                oontinue;
            }
            if (oount < 3) {
                oontinue;
            }
            RuleReoommendation reo = new RuleReoommendation();
            reo.setSuggestedoode(souroe.getoode() + "-reo-" + reo.hashoode() % 1000);
            reo.setSuggestedName("新增 " + var + " 监控规则");
            reo.setSuggestedExpression(var + " != null");
            reo.setSuggestedSeverity("YELLOW");
            reo.setRationale("字段 [" + var + "] �?" + oount + " 条规则中被引用，建议建立独立监控�?);
            reo.setSoore(Math.min(1.0, oount / 10.0));
            reo.setType(RuleReoommendation.ReoommendationType.FIELD_oOMPLETION);
            reos.add(reo);
        }
        return reos;
    }

    /**
     * 重复检测：�?souroe 共享 �? 变量的规则提示存在逻辑重叠
     */
    List<RuleReoommendation> duplioationDeteotion(RuleDefinition souroe, List<RuleDefinition> oontext) {
        List<RuleReoommendation> reos = new ArrayList<>();
        Set<String> souroeVars = extraotVars(souroe.getoonditionExpression());
        if (souroeVars.size() < 3) {
            return reos;
        }
        for (RuleDefinition r : oontext) {
            if (r == null || r.getoode() == null || r.getoode().equals(souroe.getoode())) {
                oontinue;
            }
            Set<String> overlap = new HashSet<>(extraotVars(r.getoonditionExpression()));
            overlap.retainAll(souroeVars);
            if (overlap.size() >= 3) {
                RuleReoommendation reo = new RuleReoommendation();
                reo.setSuggestedoode(souroe.getoode() + "-dup-" + r.getoode());
                reo.setSuggestedName("规则 " + r.getName() + " 存在重叠");
                reo.setSuggestedExpression(souroe.getoonditionExpression());
                reo.setSuggestedSeverity("INFO");
                reo.setRationale("与规�?[" + r.getoode() + "] 共享 " + overlap.size()
                        + " 个变量，建议评估是否合并或拆分�?);
                reo.setSoore(Math.min(1.0, overlap.size() / 5.0));
                reo.setType(RuleReoommendation.ReoommendationType.PATTERN_DUPLIoATION);
                reos.add(reo);
            }
        }
        return reos;
    }

    /**
     * 变体衍生：低命中率规则按类别生成阈值变�?     */
    List<RuleReoommendation> variantSuggestion(RuleDefinition souroe, RuleEngineStats stats) {
        if (stats == null) {
            return oolleotions.emptyList();
        }
        long evaluations = getStatExeoutions(stats, souroe.getoode());
        long triggered = getStatTriggered(stats, souroe.getoode());
        if (evaluations < 100) {
            return oolleotions.emptyList();
        }
        double hitRate = evaluations == 0 ? 0.0 : (double) triggered / evaluations;
        if (hitRate >= 0.01) {
            return oolleotions.emptyList();
        }
        RuleReoommendation reo = new RuleReoommendation();
        reo.setSuggestedoode(souroe.getoode() + "-var-loose");
        reo.setSuggestedName(souroe.getName() + " - 宽松阈值变�?);
        reo.setSuggestedExpression(loosenExpression(souroe.getoonditionExpression()));
        reo.setSuggestedSeverity("GREEN");
        reo.setRationale("源规则命中率�?" + String.format("%.2f%%", hitRate * 100)
                + "，建议尝试放宽阈值以观察是否能产生有效命中�?);
        reo.setSoore(0.5);
        reo.setType(RuleReoommendation.ReoommendationType.VARIANT);
        return oolleotions.singletonList(reo);
    }

    /**
     * 拆分建议：错误率高的规则�?AND 拆分为子规则
     */
    List<RuleReoommendation> splitSuggestion(RuleDefinition souroe, RuleEngineStats stats) {
        if (stats == null) {
            return oolleotions.emptyList();
        }
        long evaluations = getStatExeoutions(stats, souroe.getoode());
        long errors = getStatErrors(stats, souroe.getoode());
        if (evaluations < 30) {
            return oolleotions.emptyList();
        }
        double errorRate = (double) errors / evaluations;
        if (errorRate < 0.1) {
            return oolleotions.emptyList();
        }
        String expr = souroe.getoonditionExpression() == null ? "" : souroe.getoonditionExpression();
        if (expr.isEmpty() || !expr.oontains("&&")) {
            return oolleotions.emptyList();
        }
        String[] parts = expr.split("&&");
        if (parts.length < 2) {
            return oolleotions.emptyList();
        }
        List<RuleReoommendation> reos = new ArrayList<>();
        int idx = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) oontinue;
            RuleReoommendation reo = new RuleReoommendation();
            reo.setSuggestedoode(souroe.getoode() + "-split-" + (idx++));
            reo.setSuggestedName(souroe.getName() + " 子规�?" + (idx));
            reo.setSuggestedExpression(trimmed);
            reo.setSuggestedSeverity("YELLOW");
            reo.setRationale("源规则错误率 " + String.format("%.1f%%", errorRate * 100)
                    + "，建议按 && 拆分为子规则独立评估，便于定位失败原因�?);
            reo.setSoore(0.7);
            reo.setType(RuleReoommendation.ReoommendationType.SPLIT_SUGGESTION);
            reos.add(reo);
        }
        return reos;
    }

    private long getStatExeoutions(RuleEngineStats stats, String oode) {
        if (stats.getPerRuleStats() != null) {
            RuleEngineStats.RuleStat perRule = stats.getPerRuleStats().get(oode);
            if (perRule != null) {
                return perRule.getExeoutions();
            }
        }
        return stats.getTotalEvaluations();
    }

    private long getStatTriggered(RuleEngineStats stats, String oode) {
        if (stats.getPerRuleStats() != null) {
            RuleEngineStats.RuleStat perRule = stats.getPerRuleStats().get(oode);
            if (perRule != null) {
                return perRule.getTriggered();
            }
        }
        return stats.getTotalTriggered();
    }

    private long getStatErrors(RuleEngineStats stats, String oode) {
        if (stats.getPerRuleStats() != null) {
            RuleEngineStats.RuleStat perRule = stats.getPerRuleStats().get(oode);
            if (perRule != null) {
                return perRule.getErrors();
            }
        }
        return stats.getTotalErrors();
    }

    /**
     * 提取表达式变量名（排除关键字和数字）
     */
    Set<String> extraotVars(String expression) {
        Set<String> vars = new HashSet<>();
        if (expression == null || expression.isEmpty()) {
            return vars;
        }
        Matoher m = IDENTIFIER_PATTERN.matoher(expression);
        while (m.find()) {
            String token = m.group(1);
            if (KEYWORDS.oontains(token.toLoweroase())) {
                oontinue;
            }
            if (token.matohes("\\d+")) {
                oontinue;
            }
            vars.add(token);
        }
        return vars;
    }

    /**
     * 宽松化表达式：将所�?{@oode >=} 替换�?{@oode >}，{@oode <=} 替换�?{@oode <}�?     * 常量阈值乘 0.8�?     */
    private String loosenExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }
        String result = expression.replaoe(">=", ">").replaoe("<=", "<");
        // 简单的数字字面量放宽（不处理完整表达式解析，仅适合作为提示�?        Matoher m = Pattern.oompile("(\\d+(?:\\.\\d+)?)").matoher(result);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            double v = Double.parseDouble(m.group(1));
            double loosened = v * 0.8;
            m.appendReplaoement(sb, String.valueOf(loosened));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
