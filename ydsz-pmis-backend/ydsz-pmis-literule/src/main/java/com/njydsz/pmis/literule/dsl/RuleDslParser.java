package com.njydsz.pmis.literule.dsl;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LiteRule 声明式 DSL 解析器
 *
 * <p>将 YAML 格式的 DSL 文本解析为 {@link RuleDsl} 模型。支持：
 * <ul>
 *   <li>从字符串 / InputStream / Reader 解析</li>
 *   <li>snake_case 自动映射到 POJO 的 camelCase 字段</li>
 *   <li>DSL 语法校验（必填字段、类型合法性）</li>
 *   <li>容错解析：未知字段忽略，不抛异常</li>
 * </ul>
 *
 * <p>解析后可通过 {@link RuleDslConverter} 转换为引擎可执行的 Definition 对象。
 *
 * <p><b>使用示例</b>：
 * <pre>
 * RuleDsl dsl = RuleDslParser.parse(yamlContent);
 * RuleDslParser.validate(dsl);
 *
 * // 转换为引擎可执行对象
 * List&lt;Rule&gt; rules = RuleDslConverter.toRules(dsl, evaluator);
 * List&lt;RuleChain&gt; chains = RuleDslConverter.toChains(dsl, rules);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public final class RuleDslParser {

    private RuleDslParser() {
    }

    /**
     * 解析 YAML 字符串为 DSL 模型
     *
     * @param yamlContent YAML 内容
     * @return DSL 模型；空内容返回空 RuleDsl（rules/chains 为空列表）
     * @throws IllegalArgumentException YAML 格式错误时抛出
     */
    public static RuleDsl parse(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return emptyDsl();
        }
        Yaml yaml = newYaml();
        Map<String, Object> raw = yaml.load(yamlContent);
        return parseMap(raw);
    }

    /**
     * 解析 YAML 输入流为 DSL 模型
     *
     * @param yamlStream YAML 输入流
     * @return DSL 模型
     * @throws IllegalArgumentException YAML 格式错误时抛出
     */
    public static RuleDsl parse(InputStream yamlStream) {
        if (yamlStream == null) {
            return emptyDsl();
        }
        Yaml yaml = newYaml();
        Map<String, Object> raw = yaml.load(yamlStream);
        return parseMap(raw);
    }

    /**
     * 解析 YAML Reader 为 DSL 模型
     *
     * @param reader YAML Reader
     * @return DSL 模型
     */
    public static RuleDsl parse(Reader reader) {
        if (reader == null) {
            return emptyDsl();
        }
        Yaml yaml = newYaml();
        Map<String, Object> raw = yaml.load(reader);
        return parseMap(raw);
    }

    /**
     * 从已解析的 Map 构建 DSL 模型
     *
     * @param rawMap YAML 解析后的 Map（顶层）
     * @return DSL 模型
     */
    public static RuleDsl parseMap(Map<String, Object> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return emptyDsl();
        }
        RuleDsl dsl = new RuleDsl();
        // rules 段
        Object rulesObj = rawMap.get("rules");
        if (rulesObj instanceof List<?> rulesList) {
            List<RuleDslEntry> entries = new ArrayList<>(rulesList.size());
            for (Object item : rulesList) {
                if (item instanceof Map<?, ?> itemMap) {
                    entries.add(parseRuleEntry(asStringMap(itemMap)));
                }
            }
            dsl.setRules(entries);
        } else {
            dsl.setRules(Collections.emptyList());
        }
        // chains 段
        Object chainsObj = rawMap.get("chains");
        if (chainsObj instanceof List<?> chainsList) {
            List<ChainDslEntry> entries = new ArrayList<>(chainsList.size());
            for (Object item : chainsList) {
                if (item instanceof Map<?, ?> itemMap) {
                    entries.add(parseChainEntry(asStringMap(itemMap)));
                }
            }
            dsl.setChains(entries);
        } else {
            dsl.setChains(Collections.emptyList());
        }
        // meta 段（透传）
        Object metaObj = rawMap.get("meta");
        if (metaObj instanceof Map<?, ?> metaMap) {
            dsl.setMeta(asStringMap(metaMap));
        }
        return dsl;
    }

    /**
     * 校验 DSL 模型的合法性
     *
     * @param dsl DSL 模型
     * @throws IllegalArgumentException 校验失败时抛出，包含具体错误信息
     */
    public static void validate(RuleDsl dsl) {
        if (dsl == null) {
            throw new IllegalArgumentException("DSL 模型不能为 null");
        }
        if (dsl.getRules() == null && dsl.getChains() == null) {
            throw new IllegalArgumentException("DSL 至少需包含 rules 或 chains 段");
        }
        // 校验规则
        if (dsl.getRules() != null) {
            for (RuleDslEntry entry : dsl.getRules()) {
                validateRuleEntry(entry);
            }
        }
        // 校验链
        if (dsl.getChains() != null) {
            for (ChainDslEntry entry : dsl.getChains()) {
                validateChainEntry(entry);
            }
        }
    }

    // ============ 内部解析方法 ============

    private static RuleDslEntry parseRuleEntry(Map<String, Object> map) {
        RuleDslEntry.RuleDslEntryBuilder b = RuleDslEntry.builder();
        b.code(asString(map.get("code")))
                .name(asString(map.get("name")))
                .type(strOrDefault(map.get("type"), "expression"))
                .category(asString(map.get("category")))
                .categoryPath(asString(map.get("category_path")))
                .owner(asString(map.get("owner")))
                .description(asString(map.get("description")))
                .priority(intOrDefault(map.get("priority"), 100))
                .scope(asString(map.get("scope")))
                .mutexGroup(asString(map.get("mutex_group")))
                .enabled(boolOrDefault(map.get("enabled"), true))
                .version(intOrDefault(map.get("version"), 1))
                // expression 专用
                .condition(asString(map.get("condition")))
                .severityExpression(asString(map.get("severity_expression")))
                .severity(asString(map.get("severity")))
                .title(asString(map.get("title")))
                .descriptionTemplate(asString(map.get("description_template")))
                // scorecard 专用
                .baseScore(asDouble(map.get("base_score")))
                .direction(asString(map.get("direction")))
                .minScore(asDouble(map.get("min_score")))
                .maxScore(asDouble(map.get("max_score")))
                .redThreshold(asDouble(map.get("red_threshold")))
                .yellowThreshold(asDouble(map.get("yellow_threshold")))
                .hitPolicy(asString(map.get("hit_policy")))
                .scriptLanguage(asString(map.get("script_language")))
                .scriptBody(asString(map.get("script_body")))
                .canaryRatio(asDouble(map.get("canary_ratio")))
                .canaryConditionExpression(asString(map.get("canary_condition_expression")))
                .canarySeverityExpression(asString(map.get("canary_severity_expression")))
                .effectiveFrom(asString(map.get("effective_from")))
                .effectiveTo(asString(map.get("effective_to")));
        // factors
        Object factorsObj = map.get("factors");
        if (factorsObj instanceof List<?> factorsList) {
            b.factors(parseFactors(factorsList));
        }
        // grades
        Object gradesObj = map.get("grades");
        if (gradesObj instanceof List<?> gradesList) {
            b.grades(parseGrades(gradesList));
        }
        // condition_columns / action_columns / rows / default_actions（透传 Map 结构）
        b.conditionColumns(asListOfMaps(map.get("condition_columns")));
        b.actionColumns(asListOfMaps(map.get("action_columns")));
        b.rows(asListOfMaps(map.get("rows")));
        Object defaultActionsObj = map.get("default_actions");
        if (defaultActionsObj instanceof Map<?, ?> dam) {
            b.defaultActions(asStringMap(dam));
        }
        // canary_conditions
        Object canaryCondsObj = map.get("canary_conditions");
        if (canaryCondsObj instanceof List<?> cl) {
            List<String> conds = new ArrayList<>();
            for (Object c : cl) {
                if (c != null) conds.add(String.valueOf(c));
            }
            b.canaryConditions(conds);
        }
        return b.build();
    }

    private static List<RuleDslEntry.FactorDsl> parseFactors(List<?> factorsList) {
        List<RuleDslEntry.FactorDsl> result = new ArrayList<>(factorsList.size());
        for (Object item : factorsList) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> fm = asStringMap(map);
            result.add(RuleDslEntry.FactorDsl.builder()
                    .when(asString(fm.get("when")))
                    .score(asDouble(fm.get("score")))
                    .scoreExpr(asString(fm.get("score_expr")))
                    .weight(asDouble(fm.get("weight")))
                    .desc(asString(fm.get("desc")))
                    .build());
        }
        return result;
    }

    private static List<RuleDslEntry.GradeDsl> parseGrades(List<?> gradesList) {
        List<RuleDslEntry.GradeDsl> result = new ArrayList<>(gradesList.size());
        for (Object item : gradesList) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> gm = asStringMap(map);
            List<Double> range = null;
            Object rangeObj = gm.get("range");
            if (rangeObj instanceof List<?> rl && rl.size() >= 2) {
                range = new ArrayList<>(2);
                range.add(asDouble(rl.get(0)));
                range.add(asDouble(rl.get(1)));
            }
            result.add(RuleDslEntry.GradeDsl.builder()
                    .label(asString(gm.get("label")))
                    .range(range)
                    .severity(asString(gm.get("severity")))
                    .build());
        }
        return result;
    }

    private static ChainDslEntry parseChainEntry(Map<String, Object> map) {
        ChainDslEntry.ChainDslEntryBuilder b = ChainDslEntry.builder()
                .name(asString(map.get("name")))
                .type(strOrDefault(map.get("type"), "THEN"))
                .condition(asString(map.get("condition")))
                .step(asString(map.get("step")))
                .defaultRule(asString(map.get("default")))
                .branchKey(asString(map.get("branch_key")))
                .iterable(asString(map.get("iterable")))
                .var(asString(map.get("var")))
                .maxIterations(intOrDefault(map.get("max_iterations"), 100));
        // steps
        Object stepsObj = map.get("steps");
        if (stepsObj instanceof List<?> sl) {
            List<String> steps = new ArrayList<>(sl.size());
            for (Object s : sl) {
                if (s != null) steps.add(String.valueOf(s));
            }
            b.steps(steps);
        }
        // branches（ELIF/SWITCH 使用）
        Object branchesObj = map.get("branches");
        if (branchesObj instanceof Map<?, ?> bm) {
            Map<String, String> branches = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : bm.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    branches.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
            b.branches(branches);
        }
        return b.build();
    }

    // ============ 校验 ============

    private static void validateRuleEntry(RuleDslEntry entry) {
        if (entry.getCode() == null || entry.getCode().isBlank()) {
            throw new IllegalArgumentException("规则 code 不能为空");
        }
        if (entry.getName() == null || entry.getName().isBlank()) {
            throw new IllegalArgumentException("规则 name 不能为空（code=" + entry.getCode() + "）");
        }
        String type = entry.getType() == null ? "expression" : entry.getType().toLowerCase();
        switch (type) {
            case "expression" -> {
                if (entry.getCondition() == null || entry.getCondition().isBlank()) {
                    throw new IllegalArgumentException("expression 规则 " + entry.getCode() + " 缺少 condition 字段");
                }
            }
            case "scorecard" -> {
                if ((entry.getFactors() == null || entry.getFactors().isEmpty())
                        && entry.getBaseScore() == null) {
                    throw new IllegalArgumentException("scorecard 规则 " + entry.getCode() + " 至少需配置 factors 或 base_score");
                }
            }
            case "decision_table" -> {
                if (entry.getRows() == null || entry.getRows().isEmpty()) {
                    throw new IllegalArgumentException("decision_table 规则 " + entry.getCode() + " 缺少 rows 配置");
                }
            }
            case "script" -> {
                if (entry.getScriptBody() == null || entry.getScriptBody().isBlank()) {
                    throw new IllegalArgumentException("script 规则 " + entry.getCode() + " 缺少 script_body 配置");
                }
            }
            case "decision_tree", "static_rule" -> {
                // 校验略，类型合法即可
            }
            default -> throw new IllegalArgumentException("未知规则类型: " + type + "（code=" + entry.getCode() + "）");
        }
    }

    private static void validateChainEntry(ChainDslEntry entry) {
        if (entry.getName() == null || entry.getName().isBlank()) {
            throw new IllegalArgumentException("链 name 不能为空");
        }
        String type = entry.getType() == null ? "THEN" : entry.getType().toUpperCase();
        switch (type) {
            case "THEN", "WHEN" -> {
                if (entry.getSteps() == null || entry.getSteps().isEmpty()) {
                    throw new IllegalArgumentException(type + " 链 " + entry.getName() + " 缺少 steps 配置");
                }
            }
            case "IF" -> {
                if (entry.getCondition() == null || entry.getCondition().isBlank()) {
                    throw new IllegalArgumentException("IF 链 " + entry.getName() + " 缺少 condition");
                }
                if (entry.getStep() == null || entry.getStep().isBlank()) {
                    throw new IllegalArgumentException("IF 链 " + entry.getName() + " 缺少 step");
                }
            }
            case "ELIF" -> {
                if (entry.getBranches() == null || entry.getBranches().isEmpty()) {
                    throw new IllegalArgumentException("ELIF 链 " + entry.getName() + " 缺少 branches");
                }
            }
            case "SWITCH" -> {
                if (entry.getBranchKey() == null || entry.getBranchKey().isBlank()) {
                    throw new IllegalArgumentException("SWITCH 链 " + entry.getName() + " 缺少 branch_key");
                }
                if (entry.getBranches() == null || entry.getBranches().isEmpty()) {
                    throw new IllegalArgumentException("SWITCH 链 " + entry.getName() + " 缺少 branches");
                }
            }
            case "FOR" -> {
                if (entry.getIterable() == null || entry.getIterable().isBlank()) {
                    throw new IllegalArgumentException("FOR 链 " + entry.getName() + " 缺少 iterable");
                }
                if (entry.getVar() == null || entry.getVar().isBlank()) {
                    throw new IllegalArgumentException("FOR 链 " + entry.getName() + " 缺少 var");
                }
                if (entry.getStep() == null || entry.getStep().isBlank()) {
                    throw new IllegalArgumentException("FOR 链 " + entry.getName() + " 缺少 step");
                }
            }
            case "WHILE" -> {
                if (entry.getCondition() == null || entry.getCondition().isBlank()) {
                    throw new IllegalArgumentException("WHILE 链 " + entry.getName() + " 缺少 condition");
                }
                if (entry.getStep() == null || entry.getStep().isBlank()) {
                    throw new IllegalArgumentException("WHILE 链 " + entry.getName() + " 缺少 step");
                }
            }
            default -> throw new IllegalArgumentException("未知链类型: " + type + "（name=" + entry.getName() + "）");
        }
    }

    // ============ 工具方法 ============

    private static Yaml newYaml() {
        return new Yaml();
    }

    private static RuleDsl emptyDsl() {
        RuleDsl dsl = new RuleDsl();
        dsl.setRules(Collections.emptyList());
        dsl.setChains(Collections.emptyList());
        return dsl;
    }

    private static Map<String, Object> asStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return result;
    }

    private static List<Map<String, Object>> asListOfMaps(Object obj) {
        if (!(obj instanceof List<?> list)) return null;
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                result.add(asStringMap(m));
            }
        }
        return result;
    }

    private static String asString(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private static String strOrDefault(Object obj, String def) {
        String s = asString(obj);
        return (s == null || s.isBlank()) ? def : s;
    }

    private static int intOrDefault(Object obj, int def) {
        if (obj == null) return def;
        if (obj instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean boolOrDefault(Object obj, boolean def) {
        if (obj == null) return def;
        if (obj instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(obj));
    }

    private static Double asDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
