package com.njydsz.pmis.literule.server.dsl;

import com.njydsz.pmis.literule.api.RuleDefinition;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则 DSL 导出器
 *
 * <p>将引擎中的 {@link RuleDefinition} 列表导出为 YAML 格式的 DSL 文本，
 * 便于版本管理、环境迁移和跨实例共享。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * List<RuleDefinition> rules = ruleAdminService.listAll();
 * String yaml = RuleDslExporter.exportYaml(rules, "risk-rules", "风控规则集");
 *
 * // 导出单条规则
 * RuleDefinition rule = ruleAdminService.getByCode("RISK_001");
 * String singleYaml = RuleDslExporter.exportSingleRule(rule);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
public final class RuleDslExporter {

    private RuleDslExporter() {
    }

    /**
     * 导出规则列表为 YAML DSL
     *
     * @param rules       规则定义列表
     * @param name        DSL 名称（写入 meta.name）
     * @param description DSL 描述（写入 meta.description）
     * @return YAML 格式的 DSL 文本
     */
    public static String exportYaml(List<RuleDefinition> rules, String name, String description) {
        RuleDsl dsl = toDsl(rules, name, description);
        return toYaml(dsl);
    }

    /**
     * 导出单条规则为 YAML DSL
     *
     * @param rule 规则定义
     * @return YAML 格式的 DSL 文本（含单条 rules 段）
     */
    public static String exportSingleRule(RuleDefinition rule) {
        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(rule);
        return exportYaml(rules, rule.getCode(), rule.getName());
    }

