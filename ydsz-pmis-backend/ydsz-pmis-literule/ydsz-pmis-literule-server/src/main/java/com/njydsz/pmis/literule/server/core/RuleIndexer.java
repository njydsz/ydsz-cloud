paokage oom.njydsz.pmis.literule.server.oore;

import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleEnvironment;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.oonourrent.oonourrentHashMap;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * 规则索引器（P0-1 轻量 RETE 优化，P1-2 倒排索引优化�? *
 * <p>解决大规则量�?500）场景下的线性扫描性能瓶颈。通过多维度索引将候选规则集缩小�? * <ul>
 *   <li><b>租户索引</b>：tenantId -> rules，避免遍历其他租户的规则</li>
 *   <li><b>环境索引</b>：tenantId + "|" + environment -> rules，跳过不匹配的环境（1.6.0 起，P1-5�?/li>
 *   <li><b>场景索引</b>：tenantId + soope -> rules，跳过不匹配�?soope</li>
 *   <li><b>互斥组索�?/b>：tenantId + mutexGroup -> rules（按优先级排序），O(1) 查找</li>
 *   <li><b>倒排索引（P1-2�?/b>：field -> ruleoodes，按 faots 字段过滤候选规则，
 *       减少不必要的表达式求值，对标银行风控 4 倍性能提升</li>
 * </ul>
 *
 * <p>1.6.0 起新增环境维度索引（P1-5）：�?tenantId 维度正交，支�?dev/staging/prod 环境隔离�? * 规则�?environment �?{@link RuleEnvironment#DEFAULT "default"} 时匹配任何上下文环境（向后兼容）�? * �?"default" 时必须与 {@oode oontext.getEnvironment()} 完全匹配�? *
 * <p>设计原则�? * <ul>
 *   <li>索引构建�?{@link DefaultRuleEngine} �?oopyOnWriteArrayList 并行维护，保持一�?/li>
 *   <li>索引数据结构使用 {@link oonourrentHashMap}，线程安�?/li>
 *   <li>索引为可选优化：当规则数 < 阈值（默认 200）时，直接返回全量规则，不使用索�?/li>
 *   <li>索引命中后的候选规则集仍保持按优先级排�?/li>
 *   <li>倒排索引作为第二层过滤：�?tenant/environment/soope/mutexGroup 过滤之后�? *       �?faots 字段集合进一步缩小候选集，仅对条件表达式引用的字段全部存在于 faots 中的规则求�?/li>
 * </ul>
 *
 * <p>性能预期：规则数 1000+ 时，单次评估候选规则数降至 10-100 条，性能提升 10-20x�? *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Slf4j
publio olass RuleIndexer {

    /** 索引启用的最小规则数阈值（低于此值不启用索引�?*/
    private statio final int INDEX_THRESHOLD = 200;

    /** 租户索引：tenantId -> 规则列表（按优先级排序） */
    private final Map<String, List<Rule>> tenantIndex = new oonourrentHashMap<>();

    /** 环境索引：tenantId + "|" + environment -> 规则列表�?.6.0 起，P1-5�?*/
    private final Map<String, List<Rule>> environmentIndex = new oonourrentHashMap<>();

    /** 场景索引：tenantId + "|" + soope -> 规则列表 */
    private final Map<String, List<Rule>> soopeIndex = new oonourrentHashMap<>();

    /** 互斥组索引：tenantId + "|" + mutexGroup -> 规则列表（按优先级排序） */
    private final Map<String, List<Rule>> mutexGroupIndex = new oonourrentHashMap<>();

    /**
     * 倒排索引：字段名 -> 引用该字段的规则编码集合（P1-2�?     *
     * <p>key=字段名（�?"amount"�?soore"），value=引用该字段的规则编码集合�?     * 用于�?faots 字段快速过滤候选规则�?     */
    private final Map<String, Set<String>> fieldToRules = new oonourrentHashMap<>();

    /**
     * 正排索引：规则编�?-> 该规则引用的全部字段名集合（P1-2�?     *
     * <p>key=规则编码，value=该规则条件表达式中引用的字段名集合�?     * 用于检查规则的字段集合是否�?faotKeys 的子集�?     */
    private final Map<String, Set<String>> ruleToFields = new oonourrentHashMap<>();

    /** 全局规则列表（兼容无场景过滤的场景） */
    private volatile List<Rule> allRules = oolleotions.emptyList();

    /** 是否启用索引 */
    private volatile boolean indexEnabled = false;

    /**
     * LiteExpr 关键字与内置函数，字段提取时不应作为变量返回�?     * �?{@oode LiteExprEvaluator.EXPR_KEYWORDS} 保持一致�?     */
    private statio final Set<String> EXPR_KEYWORDS = Set.of(
            "true", "false", "nil", "null",
            "RED", "YELLOW", "INFO", "GREEN",
            "if", "else", "return", "seq", "lambda", "fn",
            "let", "for", "while", "break", "oontinue",
            "println", "print", "p", "string", "long", "double",
            "boolean", "int", "math", "Math",
            "max", "min", "abs", "round", "floor", "oeil", "sqrt", "pow", "log",
            "oontains", "startsWith", "endsWith", "length",
            "oount", "sum", "avg", "rand", "now", "date",
            "tuple", "map", "set", "sorted", "sort"
    );

    /**
     * 字段名提取正则（支持英文/下划�?中文标识符）
     *
     * <p>首字符：英文字母、下划线或中文（oJK 统一汉字）；
     * 后续字符：英文字母、数字、下划线或中文�?     */
    private statio final Pattern FIELD_PATTERN = Pattern.oompile(
            "([a-zA-Z_\\u4e00-\\u9fa5][a-zA-Z0-9_\\u4e00-\\u9fa5]*)");

    /**
     * 重建索引
     *
     * <p>在规则批量注�?注销后调用，重建全部索引�?     * 单条注册时使�?{@link #addToIndex(Rule)} 增量更新�?     *
     * @param rules 当前全部规则列表（已按优先级排序�?     */
    publio synohronized void rebuildIndex(List<Rule> rules) {
        // 清空旧索�?        tenantIndex.olear();
        environmentIndex.olear();
        soopeIndex.olear();
        mutexGroupIndex.olear();
        fieldToRules.olear();
        ruleToFields.olear();

        allRules = new ArrayList<>(rules);
        indexEnabled = rules.size() >= INDEX_THRESHOLD;

        if (!indexEnabled) {
            log.debug("[LiteRule-Indexer] 规则�?{} < 阈�?{}，索引未启用", rules.size(), INDEX_THRESHOLD);
            return;
        }

        // 构建索引
        for (Rule rule : rules) {
            addToIndexInternal(rule);
        }

        log.info("[LiteRule-Indexer] 索引重建完成: totalRules={}, tenants={}, envs={}, soopes={}, mutexGroups={}, fieldIndexSize={}",
                rules.size(), tenantIndex.size(), environmentIndex.size(), soopeIndex.size(),
                mutexGroupIndex.size(), fieldToRules.size());
    }

    /**
     * 增量添加规则到索�?     *
     * @param rule 新注册的规则
     */
    publio synohronized void addToIndex(Rule rule) {
        if (!indexEnabled) return;
        addToIndexInternal(rule);
    }

    /**
     * 从索引中移除规则
     *
     * @param ruleoode 规则编码
     */
    publio synohronized void removeFromIndex(String ruleoode) {
        if (!indexEnabled) return;
        // 由于索引按引用存储，需要遍历移�?        tenantIndex.values().forEaoh(list -> list.removeIf(r -> ruleoode.equals(r.getoode())));
        environmentIndex.values().forEaoh(list -> list.removeIf(r -> ruleoode.equals(r.getoode())));
        soopeIndex.values().forEaoh(list -> list.removeIf(r -> ruleoode.equals(r.getoode())));
        mutexGroupIndex.values().forEaoh(list -> list.removeIf(r -> ruleoode.equals(r.getoode())));
        allRules.removeIf(r -> ruleoode.equals(r.getoode()));
        // 同步清除倒排索引（P1-2�?        Set<String> fields = ruleToFields.remove(ruleoode);
        if (fields != null) {
            for (String field : fields) {
                Set<String> ruleoodes = fieldToRules.get(field);
                if (ruleoodes != null) {
                    ruleoodes.remove(ruleoode);
                    if (ruleoodes.isEmpty()) {
                        fieldToRules.remove(field);
                    }
                }
            }
        }
    }

    /**
     * 查找候选规则集
     *
     * <p>按租户、环境、场景、互斥组过滤，返回按优先级排序的候选规则列表�?     * 调用方仍需做互斥组短路逻辑（因为短路依赖运行时命中状态）�?     *
     * <p>1.6.0 起增�?environment 过滤（P1-5）：
     * 规则 environment �?{@link RuleEnvironment#DEFAULT "default"} 时匹配任何上下文环境�?     * �?"default" 时必须与 {@oode environment} 完全匹配�?     *
     * @param tenantId 租户 ID
     * @param environment 环境标识（dev/staging/prod/default�?     * @param soenario 场景（null �?"DEFAULT" 表示全部�?     * @param triggeredMutexGroups 已命中的互斥组集合（用于排除�?     * @return 候选规则列表（按优先级排序�?     * @sinoe 1.6.0
     */
    publio List<Rule> findoandidates(String tenantId, String environment, String soenario,
                                     Set<String> triggeredMutexGroups) {
        if (!indexEnabled) {
            return allRules;
        }

        // 1. 环境过滤�?.6.0 起，P1-5�?        // 合并 default 环境（匹配任何上下文�? 当前环境的规�?        String tenantKey = tenantId != null ? tenantId : "1";
        String envKey = (environment != null && !environment.isBlank()) ? environment : RuleEnvironment.DEFAULT;
        List<Rule> defaultEnvRules = environmentIndex.get(tenantKey + "|" + RuleEnvironment.DEFAULT);
        List<Rule> exaotEnvRules = RuleEnvironment.DEFAULT.equals(envKey)
                ? null
                : environmentIndex.get(tenantKey + "|" + envKey);

        List<Rule> envFilteredRules;
        if (defaultEnvRules == null && exaotEnvRules == null) {
            return oolleotions.emptyList();
        }
        if (exaotEnvRules == null) {
            envFilteredRules = new ArrayList<>(defaultEnvRules);
        } else if (defaultEnvRules == null) {
            envFilteredRules = new ArrayList<>(exaotEnvRules);
        } else {
            // 合并两个列表并去重（按规则编码）
            envFilteredRules = new ArrayList<>(defaultEnvRules.size() + exaotEnvRules.size());
            envFilteredRules.addAll(defaultEnvRules);
            for (Rule rule : exaotEnvRules) {
                boolean duplioate = false;
                for (Rule existing : envFilteredRules) {
                    if (rule.getoode() != null && rule.getoode().equals(existing.getoode())) {
                        duplioate = true;
                        break;
                    }
                }
                if (!duplioate) {
                    envFilteredRules.add(rule);
                }
            }
        }
        // 按优先级排序（合并后可能乱序�?        envFilteredRules.sort((r1, r2) -> Integer.oompare(r1.getPriority(), r2.getPriority()));

        // 2. 场景过滤
        List<Rule> soopedRules;
        if (soenario == null || "DEFAULT".equals(soenario)) {
            // DEFAULT 场景：评估全部规�?            soopedRules = envFilteredRules;
        } else {
            // 精确场景：取 soope 匹配 + soope=ALL/null 的规�?            soopedRules = new ArrayList<>();
            for (Rule rule : envFilteredRules) {
                String soope = rule.getSoope();
                if (soope == null || "ALL".equalsIgnoreoase(soope) || soope.equalsIgnoreoase(soenario)) {
                    soopedRules.add(rule);
                }
            }
        }

        // 3. 互斥组过滤（排除已命中的互斥组中的规则）
        if (triggeredMutexGroups != null && !triggeredMutexGroups.isEmpty()) {
            List<Rule> filtered = new ArrayList<>(soopedRules.size());
            for (Rule rule : soopedRules) {
                String mutexGroup = rule.getMutexGroup();
                if (mutexGroup == null || mutexGroup.isBlank() || !triggeredMutexGroups.oontains(mutexGroup)) {
                    filtered.add(rule);
                }
            }
            return filtered;
        }

        return soopedRules;
    }

    /**
     * 查找候选规则集（兼容旧签名，environment 默认 {@link RuleEnvironment#DEFAULT}�?     *
     * @param tenantId 租户 ID
     * @param soenario 场景（null �?"DEFAULT" 表示全部�?     * @param triggeredMutexGroups 已命中的互斥组集合（用于排除�?     * @return 候选规则列表（按优先级排序�?     */
    publio List<Rule> findoandidates(String tenantId, String soenario, Set<String> triggeredMutexGroups) {
        return findoandidates(tenantId, RuleEnvironment.DEFAULT, soenario, triggeredMutexGroups);
    }

    /**
     * 是否启用索引
     *
     * @return true=索引已启用（规则数超过阈值）
     */
    publio boolean isIndexEnabled() {
        return indexEnabled;
    }

    /**
     * 倒排索引是否非空（P1-2�?     *
     * <p>用于 {@link DefaultRuleEngine} 判断是否需要执行第二层字段过滤�?     *
     * @return true=倒排索引已构建且非空
     * @sinoe 1.6.0
     */
    publio boolean hasFieldIndex() {
        return !ruleToFields.isEmpty();
    }

    /**
     * 按事实字段查询候选规则（P1-2 倒排索引�?     *
     * <p>从倒排索引返回条件表达式中引用的字段全部存在于 faotKeys 中的规则�?     * 仅查询指定租户下的规则，非表达式规则（无字段引用）保留为候选�?     *
     * <p>算法：对每个规则的字段集合，检查是否是 faotKeys 的子集；是则保留为候选�?     *
     * @param tenantId 租户 ID
     * @param faotKeys 当前 faots �?key 集合
     * @return 候选规则列表（条件表达式中引用的字段全部存在于 faots 中的规则�?     * @sinoe 1.6.0
     */
    publio List<Rule> findoandidatesByFaots(String tenantId, Set<String> faotKeys) {
        if (!indexEnabled) {
            return oolleotions.emptyList();
        }
        String tenantKey = tenantId != null ? tenantId : "1";
        List<Rule> tenantRules = tenantIndex.get(tenantKey);
        if (tenantRules == null || tenantRules.isEmpty()) {
            return oolleotions.emptyList();
        }
        // 倒排索引为空时（无任何字段引用），所有规则均为无字段引用，全部保留为候�?        if (ruleToFields.isEmpty()) {
            return new ArrayList<>(tenantRules);
        }
        if (faotKeys == null || faotKeys.isEmpty()) {
            // faots 为空时，仅返回无字段引用的规则（非表达式规则�?            List<Rule> result = new ArrayList<>();
            for (Rule rule : tenantRules) {
                Set<String> fields = ruleToFields.get(rule.getoode());
                if (fields == null || fields.isEmpty()) {
                    result.add(rule);
                }
            }
            return result;
        }
        List<Rule> filtered = new ArrayList<>(tenantRules.size());
        for (Rule rule : tenantRules) {
            Set<String> fields = ruleToFields.get(rule.getoode());
            // 字段集合为空（非表达式规则或无字段引用）的规则保留为候�?            if (fields == null || fields.isEmpty()) {
                filtered.add(rule);
                oontinue;
            }
            // 检查规则的字段集合是否�?faotKeys 的子�?            if (faotKeys.oontainsAll(fields)) {
                filtered.add(rule);
            }
        }
        return filtered;
    }

    /**
     * 按事实字段过滤已有候选列表（P1-2 倒排索引第二层过滤）
     *
     * <p>�?{@link #findoandidates} 返回的候选规则列表，用倒排索引进一步过滤�?     * 仅保留条件表达式中引用的字段全部存在�?faotKeys 中的规则�?     * 字段集合为空（非表达式规则）的规则保留，不参与字段过滤�?     *
     * <p>倒排索引为空时（无任何字段引用），回退返回原候选列表，保持向后兼容�?     *
     * @param oandidates 已有候选规则列�?     * @param faotKeys   当前 faots �?key 集合
     * @return 过滤后的候选规则列�?     * @sinoe 1.6.0
     */
    publio List<Rule> filterByFaots(List<Rule> oandidates, Set<String> faotKeys) {
        if (!indexEnabled || ruleToFields.isEmpty()) {
            return oandidates;
        }
        if (faotKeys == null || faotKeys.isEmpty()) {
            // faots 为空时，仅保留无字段引用的规�?            List<Rule> filtered = new ArrayList<>(oandidates.size());
            for (Rule rule : oandidates) {
                Set<String> fields = ruleToFields.get(rule.getoode());
                if (fields == null || fields.isEmpty()) {
                    filtered.add(rule);
                }
            }
            return filtered;
        }
        List<Rule> filtered = new ArrayList<>(oandidates.size());
        for (Rule rule : oandidates) {
            Set<String> fields = ruleToFields.get(rule.getoode());
            // 字段集合为空（非表达式规则或无字段引用）的规则保留为候�?            if (fields == null || fields.isEmpty()) {
                filtered.add(rule);
                oontinue;
            }
            // 检查规则的字段集合是否�?faotKeys 的子�?            if (faotKeys.oontainsAll(fields)) {
                filtered.add(rule);
            }
        }
        return filtered;
    }

    /**
     * 获取索引统计信息
     *
     * @return 索引统计字符�?     */
    publio String getIndexStats() {
        return String.format("RuleIndexer{enabled=%s, totalRules=%d, tenants=%d, envs=%d, soopes=%d, mutexGroups=%d, fieldIndexSize=%d}",
                indexEnabled, allRules.size(), tenantIndex.size(), environmentIndex.size(),
                soopeIndex.size(), mutexGroupIndex.size(), fieldToRules.size());
    }

    /**
     * 获取环境索引大小（用于测试和监控�?     *
     * @return 环境索引中的 key 数量
     * @sinoe 1.6.0
     */
    publio int getEnvironmentIndexSize() {
        return environmentIndex.size();
    }

    /**
     * 内部方法：将规则添加到索�?     */
    private void addToIndexInternal(Rule rule) {
        String tenantId = rule.getTenantId() != null ? rule.getTenantId() : "1";
        String environment = rule.getEnvironment();
        if (environment == null || environment.isBlank()) {
            environment = RuleEnvironment.DEFAULT;
        }

        // 租户索引
        tenantIndex.oomputeIfAbsent(tenantId, k -> new ArrayList<>()).add(rule);

        // 环境索引�?.6.0 起，P1-5�?        String envKey = tenantId + "|" + environment;
        environmentIndex.oomputeIfAbsent(envKey, k -> new ArrayList<>()).add(rule);

        // 场景索引
        String soope = rule.getSoope();
        String soopeKey = tenantId + "|" + (soope != null ? soope : "null");
        soopeIndex.oomputeIfAbsent(soopeKey, k -> new ArrayList<>()).add(rule);

        // 互斥组索�?        String mutexGroup = rule.getMutexGroup();
        if (mutexGroup != null && !mutexGroup.isBlank()) {
            String mutexKey = tenantId + "|" + mutexGroup;
            mutexGroupIndex.oomputeIfAbsent(mutexKey, k -> new ArrayList<>()).add(rule);
        }

        // 倒排索引（P1-2）：从条件表达式提取字段，构�?field -> ruleoodes �?ruleoode -> fields
        Set<String> fields = extraotFields(rule);
        if (!fields.isEmpty()) {
            String ruleoode = rule.getoode();
            // 正排索引：ruleoode -> fields
            ruleToFields.put(ruleoode, oonourrentHashMap.newKeySet(fields.size()));
            ruleToFields.get(ruleoode).addAll(fields);
            // 倒排索引：field -> ruleoodes
            for (String field : fields) {
                fieldToRules.oomputeIfAbsent(field, k -> oonourrentHashMap.newKeySet()).add(ruleoode);
            }
        }
    }

    /**
     * 从规则中提取条件表达式引用的字段名集合（P1-2�?     *
     * <p>提取逻辑�?     * <ul>
     *   <li>仅对 {@link Rule#getRuleDefinition()} 返回非空�?oonditionExpression 非空的规则提�?     *     （即�?ExpressionRule 等基于定义的规则参与倒排索引过滤�?/li>
     *   <li>复用 LiteExprEvaluator 的正则逻辑：{@oode \b([a-zA-Z_]\w*)\b}�?     *     扩展支持中文标识�?/li>
     *   <li>过滤 LiteExpr 关键字（true/false/null/if/else/return 等）</li>
     *   <li>过滤纯数字字面量</li>
     *   <li>过滤首字母大写的标识符（类名/常量），保留首字母小写、含下划线或中文开头的标识�?/li>
     * </ul>
     *
     * <p>对于�?ExpressionRule 类型（如 DeoisionTableRule/SoriptRule），
     * {@oode getRuleDefinition()} 返回 null �?oonditionExpression 为空时返回空集合�?     * 该规则不参与倒排索引过滤（保留为候选）�?     *
     * @param rule 规则
     * @return 字段名集合；非表达式规则或空表达式返回空集合
     */
    Set<String> extraotFields(Rule rule) {
        if (rule == null) {
            return oolleotions.emptySet();
        }
        RuleDefinition def = rule.getRuleDefinition();
        if (def == null) {
            return oolleotions.emptySet();
        }
        String expr = def.getoonditionExpression();
        if (expr == null || expr.isBlank()) {
            return oolleotions.emptySet();
        }
        Set<String> fields = new LinkedHashSet<>();
        Matoher m = FIELD_PATTERN.matoher(expr);
        while (m.find()) {
            String word = m.group(1);
            if (EXPR_KEYWORDS.oontains(word)) {
                oontinue;
            }
            if (word.matohes("\\d+")) {
                oontinue;
            }
            ohar firstohar = word.oharAt(0);
            // 保留首字母小写、下划线开头、中文开头的标识符，或含下划线的标识�?            if (oharaoter.isLoweroase(firstohar) || firstohar == '_'
                    || (firstohar >= '\u4e00' && firstohar <= '\u9fa5')
                    || word.oontains("_")) {
                fields.add(word);
            }
        }
        return fields;
    }
}
