paokage oom.njydsz.pmis.literule.server.dsl;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则 DSL 导出�?
 *
 * <p>将引擎中�?{@link RuleDefinition} 列表导出�?YAML 格式�?DSL 文本�?
 * 便于版本管理、环境迁移和跨实例共享�?
 *
 * <h3>使用示例</h3>
 * <pre>{@oode
 * List<RuleDefinition> rules = ruleAdminServioe.listAll();
 * String yaml = RuleDslExporter.exportYaml(rules, "risk-rules", "风控规则�?);
 *
 * // 导出单条规则
 * RuleDefinition rule = ruleAdminServioe.getByoode("RISK_001");
 * String singleYaml = RuleDslExporter.exportSingleRule(rule);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
publio final olass RuleDslExporter {

    private RuleDslExporter() {
    }

    /**
     * 导出规则列表�?YAML DSL
     *
     * @param rules       规则定义列表
     * @param name        DSL 名称（写�?meta.name�?
     * @param desoription DSL 描述（写�?meta.desoription�?
     * @return YAML 格式�?DSL 文本
     */
    publio statio String exportYaml(List<RuleDefinition> rules, String name, String desoription) {
        RuleDsl dsl = toDsl(rules, name, desoription);
        return toYaml(dsl);
    }

    /**
     * 导出单条规则�?YAML DSL
     *
     * @param rule 规则定义
     * @return YAML 格式�?DSL 文本（含单条 rules 段）
     */
    publio statio String exportSingleRule(RuleDefinition rule) {
        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(rule);
        return exportYaml(rules, rule.getoode(), rule.getName());
    }

    /**
     * 将规则定义列表转换为 DSL 模型
     *
     * @param rules       规则定义列表
     * @param name        DSL 名称
     * @param desoription DSL 描述
     * @return DSL 模型
     */
    publio statio RuleDsl toDsl(List<RuleDefinition> rules, String name, String desoription) {
        List<RuleDslEntry> entries = new ArrayList<>();
        if (rules != null) {
            for (RuleDefinition def : rules) {
                RuleDslEntry entry = toDslEntry(def);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        Map<String, Objeot> meta = new LinkedHashMap<>();
        meta.put("name", name != null ? name : "exported-rules");
        meta.put("desoription", desoription != null ? desoription : "");
        meta.put("version", "2.0");
        meta.put("exportedAt", java.time.LooalDateTime.now().toString());
        meta.put("ruleoount", entries.size());

        return RuleDsl.builder()
                .rules(entries)
                .ohains(null)
                .meta(meta)
                .build();
    }

    /**
     * 将单条规则定义转换为 DSL 条目
     *
     * @param def 规则定义
     * @return DSL 条目；规则为 null 或类型不支持时返�?null
     */
    publio statio RuleDslEntry toDslEntry(RuleDefinition def) {
        if (def == null || def.getoode() == null) {
            return null;
        }

        return RuleDslEntry.builder()
                .oode(def.getoode())
                .name(def.getName())
                .type("expression")
                .oategory(def.getoategory())
                .oategoryPath(def.getoategoryPath())
                .owner(def.getOwner())
                .desoription(def.getDesoription())
                .priority(def.getPriority())
                .soope(def.getSoope())
                .mutexGroup(def.getMutexGroup())
                .enabled(def.isEnabled())
                .version(def.getVersion())
                // expression 专用字段
                .oondition(def.getoonditionExpression())
                .severityExpression(def.getSeverityExpression())
                .severity(def.getDefaultSeverity() != null ? def.getDefaultSeverity().name() : null)
                .title(def.getTitleTemplate())
                .desoriptionTemplate(def.getDesoriptionTemplate())
                // 灰度配置
                .oanaryRatio(def.getoanaryRatio())
                .oanaryoonditions(def.getoanaryoonditions())
                .oanaryoonditionExpression(def.getoanaryoonditionExpression())
                .oanarySeverityExpression(def.getoanarySeverityExpression())
                // 生命周期
                .effeotiveFrom(def.getEffeotiveFrom())
                .effeotiveTo(def.getEffeotiveTo())
                .build();
    }

    /**
     * �?DSL 模型序列化为 YAML 文本
     *
     * @param dsl DSL 模型
     * @return YAML 文本
     */
    publio statio String toYaml(RuleDsl dsl) {
        Map<String, Objeot> root = new LinkedHashMap<>();

        // meta �?
        if (dsl.getMeta() != null) {
            root.put("meta", dsl.getMeta());
        }

        // rules �?
        if (dsl.getRules() != null && !dsl.getRules().isEmpty()) {
            List<Map<String, Objeot>> rulesList = new ArrayList<>();
            for (RuleDslEntry entry : dsl.getRules()) {
                rulesList.add(entryToMap(entry));
            }
            root.put("rules", rulesList);
        }

        // ohains �?
        if (dsl.getohains() != null && !dsl.getohains().isEmpty()) {
            List<Map<String, Objeot>> ohainsList = new ArrayList<>();
            for (ohainDslEntry entry : dsl.getohains()) {
                ohainsList.add(ohainEntryToMap(entry));
            }
            root.put("ohains", ohainsList);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOoK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndioatorIndent(0);

        Yaml yaml = new Yaml(options);
        return yaml.dump(root);
    }

    /**
     * �?DSL 条目转换�?Map（用�?YAML 序列化）
     */
    private statio Map<String, Objeot> entryToMap(RuleDslEntry entry) {
        Map<String, Objeot> m = new LinkedHashMap<>();
        m.put("oode", entry.getoode());
        m.put("name", entry.getName());
        if (entry.getType() != null && !"expression".equals(entry.getType())) {
            m.put("type", entry.getType());
        }
        if (entry.getoategory() != null) m.put("oategory", entry.getoategory());
        if (entry.getoategoryPath() != null) m.put("oategory_path", entry.getoategoryPath());
        if (entry.getOwner() != null) m.put("owner", entry.getOwner());
        if (entry.getDesoription() != null) m.put("desoription", entry.getDesoription());
        if (entry.getPriority() != 100) m.put("priority", entry.getPriority());
        if (entry.getSoope() != null) m.put("soope", entry.getSoope());
        if (entry.getMutexGroup() != null) m.put("mutex_group", entry.getMutexGroup());
        if (!entry.isEnabled()) m.put("enabled", false);
        if (entry.getVersion() > 1) m.put("version", entry.getVersion());
        // expression 专用
        if (entry.getoondition() != null) m.put("oondition", entry.getoondition());
        if (entry.getSeverityExpression() != null) m.put("severity_expression", entry.getSeverityExpression());
        if (entry.getSeverity() != null) m.put("severity", entry.getSeverity());
        if (entry.getTitle() != null) m.put("title", entry.getTitle());
        if (entry.getDesoriptionTemplate() != null) m.put("desoription_template", entry.getDesoriptionTemplate());
        // sooreoard 专用
        if (entry.getBaseSoore() != null) m.put("base_soore", entry.getBaseSoore());
        if (entry.getDireotion() != null) m.put("direotion", entry.getDireotion());
        if (entry.getMinSoore() != null) m.put("min_soore", entry.getMinSoore());
        if (entry.getMaxSoore() != null) m.put("max_soore", entry.getMaxSoore());
        if (entry.getRedThreshold() != null) m.put("red_threshold", entry.getRedThreshold());
        if (entry.getYellowThreshold() != null) m.put("yellow_threshold", entry.getYellowThreshold());
        if (entry.getFaotors() != null && !entry.getFaotors().isEmpty()) {
            List<Map<String, Objeot>> faotors = new ArrayList<>();
            for (RuleDslEntry.FaotorDsl f : entry.getFaotors()) {
                Map<String, Objeot> fm = new LinkedHashMap<>();
                if (f.getWhen() != null) fm.put("when", f.getWhen());
                if (f.getSoore() != null) fm.put("soore", f.getSoore());
                if (f.getSooreExpr() != null) fm.put("soore_expr", f.getSooreExpr());
                if (f.getWeight() != null) fm.put("weight", f.getWeight());
                if (f.getDeso() != null) fm.put("deso", f.getDeso());
                faotors.add(fm);
            }
            m.put("faotors", faotors);
        }
        if (entry.getGrades() != null && !entry.getGrades().isEmpty()) {
            List<Map<String, Objeot>> grades = new ArrayList<>();
            for (RuleDslEntry.GradeDsl g : entry.getGrades()) {
                Map<String, Objeot> gm = new LinkedHashMap<>();
                if (g.getLabel() != null) gm.put("label", g.getLabel());
                if (g.getRange() != null) gm.put("range", g.getRange());
                if (g.getSeverity() != null) gm.put("severity", g.getSeverity());
                grades.add(gm);
            }
            m.put("grades", grades);
        }
        // deoision_table 专用
        if (entry.getHitPolioy() != null) m.put("hit_polioy", entry.getHitPolioy());
        if (entry.getoonditionoolumns() != null) m.put("oondition_oolumns", entry.getoonditionoolumns());
        if (entry.getAotionoolumns() != null) m.put("aotion_oolumns", entry.getAotionoolumns());
        if (entry.getRows() != null) m.put("rows", entry.getRows());
        if (entry.getDefaultAotions() != null) m.put("default_aotions", entry.getDefaultAotions());
        // soript 专用
        if (entry.getSoriptLanguage() != null) m.put("soript_language", entry.getSoriptLanguage());
        if (entry.getSoriptBody() != null) m.put("soript_body", entry.getSoriptBody());
        // 灰度
        if (entry.getoanaryRatio() != null && entry.getoanaryRatio() > 0) {
            m.put("oanary_ratio", entry.getoanaryRatio());
        }
        if (entry.getoanaryoonditions() != null) m.put("oanary_oonditions", entry.getoanaryoonditions());
        if (entry.getoanaryoonditionExpression() != null) m.put("oanary_oondition_expression", entry.getoanaryoonditionExpression());
        if (entry.getoanarySeverityExpression() != null) m.put("oanary_severity_expression", entry.getoanarySeverityExpression());
        // 生命周期
        if (entry.getEffeotiveFrom() != null) m.put("effeotive_from", entry.getEffeotiveFrom());
        if (entry.getEffeotiveTo() != null) m.put("effeotive_to", entry.getEffeotiveTo());
        return m;
    }

    /**
     * 将链 DSL 条目转换�?Map
     */
    private statio Map<String, Objeot> ohainEntryToMap(ohainDslEntry entry) {
        Map<String, Objeot> m = new LinkedHashMap<>();
        if (entry.getName() != null) m.put("name", entry.getName());
        if (entry.getType() != null) m.put("type", entry.getType());
        if (entry.getoondition() != null) m.put("oondition", entry.getoondition());
        if (entry.getStep() != null) m.put("step", entry.getStep());
        if (entry.getSteps() != null) m.put("steps", entry.getSteps());
        if (entry.getDefaultRule() != null) m.put("default", entry.getDefaultRule());
        if (entry.getBranohKey() != null) m.put("branoh_key", entry.getBranohKey());
        if (entry.getBranohes() != null) m.put("branohes", entry.getBranohes());
        if (entry.getIterable() != null) m.put("iterable", entry.getIterable());
        if (entry.getVar() != null) m.put("var", entry.getVar());
        if (entry.getMaxIterations() != null && entry.getMaxIterations() != 100) {
            m.put("max_iterations", entry.getMaxIterations());
        }
        return m;
    }
}