    /**
     * 将规则定义列表转换为 DSL 模型
     *
     * @param rules       规则定义列表
     * @param name        DSL 名称
     * @param description DSL 描述
     * @return DSL 模型
     */
    public static RuleDsl toDsl(List<RuleDefinition> rules, String name, String description) {
        List<RuleDslEntry> entries = new ArrayList<>();
        if (rules != null) {
            for (RuleDefinition def : rules) {
                RuleDslEntry entry = toDslEntry(def);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", name != null ? name : "exported-rules");
        meta.put("description", description != null ? description : "");
        meta.put("version", "2.0");
        meta.put("exportedAt", java.time.LocalDateTime.now().toString());
        meta.put("ruleCount", entries.size());

        return RuleDsl.builder()
                .rules(entries)
                .chains(null)
                .meta(meta)
                .build();
    }

    /**
     * 将单条规则定义转换为 DSL 条目
     *
     * @param def 规则定义
     * @return DSL 条目；规则为 null 或类型不支持时返回 null
     */
    public static RuleDslEntry toDslEntry(RuleDefinition def) {
        if (def == null || def.getCode() == null) {
            return null;
        }

        return RuleDslEntry.builder()
                .code(def.getCode())
                .name(def.getName())
                .type("expression")
                .category(def.getCategory())
                .categoryPath(def.getCategoryPath())
                .owner(def.getOwner())
                .description(def.getDescription())
                .priority(def.getPriority())
                .scope(def.getScope())
                .mutexGroup(def.getMutexGroup())
                .enabled(def.isEnabled())
                .version(def.getVersion())
                // expression 专用字段
                .condition(def.getConditionExpression())
                .severityExpression(def.getSeverityExpression())
                .severity(def.getDefaultSeverity() != null ? def.getDefaultSeverity().name() : null)
                .title(def.getTitleTemplate())
                .descriptionTemplate(def.getDescriptionTemplate())
                // 灰度配置
                .canaryRatio(def.getCanaryRatio())
                .canaryConditions(def.getCanaryConditions())
                .canaryConditionExpression(def.getCanaryConditionExpression())
                .canarySeverityExpression(def.getCanarySeverityExpression())
                // 生命周期
                .effectiveFrom(def.getEffectiveFrom())
                .effectiveTo(def.getEffectiveTo())
                .build();
    }

    /**
     * 将 DSL 模型序列化为 YAML 文本
     *
     * @param dsl DSL 模型
     * @return YAML 文本
     */
    public static String toYaml(RuleDsl dsl) {
        Map<String, Object> root = new LinkedHashMap<>();

        // meta 段
        if (dsl.getMeta() != null) {
            root.put("meta", dsl.getMeta());
        }

        // rules 段
        if (dsl.getRules() != null && !dsl.getRules().isEmpty()) {
            List<Map<String, Object>> rulesList = new ArrayList<>();
            for (RuleDslEntry entry : dsl.getRules()) {
                rulesList.add(entryToMap(entry));
            }
            root.put("rules", rulesList);
        }

        // chains 段
        if (dsl.getChains() != null && !dsl.getChains().isEmpty()) {
            List<Map<String, Object>> chainsList = new ArrayList<>();
            for (ChainDslEntry entry : dsl.getChains()) {
                chainsList.add(chainEntryToMap(entry));
            }
            root.put("chains", chainsList);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);

        Yaml yaml = new Yaml(options);
        return yaml.dump(root);
    }

    /**
     * 将 DSL 条目转换为 Map（用于 YAML 序列化）
     */
    private static Map<String, Object> entryToMap(RuleDslEntry entry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", entry.getCode());
        m.put("name", entry.getName());
        if (entry.getType() != null && !"expression".equals(entry.getType())) {
            m.put("type", entry.getType());
        }
        if (entry.getCategory() != null) m.put("category", entry.getCategory());
        if (entry.getCategoryPath() != null) m.put("category_path", entry.getCategoryPath());
        if (entry.getOwner() != null) m.put("owner", entry.getOwner());
        if (entry.getDescription() != null) m.put("description", entry.getDescription());
        if (entry.getPriority() != 100) m.put("priority", entry.getPriority());
        if (entry.getScope() != null) m.put("scope", entry.getScope());
        if (entry.getMutexGroup() != null) m.put("mutex_group", entry.getMutexGroup());
        if (!entry.isEnabled()) m.put("enabled", false);
        if (entry.getVersion() > 1) m.put("version", entry.getVersion());
        // expression 专用
        if (entry.getCondition() != null) m.put("condition", entry.getCondition());
        if (entry.getSeverityExpression() != null) m.put("severity_expression", entry.getSeverityExpression());
        if (entry.getSeverity() != null) m.put("severity", entry.getSeverity());
        if (entry.getTitle() != null) m.put("title", entry.getTitle());
        if (entry.getDescriptionTemplate() != null) m.put("description_template", entry.getDescriptionTemplate());
        // scorecard 专用
        if (entry.getBaseScore() != null) m.put("base_score", entry.getBaseScore());
        if (entry.getDirection() != null) m.put("direction", entry.getDirection());
        if (entry.getMinScore() != null) m.put("min_score", entry.getMinScore());
        if (entry.getMaxScore() != null) m.put("max_score", entry.getMaxScore());
        if (entry.getRedThreshold() != null) m.put("red_threshold", entry.getRedThreshold());
        if (entry.getYellowThreshold() != null) m.put("yellow_threshold", entry.getYellowThreshold());
        if (entry.getFactors() != null && !entry.getFactors().isEmpty()) {
            List<Map<String, Object>> factors = new ArrayList<>();
            for (RuleDslEntry.FactorDsl f : entry.getFactors()) {
                Map<String, Object> fm = new LinkedHashMap<>();
                if (f.getWhen() != null) fm.put("when", f.getWhen());
                if (f.getScore() != null) fm.put("score", f.getScore());
                if (f.getScoreExpr() != null) fm.put("score_expr", f.getScoreExpr());
                if (f.getWeight() != null) fm.put("weight", f.getWeight());
                if (f.getDesc() != null) fm.put("desc", f.getDesc());
                factors.add(fm);
            }
            m.put("factors", factors);
        }
        if (entry.getGrades() != null && !entry.getGrades().isEmpty()) {
            List<Map<String, Object>> grades = new ArrayList<>();
            for (RuleDslEntry.GradeDsl g : entry.getGrades()) {
                Map<String, Object> gm = new LinkedHashMap<>();
                if (g.getLabel() != null) gm.put("label", g.getLabel());
                if (g.getRange() != null) gm.put("range", g.getRange());
                if (g.getSeverity() != null) gm.put("severity", g.getSeverity());
                grades.add(gm);
            }
            m.put("grades", grades);
        }
        // decision_table 专用
        if (entry.getHitPolicy() != null) m.put("hit_policy", entry.getHitPolicy());
        if (entry.getConditionColumns() != null) m.put("condition_columns", entry.getConditionColumns());
        if (entry.getActionColumns() != null) m.put("action_columns", entry.getActionColumns());
        if (entry.getRows() != null) m.put("rows", entry.getRows());
        if (entry.getDefaultActions() != null) m.put("default_actions", entry.getDefaultActions());
        // script 专用
        if (entry.getScriptLanguage() != null) m.put("script_language", entry.getScriptLanguage());
        if (entry.getScriptBody() != null) m.put("script_body", entry.getScriptBody());
        // 灰度
        if (entry.getCanaryRatio() != null && entry.getCanaryRatio() > 0) {
            m.put("canary_ratio", entry.getCanaryRatio());
        }
        if (entry.getCanaryConditions() != null) m.put("canary_conditions", entry.getCanaryConditions());
        if (entry.getCanaryConditionExpression() != null) m.put("canary_condition_expression", entry.getCanaryConditionExpression());
        if (entry.getCanarySeverityExpression() != null) m.put("canary_severity_expression", entry.getCanarySeverityExpression());
        // 生命周期
        if (entry.getEffectiveFrom() != null) m.put("effective_from", entry.getEffectiveFrom());
        if (entry.getEffectiveTo() != null) m.put("effective_to", entry.getEffectiveTo());
        return m;
    }

    /**
     * 将链 DSL 条目转换为 Map
     */
    private static Map<String, Object> chainEntryToMap(ChainDslEntry entry) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (entry.getName() != null) m.put("name", entry.getName());
        if (entry.getType() != null) m.put("type", entry.getType());
        if (entry.getCondition() != null) m.put("condition", entry.getCondition());
        if (entry.getStep() != null) m.put("step", entry.getStep());
        if (entry.getSteps() != null) m.put("steps", entry.getSteps());
        if (entry.getDefaultRule() != null) m.put("default", entry.getDefaultRule());
        if (entry.getBranchKey() != null) m.put("branch_key", entry.getBranchKey());
        if (entry.getBranches() != null) m.put("branches", entry.getBranches());
        if (entry.getIterable() != null) m.put("iterable", entry.getIterable());
        if (entry.getVar() != null) m.put("var", entry.getVar());
        if (entry.getMaxIterations() != null && entry.getMaxIterations() != 100) {
            m.put("max_iterations", entry.getMaxIterations());
        }
        return m;
    }
}
