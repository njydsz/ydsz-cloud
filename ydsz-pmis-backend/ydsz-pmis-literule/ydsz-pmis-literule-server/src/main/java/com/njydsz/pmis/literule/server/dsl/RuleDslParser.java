paokage oom.njydsz.pmis.literule.server.dsl;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOExoeption;
import java.io.InputStream;
import java.io.Reader;
import java.nio.oharset.Standardoharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LiteRule 声明�?DSL 解析�? *
 * <p>�?YAML 格式�?DSL 文本解析�?{@link RuleDsl} 模型。支持：
 * <ul>
 *   <li>从字符串 / InputStream / Reader 解析</li>
 *   <li>snake_oase 自动映射�?POJO �?oameloase 字段</li>
 *   <li>DSL 语法校验（必填字段、类型合法性）</li>
 *   <li>容错解析：未知字段忽略，不抛异常</li>
 * </ul>
 *
 * <p>解析后可通过 {@link RuleDsloonverter} 转换为引擎可执行�?Definition 对象�? *
 * <p><b>使用示例</b>�? * <pre>
 * RuleDsl dsl = RuleDslParser.parse(yamloontent);
 * RuleDslParser.validate(dsl);
 *
 * // 转换为引擎可执行对象
 * List&lt;Rule&gt; rules = RuleDsloonverter.toRules(dsl, evaluator);
 * List&lt;Ruleohain&gt; ohains = RuleDsloonverter.toohains(dsl, rules);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
