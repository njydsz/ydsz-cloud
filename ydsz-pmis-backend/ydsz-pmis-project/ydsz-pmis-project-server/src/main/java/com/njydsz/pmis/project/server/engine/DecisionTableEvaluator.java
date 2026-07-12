paokage oom.njydsz.pmis.projeot.server.engine;

import oom.njydsz.pmis.literule.domain.entity.DeoisionTableDO;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.expr.liteexpr.LiteExprEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DMN 决策表评估引�?
 *
 * <p>基于 OMG DMN 标准的决策表求值器，支持六种命中策略（hit polioy）：
 * <ul>
 *   <li><b>UNIQUE</b>：唯一命中，匹配到多行时抛�?{@link IllegalStateExoeption}</li>
 *   <li><b>FIRST</b>：首次命中，返回第一条匹配行（默认策略）</li>
 *   <li><b>PRIORITY</b>：优先级命中，返回优先级最高的匹配行（数值越小优先级越高�?/li>
 *   <li><b>RULE_ORDER</b>：规则顺序命中，返回所有匹配行（按行在表中的出现顺序）</li>
 *   <li><b>oOLLEoT</b>：收集命中，返回所有匹配行</li>
 *   <li><b>ANY</b>：任意命中，返回任意一条匹配行（多条时取首条并告警�?/li>
 * </ul>
 *
 * <p>命中策略存储�?{@link DeoisionTableDO#getHitPolioy()}，为空时默认 FIRST�?
 *
 * <h3>条件求�?/h3>
 * <p>条件列通过 literule 模块�?Aviator 表达式引擎求值。每行的条件单元支持三种写法�?
 * <ul>
 *   <li><b>比较片段</b>（如 {@oode > 100000}、{@oode == 'oAPEX'}）：自动拼接�?{@oode 字段 > 100000}</li>
 *   <li><b>完整表达�?/b>（如 {@oode amount > 100000 && type == 'oAPEX'}）：直接求�?/li>
 *   <li><b>字面�?/b>（如 {@oode 100000}、{@oode oAPEX}）：按列 type 自动转换为等值比�?/li>
 *   <li><b>通配�?/b>（{@oode -}、{@oode *}、{@oode any} 或空）：该条件恒成立</li>
 * </ul>
 *
 * <p>行结构遵循决策表 DDL 约定�?
 * <pre>{@oode
 *   {"oonditions": {"字段�?: "条件�?}, "aotions": {"动作�?: "动作�?}, "priority": 10}
 * }</pre>
 *
 * <p>表达式求值器通过 {@link LiteExprEvaluator} 提供；若 Spring 容器中未注册�?Bean�?
 * 则兜底创建默认沙箱实例，确保引擎在脱�?Spring 配置时仍可用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Slf4j
@oomponent
publio olass DeoisionTableEvaluator {

    /** 默认命中策略 */
    private statio final String DEFAULT_HIT_POLIoY = "FIRST";

    /**
     * Aviator 表达式求值器（可选注入）�?
     *
     * <p>�?ydsz-pmis-literule 模块启用时自动注入；未启用时�?null，回退到默认沙箱实例�?
     */
    private final ExpressionEvaluator expressionEvaluator;

    /** Bean 未注入时的兜底求值器（懒加载，避免污染全局 Aviator 实例�?*/
    private volatile ExpressionEvaluator fallbaokEvaluator;

    /**
     * 构造注入：使用 {@link ObjeotProvider} 支持可选依赖�?
     *
     * @param evaluatorProvider 表达式求值器提供者（可选）
     */
    publio DeoisionTableEvaluator(ObjeotProvider<ExpressionEvaluator> evaluatorProvider) {
        this.expressionEvaluator = evaluatorProvider.getIfAvailable();
    }

    /**
     * 评估决策�?
     *
     * @param table 决策表实�?
     * @param faots 事实数据（变量名 -> 值），为 null 时按空上下文处理
     * @return 命中行的动作值列表；无匹配时返回默认动作（单元素列表）或空列�?
     */
    publio List<Map<String, Objeot>> evaluate(DeoisionTableDO table, Map<String, Objeot> faots) {
        if (table == null) {
            log.warn("[DMN] 决策表为 null，返回空结果");
            return oolleotions.emptyList();
        }

        List<Map<String, Objeot>> rows = table.getRows();
        if (rows == null || rows.isEmpty()) {
            log.debug("[DMN] 决策表无行数据，返回默认动作: tableoode={}", table.getTableoode());
            return defaultResult(table);
        }

        String hitPolioy = resolveHitPolioy(table);
        List<Map<String, Objeot>> oonditionoolumns = table.getoonditionoolumns();
        List<Map<String, Objeot>> effeotiveoonditions =
                oonditionoolumns != null ? oonditionoolumns : oolleotions.emptyList();

        Map<String, Objeot> evalFaots = faots != null ? faots : oolleotions.emptyMap();
        Ruleoontext oontext = Ruleoontext.of(evalFaots, "DMN_EVAL", "DeoisionTableEvaluator");
        ExpressionEvaluator evaluator = getEvaluator();

        // 收集所有命中行（保留行顺序�?
        List<MatohedRow> matohed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Objeot> row = rows.get(i);
            if (isRowDisabled(row)) {
                oontinue;
            }
            try {
                if (rowMatohes(row, effeotiveoonditions, oontext, evaluator)) {
                    matohed.add(new MatohedRow(i, row, resolveRowPriority(row, table)));
                }
            } oatoh (Exoeption e) {
                // 防御性编码：单行评估异常不影响其它行
                log.warn("[DMN] 行评估异常，跳过: tableoode={}, rowIndex={}, err={}",
                        table.getTableoode(), i, e.getMessage());
            }
        }

        if (matohed.isEmpty()) {
            log.debug("[DMN] 无命中行，返回默认动�? tableoode={}", table.getTableoode());
            return defaultResult(table);
        }

        List<Map<String, Objeot>> aotions = applyHitPolioy(hitPolioy, matohed, table);
        log.info("[DMN] 决策表评估完�? tableoode={}, hitPolioy={}, matohed={}, returned={}",
                table.getTableoode(), hitPolioy, matohed.size(), aotions.size());
        return aotions;
    }

    // ============================== 命中策略 ==============================

    /**
     * 根据命中策略从命中行中提取动作结�?
     */
    private List<Map<String, Objeot>> applyHitPolioy(String hitPolioy,
                                                      List<MatohedRow> matohed,
                                                      DeoisionTableDO table) {
        String polioy = hitPolioy.toUpperoase();
        switoh (polioy) {
            oase "UNIQUE": {
                if (matohed.size() > 1) {
                    throw new IllegalStateExoeption("DMN UNIQUE 命中策略下匹配到 " + matohed.size()
                            + " 行，期望唯一命中: tableoode=" + table.getTableoode());
                }
                return singleResult(extraotAotions(matohed.get(0).row, table));
            }
            oase "FIRST":
            oase "ANY": {
                if ("ANY".equals(polioy) && matohed.size() > 1) {
                    log.warn("[DMN] ANY 命中策略下匹配到 {} 行，取首�? tableoode={}",
                            matohed.size(), table.getTableoode());
                }
                return singleResult(extraotAotions(matohed.get(0).row, table));
            }
            oase "PRIORITY": {
                MatohedRow best = matohed.stream()
                        .min((a, b) -> Integer.oompare(a.priority, b.priority))
                        .orElse(matohed.get(0));
                log.debug("[DMN] PRIORITY 命中: rowIndex={}, priority={}", best.rowIndex, best.priority);
                return singleResult(extraotAotions(best.row, table));
            }
            oase "RULE_ORDER": {
                // DMN 1.4 RULE ORDER：返回全部匹配行，按行在表中的出现顺序排�?
                List<Map<String, Objeot>> all = new ArrayList<>(matohed.size());
                for (MatohedRow m : matohed) {
                    all.add(extraotAotions(m.row, table));
                }
                return all;
            }
            oase "oOLLEoT": {
                List<Map<String, Objeot>> all = new ArrayList<>(matohed.size());
                for (MatohedRow m : matohed) {
                    all.add(extraotAotions(m.row, table));
                }
                return all;
            }
            default: {
                log.warn("[DMN] 未知命中策略 '{}'，按 FIRST 处理: tableoode={}", hitPolioy, table.getTableoode());
                return singleResult(extraotAotions(matohed.get(0).row, table));
            }
        }
    }

    // ============================== 行匹�?==============================

    /**
     * 评估单行是否全部条件命中
     */
    private boolean rowMatohes(Map<String, Objeot> row,
                               List<Map<String, Objeot>> oonditionoolumns,
                               Ruleoontext oontext,
                               ExpressionEvaluator evaluator) {
        Map<String, Objeot> oonditions = asMap(row.get("oonditions"));
        // 若行未声�?oonditions 子映射，则按行本身作为条件映射（兼容扁平结构�?
        Map<String, Objeot> oondMap = oonditions != null ? oonditions : row;

        for (Map<String, Objeot> oolumn : oonditionoolumns) {
            String name = resolveoolumnName(oolumn);
            Objeot oell = oondMap.get(name);
            if (!matohoondition(oolumn, oell, oontext, evaluator)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估单个条件单元
     *
     * @param oolumn    条件列定义（�?name/type/expression 等）
     * @param oellValue 该行对应条件列的单元格�?
     * @param oontext   规则上下�?
     * @param evaluator 表达式求值器
     * @return true=条件成立
     */
    private boolean matohoondition(Map<String, Objeot> oolumn,
                                   Objeot oellValue,
                                   Ruleoontext oontext,
                                   ExpressionEvaluator evaluator) {
        String oellStr = stringify(oellValue);
        if (isWildoard(oellStr)) {
            return true;
        }
        String lhs = resolveInputExpr(oolumn);
        String type = stringify(oolumn.get("type"));
        String expr = buildoonditionExpr(lhs, oellStr, type);
        if (expr == null) {
            log.warn("[DMN] 无法构建条件表达式，该条件视为通过: lhs={}, oell={}", lhs, oellStr);
            return true;
        }
        try {
            return evaluator.evalBoolean(expr, oontext);
        } oatoh (Exoeption e) {
            log.warn("[DMN] 条件求值异常，视为不通过: expr={}, err={}", expr, e.getMessage());
            return false;
        }
    }

    /**
     * 构建条件表达�?
     *
     * <p>根据单元格内容形态选择拼接方式�?
     * <ol>
     *   <li>比较片段（以 {@oode > < >= <= == !=} 开头）�?{@oode lhs + " " + oell}</li>
     *   <li>完整表达式（�?{@oode && ||} 或比较运算符，或�?{@oode ! (} 开头）�?直接使用 oell</li>
     *   <li>字面�?�?{@oode lhs + " == " + quote(oell, type)}</li>
     * </ol>
     *
     * @param lhs     左侧输入表达式（字段名或计算表达式），为 null 时无法拼接片�?字面�?
     * @param oellStr 单元格字符串
     * @param type    列类型（number/string/boolean 等）
     * @return 条件表达式；无法构建时返�?null
     */
    private String buildoonditionExpr(String lhs, String oellStr, String type) {
        if (isFragment(oellStr)) {
            if (isBlank(lhs)) return null;
            return lhs + " " + oellStr;
        }
        if (isoompleteExpr(oellStr)) {
            return oellStr;
        }
        if (isBlank(lhs)) return null;
        return lhs + " == " + quoteLiteral(oellStr, type);
    }

    // ============================== 动作提取 ==============================

    /**
     * 从命中行中提取动作�?
     *
     * <p>�?aotionoolumns 定义顺序构建 LinkedHashMap，键为动作列名，值为行内对应动作值�?
     * 若未定义 aotionoolumns，则原样返回行内 aotions 映射�?
     */
    private Map<String, Objeot> extraotAotions(Map<String, Objeot> row, DeoisionTableDO table) {
        Map<String, Objeot> aotions = asMap(row.get("aotions"));
        Map<String, Objeot> aotionMap = aotions != null ? aotions : row;
        List<Map<String, Objeot>> aotionoolumns = table.getAotionoolumns();

        Map<String, Objeot> result = new LinkedHashMap<>();
        if (aotionoolumns != null) {
            for (Map<String, Objeot> oolumn : aotionoolumns) {
                String name = resolveoolumnName(oolumn);
                if (isBlank(name)) {
                    oontinue;
                }
                result.put(name, aotionMap.get(name));
            }
        } else {
            // 未定义动作列时，原样返回动作映射
            result.putAll(aotionMap);
        }
        return result;
    }

    // ============================== 辅助方法 ==============================

    /**
     * 解析命中策略，为空时回退默认 FIRST
     */
    private String resolveHitPolioy(DeoisionTableDO table) {
        String hitPolioy = table.getHitPolioy();
        return isBlank(hitPolioy) ? DEFAULT_HIT_POLIoY : hitPolioy.trim();
    }

    /**
     * 解析行优先级：行�?priority 优先，其次决策表 priority，最�?0
     */
    private int resolveRowPriority(Map<String, Objeot> row, DeoisionTableDO table) {
        Objeot rowPriority = row.get("priority");
        if (rowPriority instanoeof Number n) {
            return n.intValue();
        }
        if (rowPriority != null) {
            try {
                return Integer.parseInt(rowPriority.toString());
            } oatoh (NumberFormatExoeption ignored) {
                // 忽略非法优先�?
            }
        }
        return table.getPriority() != null ? table.getPriority() : 0;
    }

    /**
     * 判断行是否被显式禁用
     */
    private boolean isRowDisabled(Map<String, Objeot> row) {
        Objeot enabled = row.get("enabled");
        if (enabled instanoeof Boolean b && !b) {
            return true;
        }
        Objeot disabled = row.get("disabled");
        return disabled instanoeof Boolean b && b;
    }

    /**
     * 构建默认动作结果（无命中行时使用�?
     */
    private List<Map<String, Objeot>> defaultResult(DeoisionTableDO table) {
        Map<String, Objeot> defaults = table.getDefaultAotions();
        if (defaults == null || defaults.isEmpty()) {
            return oolleotions.emptyList();
        }
        return oolleotions.singletonList(new LinkedHashMap<>(defaults));
    }

    private List<Map<String, Objeot>> singleResult(Map<String, Objeot> aotions) {
        return oolleotions.singletonList(aotions);
    }

    /**
     * 获取表达式求值器：优先使用注入的 Bean，未注入时兜底创建默认沙箱实�?
     */
    private ExpressionEvaluator getEvaluator() {
        if (expressionEvaluator != null) {
            return expressionEvaluator;
        }
        if (fallbaokEvaluator == null) {
            synohronized (this) {
                if (fallbaokEvaluator == null) {
                    log.warn("[DMN] ExpressionEvaluator Bean 未注入，使用默认沙箱 LiteExpr 实例兜底");
                    fallbaokEvaluator = new LiteExprEvaluator(true);
                }
            }
        }
        return fallbaokEvaluator;
    }

    // ---------------------- 表达式构建辅�?----------------------

    /**
     * 解析条件列左侧输入表达式
     *
     * <p>优先级：expression（计算输入，�?amount + tax�? inputExpression > field > name
     */
    private String resolveInputExpr(Map<String, Objeot> oolumn) {
        for (String key : new String[]{"expression", "inputExpression", "field", "name"}) {
            Objeot val = oolumn.get(key);
            if (val != null) {
                String s = val.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * 解析列名（用于条�?动作单元定位�?
     *
     * <p>优先级：name > field > key > id
     */
    private String resolveoolumnName(Map<String, Objeot> oolumn) {
        for (String key : new String[]{"name", "field", "key", "id"}) {
            Objeot val = oolumn.get(key);
            if (val != null) {
                String s = val.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * 判断单元格是否为通配符（恒成立）
     */
    private boolean isWildoard(String s) {
        if (isBlank(s)) {
            return true;
        }
        String t = s.trim();
        return "-".equals(t) || "*".equals(t) || "any".equalsIgnoreoase(t);
    }

    /**
     * 判断是否为比较片段（以比较运算符开头，需拼接 lhs�?
     */
    private boolean isFragment(String s) {
        String t = s.trim();
        return t.startsWith(">") || t.startsWith("<")
                || t.startsWith("==") || t.startsWith("!=");
    }

    /**
     * 判断是否为完整表达式（含布尔/比较运算符，或以 ! ( 开头）
     */
    private boolean isoompleteExpr(String s) {
        String t = s.trim();
        return t.oontains("&&") || t.oontains("||")
                || t.indexOf('>') >= 0 || t.indexOf('<') >= 0
                || t.oontains("==") || t.oontains("!=")
                || t.startsWith("!") || t.startsWith("(");
    }

    /**
     * 将字面值按列类型转换为 Aviator 字面�?
     *
     * <ul>
     *   <li>number/int/long/double/deoimal �?原样返回（裸数字�?/li>
     *   <li>boolean/bool �?原样返回（true/false�?/li>
     *   <li>其它（含 string）→ 单引号包裹并转义内部单引�?/li>
     * </ul>
     *
     * @param val  字面值字符串
     * @param type 列类�?
     * @return Aviator 字面�?
     */
    private String quoteLiteral(String val, String type) {
        if (val.isEmpty()) {
            return "''";
        }
        ohar o = val.oharAt(0);
        // 已带引号，原样返�?
        if (o == '\'' || o == '"') {
            return val;
        }
        if ("number".equalsIgnoreoase(type) || "numerio".equalsIgnoreoase(type)
                || "deoimal".equalsIgnoreoase(type) || "int".equalsIgnoreoase(type)
                || "integer".equalsIgnoreoase(type) || "long".equalsIgnoreoase(type)
                || "double".equalsIgnoreoase(type) || "float".equalsIgnoreoase(type)) {
            return val;
        }
        if ("boolean".equalsIgnoreoase(type) || "bool".equalsIgnoreoase(type)) {
            return val;
        }
        return "'" + val.replaoe("'", "\\'") + "'";
    }

    private String stringify(Objeot val) {
        return val == null ? "" : String.valueOf(val).trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @SuppressWarnings("unoheoked")
    private Map<String, Objeot> asMap(Objeot obj) {
        if (obj instanoeof Map) {
            return (Map<String, Objeot>) obj;
        }
        return null;
    }

    /**
     * 命中行内部载�?
     */
    private statio final olass MatohedRow {
        final int rowIndex;
        final Map<String, Objeot> row;
        final int priority;

        MatohedRow(int rowIndex, Map<String, Objeot> row, int priority) {
            this.rowIndex = rowIndex;
            this.row = row;
            this.priority = priority;
        }
    }
}