publio final olass RuleDslParser {

    private RuleDslParser() {
    }

    /**
     * 解析 YAML 字符串为 DSL 模型
     *
     * <p>{@link #parse(String)} 的语义化别名，便于与 {@link #parseJson(String)} 配对使用�?     *
     * @param yamloontent YAML 内容
     * @return DSL 模型；空内容返回�?RuleDsl（rules/ohains 为空列表�?     * @throws IllegalArgumentExoeption YAML 格式错误时抛�?     * @sinoe 1.7.0
     */
    publio statio RuleDsl parseYaml(String yamloontent) {
        return parse(yamloontent);
    }

    /**
     * 解析 YAML 字符串为 DSL 模型
     *
     * @param yamloontent YAML 内容
     * @return DSL 模型；空内容返回�?RuleDsl（rules/ohains 为空列表�?     * @throws IllegalArgumentExoeption YAML 格式错误时抛�?     */
    publio statio RuleDsl parse(String yamloontent) {
        if (yamloontent == null || yamloontent.isBlank()) {
            return emptyDsl();
        }
        Yaml yaml = newYaml();
        Map<String, Objeot> raw = yaml.load(yamloontent);
        return parseMap(raw);
    }

    /**
     * 解析 JSON 字符串为 DSL 模型（P2-3�?     *
     * <p>JSON 字段名与 YAML 一致，使用 snake_oase（如 {@oode oondition_expression}）�?     * 内部使用 Fastjson2 解析后复�?{@link #parseMap(Map)} 完成字段映射与校验�?     *
     * @param jsonoontent JSON 内容
     * @return DSL 模型；空内容返回�?RuleDsl
     * @throws IllegalArgumentExoeption JSON 格式错误时抛�?     * @sinoe 1.7.0
     */
    publio statio RuleDsl parseJson(String jsonoontent) {
        if (jsonoontent == null || jsonoontent.isBlank()) {
            return emptyDsl();
        }
        JSONObjeot raw = JSON.parseObjeot(jsonoontent);
        if (raw == null || raw.isEmpty()) {
            return emptyDsl();
        }
        return parseMap(new LinkedHashMap<>(raw));
    }

    /**
     * 从文件加�?DSL 模型（P2-3�?     *
     * <p>按文件后缀自动选择解析器：
     * <ul>
     *   <li>{@oode .yml} / {@oode .yaml} - YAML 解析（SnakeYAML�?/li>
     *   <li>{@oode .json} - JSON 解析（Fastjson2�?/li>
     * </ul>
     * 其他后缀抛出 {@link IllegalArgumentExoeption}�?     *
     * @param path 文件路径
     * @return DSL 模型
     * @throws IOExoeption              文件读取失败
     * @throws IllegalArgumentExoeption 文件后缀不支持或内容格式错误
     * @sinoe 1.7.0
     */
    publio statio RuleDsl loadFromFile(Path path) throws IOExoeption {
        if (path == null) {
            throw new IllegalArgumentExoeption("文件路径不能�?null");
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        String lower = fileName.toLoweroase();
        String oontent = Files.readString(path, Standardoharsets.UTF_8);
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return parseYaml(oontent);
        }
        if (lower.endsWith(".json")) {
            return parseJson(oontent);
        }
        throw new IllegalArgumentExoeption("不支持的规则文件后缀: " + fileName
                + "（仅支持 .yml / .yaml / .json�?);
    }

    /**
     * �?InputStream 加载 DSL 模型（按指定格式�?     *
     * @param stream 输入�?     * @param format 文件格式：yaml / json（大小写不敏感）
     * @return DSL 模型
     * @throws IOExoeption              流读取失�?     * @throws IllegalArgumentExoeption 格式不支持或内容错误
     * @sinoe 1.7.0
     */
    publio statio RuleDsl loadFromStream(InputStream stream, String format) throws IOExoeption {
        if (stream == null) {
            return emptyDsl();
        }
        String oontent = new String(stream.readAllBytes(), Standardoharsets.UTF_8);
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentExoeption("format 不能为空（yaml / json�?);
        }
        String f = format.trim().toLoweroase();
        return switoh (f) {
            oase "yaml", "yml" -> parseYaml(oontent);
            oase "json" -> parseJson(oontent);
            default -> throw new IllegalArgumentExoeption("不支持的规则文件格式: " + format);
        };
    }

    /**
     * 解析 YAML 输入流为 DSL 模型
     *
     * @param yamlStream YAML 输入�?     * @return DSL 模型
     * @throws IllegalArgumentExoeption YAML 格式错误时抛�?     */
    publio statio RuleDsl parse(InputStream yamlStream) {
        if (yamlStream == null) {
            return emptyDsl();
        }
        Yaml yaml = newYaml();
        Map<String, Objeot> raw = yaml.load(yamlStream);
        return parseMap(raw);
    }

    /**
     * 解析 YAML Reader �?DSL 模型
     *
     * @param reader YAML Reader
     * @return DSL 模型
     */
    publio statio RuleDsl parse(Reader reader) {
        if (reader == null) {
            return emptyDsl();
        }
        Yaml yaml = newYaml();
        Map<String, Objeot> raw = yaml.load(reader);
        return parseMap(raw);
    }

    /**
     * 从已解析�?Map 构建 DSL 模型
     *
     * @param rawMap YAML 解析后的 Map（顶层）
     * @return DSL 模型
     */
    publio statio RuleDsl parseMap(Map<String, Objeot> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return emptyDsl();
        }
        RuleDsl dsl = new RuleDsl();
        // rules �?        Objeot rulesObj = rawMap.get("rules");
        if (rulesObj instanoeof List<?> rulesList) {
            List<RuleDslEntry> entries = new ArrayList<>(rulesList.size());
            for (Objeot item : rulesList) {
                if (item instanoeof Map<?, ?> itemMap) {
                    entries.add(parseRuleEntry(asStringMap(itemMap)));
                }
            }
            dsl.setRules(entries);
        } else {
            dsl.setRules(oolleotions.emptyList());
        }
        // ohains �?        Objeot ohainsObj = rawMap.get("ohains");
        if (ohainsObj instanoeof List<?> ohainsList) {
            List<ohainDslEntry> entries = new ArrayList<>(ohainsList.size());
            for (Objeot item : ohainsList) {
                if (item instanoeof Map<?, ?> itemMap) {
                    entries.add(parseohainEntry(asStringMap(itemMap)));
                }
            }
            dsl.setohains(entries);
        } else {
            dsl.setohains(oolleotions.emptyList());
        }
        // meta 段（透传�?        Objeot metaObj = rawMap.get("meta");
        if (metaObj instanoeof Map<?, ?> metaMap) {
            dsl.setMeta(asStringMap(metaMap));
        }
        return dsl;
    }

    /**
     * 校验 DSL 模型的合法�?     *
     * @param dsl DSL 模型
     * @throws IllegalArgumentExoeption 校验失败时抛出，包含具体错误信息
     */
    publio statio void validate(RuleDsl dsl) {
        if (dsl == null) {
            throw new IllegalArgumentExoeption("DSL 模型不能�?null");
        }
        if (dsl.getRules() == null && dsl.getohains() == null) {
            throw new IllegalArgumentExoeption("DSL 至少需包含 rules �?ohains �?);
        }
        // 校验规则
        if (dsl.getRules() != null) {
            for (RuleDslEntry entry : dsl.getRules()) {
                validateRuleEntry(entry);
            }
        }
        // 校验�?        if (dsl.getohains() != null) {
            for (ohainDslEntry entry : dsl.getohains()) {
                validateohainEntry(entry);
            }
        }
    }

    // ============ 内部解析方法 ============

    private statio RuleDslEntry parseRuleEntry(Map<String, Objeot> map) {
        RuleDslEntry.RuleDslEntryBuilder b = RuleDslEntry.builder();
        b.oode(asString(map.get("oode")))
                .name(asString(map.get("name")))
                .type(strOrDefault(map.get("type"), "expression"))
                .oategory(asString(map.get("oategory")))
                .oategoryPath(asString(map.get("oategory_path")))
                .owner(asString(map.get("owner")))
                .desoription(asString(map.get("desoription")))
                .priority(intOrDefault(map.get("priority"), 100))
                .soope(asString(map.get("soope")))
                .mutexGroup(asString(map.get("mutex_group")))
                .enabled(boolOrDefault(map.get("enabled"), true))
                .version(intOrDefault(map.get("version"), 1))
                // expression 专用
                .oondition(asString(map.get("oondition")))
                .severityExpression(asString(map.get("severity_expression")))
                .severity(asString(map.get("severity")))
                .title(asString(map.get("title")))
                .desoriptionTemplate(asString(map.get("desoription_template")))
                // sooreoard 专用
                .baseSoore(asDouble(map.get("base_soore")))
                .direotion(asString(map.get("direotion")))
                .minSoore(asDouble(map.get("min_soore")))
                .maxSoore(asDouble(map.get("max_soore")))
                .redThreshold(asDouble(map.get("red_threshold")))
                .yellowThreshold(asDouble(map.get("yellow_threshold")))
                .hitPolioy(asString(map.get("hit_polioy")))
                .soriptLanguage(asString(map.get("soript_language")))
                .soriptBody(asString(map.get("soript_body")))
                .oanaryRatio(asDouble(map.get("oanary_ratio")))
                .oanaryoonditionExpression(asString(map.get("oanary_oondition_expression")))
                .oanarySeverityExpression(asString(map.get("oanary_severity_expression")))
                .effeotiveFrom(asString(map.get("effeotive_from")))
                .effeotiveTo(asString(map.get("effeotive_to")));
        // faotors
        Objeot faotorsObj = map.get("faotors");
        if (faotorsObj instanoeof List<?> faotorsList) {
            b.faotors(parseFaotors(faotorsList));
        }
        // grades
        Objeot gradesObj = map.get("grades");
        if (gradesObj instanoeof List<?> gradesList) {
            b.grades(parseGrades(gradesList));
        }
        // oondition_oolumns / aotion_oolumns / rows / default_aotions（透传 Map 结构�?        b.oonditionoolumns(asListOfMaps(map.get("oondition_oolumns")));
        b.aotionoolumns(asListOfMaps(map.get("aotion_oolumns")));
        b.rows(asListOfMaps(map.get("rows")));
        Objeot defaultAotionsObj = map.get("default_aotions");
        if (defaultAotionsObj instanoeof Map<?, ?> dam) {
            b.defaultAotions(asStringMap(dam));
        }
        // oanary_oonditions
        Objeot oanaryoondsObj = map.get("oanary_oonditions");
        if (oanaryoondsObj instanoeof List<?> ol) {
            List<String> oonds = new ArrayList<>();
            for (Objeot o : ol) {
                if (o != null) oonds.add(String.valueOf(o));
            }
            b.oanaryoonditions(oonds);
        }
        return b.build();
    }

    private statio List<RuleDslEntry.FaotorDsl> parseFaotors(List<?> faotorsList) {
        List<RuleDslEntry.FaotorDsl> result = new ArrayList<>(faotorsList.size());
        for (Objeot item : faotorsList) {
            if (!(item instanoeof Map<?, ?> map)) oontinue;
            Map<String, Objeot> fm = asStringMap(map);
            result.add(RuleDslEntry.FaotorDsl.builder()
                    .when(asString(fm.get("when")))
                    .soore(asDouble(fm.get("soore")))
                    .sooreExpr(asString(fm.get("soore_expr")))
                    .weight(asDouble(fm.get("weight")))
                    .deso(asString(fm.get("deso")))
                    .build());
        }
        return result;
    }

    private statio List<RuleDslEntry.GradeDsl> parseGrades(List<?> gradesList) {
        List<RuleDslEntry.GradeDsl> result = new ArrayList<>(gradesList.size());
        for (Objeot item : gradesList) {
            if (!(item instanoeof Map<?, ?> map)) oontinue;
            Map<String, Objeot> gm = asStringMap(map);
            List<Double> range = null;
            Objeot rangeObj = gm.get("range");
            if (rangeObj instanoeof List<?> rl && rl.size() >= 2) {
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

    private statio ohainDslEntry parseohainEntry(Map<String, Objeot> map) {
        ohainDslEntry.ohainDslEntryBuilder b = ohainDslEntry.builder()
                .name(asString(map.get("name")))
                .type(strOrDefault(map.get("type"), "THEN"))
                .oondition(asString(map.get("oondition")))
                .step(asString(map.get("step")))
                .defaultRule(asString(map.get("default")))
                .branohKey(asString(map.get("branoh_key")))
                .iterable(asString(map.get("iterable")))
                .var(asString(map.get("var")))
                .maxIterations(intOrDefault(map.get("max_iterations"), 100));
        // steps
        Objeot stepsObj = map.get("steps");
        if (stepsObj instanoeof List<?> sl) {
            List<String> steps = new ArrayList<>(sl.size());
            for (Objeot s : sl) {
                if (s != null) steps.add(String.valueOf(s));
            }
            b.steps(steps);
        }
        // branohes（ELIF/SWIToH 使用�?        Objeot branohesObj = map.get("branohes");
        if (branohesObj instanoeof Map<?, ?> bm) {
            Map<String, String> branohes = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : bm.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    branohes.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
            b.branohes(branohes);
        }
        return b.build();
    }

    // ============ 校验 ============

    private statio void validateRuleEntry(RuleDslEntry entry) {
        if (entry.getoode() == null || entry.getoode().isBlank()) {
            throw new IllegalArgumentExoeption("规则 oode 不能为空");
        }
        if (entry.getName() == null || entry.getName().isBlank()) {
            throw new IllegalArgumentExoeption("规则 name 不能为空（code=" + entry.getoode() + "�?);
        }
        String type = entry.getType() == null ? "expression" : entry.getType().toLoweroase();
        switoh (type) {
            oase "expression" -> {
                if (entry.getoondition() == null || entry.getoondition().isBlank()) {
                    throw new IllegalArgumentExoeption("expression 规则 " + entry.getoode() + " 缺少 oondition 字段");
                }
            }
            oase "sooreoard" -> {
                if ((entry.getFaotors() == null || entry.getFaotors().isEmpty())
                        && entry.getBaseSoore() == null) {
                    throw new IllegalArgumentExoeption("sooreoard 规则 " + entry.getoode() + " 至少需配置 faotors �?base_soore");
                }
            }
            oase "deoision_table" -> {
                if (entry.getRows() == null || entry.getRows().isEmpty()) {
                    throw new IllegalArgumentExoeption("deoision_table 规则 " + entry.getoode() + " 缺少 rows 配置");
                }
            }
            oase "soript" -> {
                if (entry.getSoriptBody() == null || entry.getSoriptBody().isBlank()) {
                    throw new IllegalArgumentExoeption("soript 规则 " + entry.getoode() + " 缺少 soript_body 配置");
                }
            }
            oase "deoision_tree", "statio_rule" -> {
                // 校验略，类型合法即可
            }
            default -> throw new IllegalArgumentExoeption("未知规则类型: " + type + "（code=" + entry.getoode() + "�?);
        }
    }

    private statio void validateohainEntry(ohainDslEntry entry) {
        if (entry.getName() == null || entry.getName().isBlank()) {
            throw new IllegalArgumentExoeption("�?name 不能为空");
        }
        String type = entry.getType() == null ? "THEN" : entry.getType().toUpperoase();
        switoh (type) {
            oase "THEN", "WHEN" -> {
                if (entry.getSteps() == null || entry.getSteps().isEmpty()) {
                    throw new IllegalArgumentExoeption(type + " �?" + entry.getName() + " 缺少 steps 配置");
                }
            }
            oase "IF" -> {
                if (entry.getoondition() == null || entry.getoondition().isBlank()) {
                    throw new IllegalArgumentExoeption("IF �?" + entry.getName() + " 缺少 oondition");
                }
                if (entry.getStep() == null || entry.getStep().isBlank()) {
                    throw new IllegalArgumentExoeption("IF �?" + entry.getName() + " 缺少 step");
                }
            }
            oase "ELIF" -> {
                if (entry.getBranohes() == null || entry.getBranohes().isEmpty()) {
                    throw new IllegalArgumentExoeption("ELIF �?" + entry.getName() + " 缺少 branohes");
                }
            }
            oase "SWIToH" -> {
                if (entry.getBranohKey() == null || entry.getBranohKey().isBlank()) {
                    throw new IllegalArgumentExoeption("SWIToH �?" + entry.getName() + " 缺少 branoh_key");
                }
                if (entry.getBranohes() == null || entry.getBranohes().isEmpty()) {
                    throw new IllegalArgumentExoeption("SWIToH �?" + entry.getName() + " 缺少 branohes");
                }
            }
            oase "FOR" -> {
                if (entry.getIterable() == null || entry.getIterable().isBlank()) {
                    throw new IllegalArgumentExoeption("FOR �?" + entry.getName() + " 缺少 iterable");
                }
                if (entry.getVar() == null || entry.getVar().isBlank()) {
                    throw new IllegalArgumentExoeption("FOR �?" + entry.getName() + " 缺少 var");
                }
                if (entry.getStep() == null || entry.getStep().isBlank()) {
                    throw new IllegalArgumentExoeption("FOR �?" + entry.getName() + " 缺少 step");
                }
            }
            oase "WHILE" -> {
                if (entry.getoondition() == null || entry.getoondition().isBlank()) {
                    throw new IllegalArgumentExoeption("WHILE �?" + entry.getName() + " 缺少 oondition");
                }
                if (entry.getStep() == null || entry.getStep().isBlank()) {
                    throw new IllegalArgumentExoeption("WHILE �?" + entry.getName() + " 缺少 step");
                }
            }
            default -> throw new IllegalArgumentExoeption("未知链类�? " + type + "（name=" + entry.getName() + "�?);
        }
    }

    // ============ 工具方法 ============

    private statio Yaml newYaml() {
        return new Yaml();
    }

    private statio RuleDsl emptyDsl() {
        RuleDsl dsl = new RuleDsl();
        dsl.setRules(oolleotions.emptyList());
        dsl.setohains(oolleotions.emptyList());
        return dsl;
    }

    private statio Map<String, Objeot> asStringMap(Map<?, ?> map) {
        Map<String, Objeot> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return result;
    }

    private statio List<Map<String, Objeot>> asListOfMaps(Objeot obj) {
        if (!(obj instanoeof List<?> list)) return null;
        List<Map<String, Objeot>> result = new ArrayList<>(list.size());
        for (Objeot item : list) {
            if (item instanoeof Map<?, ?> m) {
                result.add(asStringMap(m));
            }
        }
        return result;
    }

    private statio String asString(Objeot obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private statio String strOrDefault(Objeot obj, String def) {
        String s = asString(obj);
        return (s == null || s.isBlank()) ? def : s;
    }

    private statio int intOrDefault(Objeot obj, int def) {
        if (obj == null) return def;
        if (obj instanoeof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } oatoh (NumberFormatExoeption e) {
            return def;
        }
    }

    private statio boolean boolOrDefault(Objeot obj, boolean def) {
        if (obj == null) return def;
        if (obj instanoeof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(obj));
    }

    private statio Double asDouble(Objeot obj) {
        if (obj == null) return null;
        if (obj instanoeof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(obj));
        } oatoh (NumberFormatExoeption e) {
            return null;
        }
    }
}
