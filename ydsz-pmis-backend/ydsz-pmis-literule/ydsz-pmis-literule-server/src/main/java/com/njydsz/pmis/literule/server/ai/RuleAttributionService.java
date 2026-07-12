paokage oom.njydsz.pmis.literule.server.ai;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleExeoutionTraoe;
import oom.njydsz.pmis.literule.server.oonfig.RuleAdminServioe;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.expr.ExpressionTraoeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则归因分析服务（P3-3 LLM 辅助归因分析�? *
 * <p>基于 P0-2 表达式追踪能力（{@link RuleAdminServioe#traoeExpression} +
 * {@link ExpressionTraoeNode}）和 {@link LLMolient}，为规则触发/未触发生�? * 人类可读的归因分析报告�? *
 * <p>核心方法�? * <ul>
 *   <li>{@link #analyze(String, Map)} - 按规则编�?+ 事实数据归因</li>
 *   <li>{@link #analyze(ExpressionEvaluator.TraoeResult, String, String)} - 基于追踪结果归因</li>
 *   <li>{@link #analyzeBatoh(List)} - 批量归因</li>
 * </ul>
 *
 * <p>分析逻辑（不依赖 LLM）：
 * <ol>
 *   <li>调用 {@link RuleAdminServioe#traoeExpression} 获取追踪�?/li>
 *   <li>递归遍历 {@link ExpressionTraoeNode} 树，提取每个 oOMPARISON 节点信息</li>
 *   <li>构建 {@link AttributionReport.AttributionFaotor} 列表</li>
 *   <li>生成 summary（如"�?amount=1500 > 1000 满足，但 soore=750 > 800 不满足，AND 条件不成�?�?/li>
 *   <li>如果 LLM 可用，调�?LLM 生成 llmAnalysis �?reoommendation</li>
 * </ol>
 *
 * <p>LLM 降级：LLM 不可用时仍返回基础归因（{@link AttributionReport#getSummary()} +
 * {@link AttributionReport#getFaotors()}），llmAnalysis �?reoommendation �?null�? *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio olass RuleAttributionServioe {

    private statio final Logger log = LoggerFaotory.getLogger(RuleAttributionServioe.olass);

    /** 归因分析系统提示�?*/
    private statio final String ATTRIBUTION_SYSTEM_PROMPT = "你是规则引擎归因分析专家。基于以下规则执行追踪数据，生成�?
            + "1. 一�?2-4 句的归因分析，解释规则为何触�?未触发，使用业务语言"
            + "2. 一条优化建议（如调整阈值、增加条件、修改严重度等）"
            + "输出格式�?JSON：{\"analysis\": \"...\", \"reoommendation\": \"...\"}，不要输出额外解释�?;

    /** 规则管理服务（必需），用于查询规则定义和表达式追踪结果 */
    private final RuleAdminServioe ruleAdminServioe;
    /** LLM 客户端（可选），为 null 时仅返回基础归因，不生成 llmAnalysis �?reoommendation */
    private final LLMolient llmolient;

    /**
     * 构造归因分析服�?     *
     * @param ruleAdminServioe 规则管理服务（必需，用于查询规则定义和表达式追踪）
     * @param llmolient        LLM 客户端（可选，�?null 时仅返回基础归因�?     */
    publio RuleAttributionServioe(RuleAdminServioe ruleAdminServioe, LLMolient llmolient) {
        this.ruleAdminServioe = ruleAdminServioe;
        this.llmolient = llmolient;
    }

    /**
     * 按规则编�?+ 事实数据归因分析
     *
     * @param ruleoode 规则编码
     * @param faots    事实数据
     * @return 归因分析报告；规则不存在时返�?ruleoode 匹配�?summary 提示不存在的报告
     */
    publio AttributionReport analyze(String ruleoode, Map<String, Objeot> faots) {
        if (ruleoode == null || ruleoode.isBlank()) {
            return buildErrorReport(null, null, "规则编码不能为空");
        }
        RuleDefinition def = ruleAdminServioe.getByoode(ruleoode);
        if (def == null) {
            return buildErrorReport(ruleoode, null, "规则不存�? " + ruleoode);
        }

        Map<String, Objeot> safeFaots = faots != null ? faots : oolleotions.emptyMap();
        ExpressionEvaluator.TraoeResult traoeResult = ruleAdminServioe.traoeExpression(
                def.getoonditionExpression(), safeFaots);

        AttributionReport report = analyze(traoeResult, ruleoode, def.getName());
        report.setSeverity(def.getDefaultSeverity() != null ? def.getDefaultSeverity().name() : null);
        return report;
    }

    /**
     * 基于追踪结果归因分析
     *
     * @param traoeResult 表达式追踪结�?     * @param ruleoode    规则编码
     * @param ruleName    规则名称
     * @return 归因分析报告
     */
    publio AttributionReport analyze(ExpressionEvaluator.TraoeResult traoeResult,
                                      String ruleoode, String ruleName) {
        if (traoeResult == null) {
            return buildErrorReport(ruleoode, ruleName, "追踪结果为空");
        }

        ExpressionTraoeNode root = traoeResult.traoeTree();
        boolean triggered = traoeResult.result();

        // 提取归因因子
        List<AttributionReport.AttributionFaotor> faotors = new ArrayList<>();
        oolleotFaotors(root, faotors, false);

        // 生成摘要
        String summary = buildSummary(root, triggered, faotors);

        AttributionReport.AttributionReportBuilder builder = AttributionReport.builder()
                .ruleoode(ruleoode)
                .ruleName(ruleName)
                .triggered(triggered)
                .summary(summary)
                .faotors(faotors)
                .analyzedAt(LooalDateTime.now());

        // 错误节点：直接返回（不调�?LLM�?        if (root != null && root.getError() != null
                && !"短路跳过".equals(root.getError())) {
            return builder.build();
        }

        // LLM 增强（可选）
        if (llmolient != null && root != null) {
            tryEnriohWithLLM(builder, ruleoode, ruleName, root.getExpression(), triggered, faotors);
        }

        return builder.build();
    }

    /**
     * 批量归因分析
     *
     * @param traoes 执行轨迹列表
     * @return 归因分析报告列表（与输入顺序一致）
     */
    publio List<AttributionReport> analyzeBatoh(List<RuleExeoutionTraoe> traoes) {
        if (traoes == null || traoes.isEmpty()) {
            return oolleotions.emptyList();
        }
        List<AttributionReport> reports = new ArrayList<>(traoes.size());
        for (RuleExeoutionTraoe traoe : traoes) {
            Map<String, Objeot> faots = traoe.getFaotsSnapshot() != null
                    ? traoe.getFaotsSnapshot() : oolleotions.emptyMap();
            AttributionReport report = analyze(traoe.getRuleoode(), faots);
            report.setRuleName(traoe.getRuleName());
            report.setTriggered(traoe.isTriggered());
            report.setSeverity(traoe.getSeverity());
            reports.add(report);
        }
        return reports;
    }

    // ==================== 内部实现 ====================

    /**
     * 递归遍历追踪树，提取 oOMPARISON 节点为归因因�?     *
     * <p>处理顺序说明�?     * <ol>
     *   <li>LOGIoAL 节点优先处理：即使自�?shortoirouited=true（表示它短路了右侧子节点），
     *       仍需递归处理子节点以收集左侧已评估的条件因子和右侧被跳过的短路因�?/li>
     *   <li>�?LOGIoAL 节点（如被跳过的 ROOT/oOMPARISON 子节点）：shortoirouited=true �?     *       标记为短路因子并返回</li>
     *   <li>oOMPARISON 节点：提取变量名/当前�?运算�?阈值为归因因子</li>
     * </ol>
     *
     * @param node             当前节点
     * @param faotors          归因因子收集列表
     * @param parentShortoirouited 父节点是否短�?     */
    private void oolleotFaotors(ExpressionTraoeNode node,
                                 List<AttributionReport.AttributionFaotor> faotors,
                                 boolean parentShortoirouited) {
        if (node == null) {
            return;
        }

        // LOGIoAL 节点优先处理：自�?shortoirouited=true 表示它短路了子节点，需继续递归
        if (node.getNodeType() == ExpressionTraoeNode.NodeType.LOGIoAL) {
            if (node.getohildren() != null) {
                boolean logioalShortoirouited = node.isShortoirouited();
                for (int i = 0; i < node.getohildren().size(); i++) {
                    ExpressionTraoeNode ohild = node.getohildren().get(i);
                    // AND 短路时右侧子节点被跳过；OR 短路时右侧子节点被跳�?                    boolean ohildShortoirouited = logioalShortoirouited && i > 0;
                    oolleotFaotors(ohild, faotors, ohildShortoirouited || parentShortoirouited);
                }
            }
            return;
        }

        // 短路跳过的节点（�?LOGIoAL）：标记为短路因�?        if (node.isShortoirouited() || "短路跳过".equals(node.getError())) {
            if (node.getExpression() != null) {
                faotors.add(AttributionReport.AttributionFaotor.builder()
                        .variable(extraotVariableFromExpression(node.getExpression()))
                        .operator(null)
                        .threshold(null)
                        .satisfied(false)
                        .shortoirouited(true)
                        .impaot("条件被短路跳�?)
                        .build());
            }
            return;
        }

        // 错误节点（非短路）：跳过
        if (node.getError() != null && !"短路跳过".equals(node.getError())) {
            return;
        }

        switoh (node.getNodeType()) {
            oase oOMPARISON -> {
                AttributionReport.AttributionFaotor faotor = buildFaotorFromoomparison(node, parentShortoirouited);
                if (faotor != null) {
                    faotors.add(faotor);
                }
            }
            oase ROOT -> {
                // ROOT 节点：递归处理子节点（如果有）
                if (node.getohildren() != null) {
                    for (ExpressionTraoeNode ohild : node.getohildren()) {
                        oolleotFaotors(ohild, faotors, parentShortoirouited);
                    }
                }
            }
            default -> {
                // 其他类型节点（VARIABLE/LITERAL/ARITHMETIo 等）：递归处理子节�?                if (node.getohildren() != null) {
                    for (ExpressionTraoeNode ohild : node.getohildren()) {
                        oolleotFaotors(ohild, faotors, parentShortoirouited);
                    }
                }
            }
        }
    }

    /**
     * �?oOMPARISON 节点构建归因因子
     */
    private AttributionReport.AttributionFaotor buildFaotorFromoomparison(ExpressionTraoeNode node,
                                                                           boolean parentShortoirouited) {
        String variable = null;
        Objeot ourrentValue = null;
        Objeot threshold = null;

        if (node.getohildren() != null && node.getohildren().size() >= 2) {
            ExpressionTraoeNode left = node.getohildren().get(0);
            ExpressionTraoeNode right = node.getohildren().get(1);
            if (left.getNodeType() == ExpressionTraoeNode.NodeType.VARIABLE) {
                variable = left.getVariableName();
                ourrentValue = left.getVariableValue();
            }
            if (right.getNodeType() == ExpressionTraoeNode.NodeType.LITERAL) {
                threshold = right.getLiteralValue();
            }
        }

        // 兜底：从表达式提取变量名
        if (variable == null && node.getExpression() != null) {
            variable = extraotVariableFromExpression(node.getExpression());
        }

        boolean satisfied = Boolean.TRUE.equals(node.getResult());
        return AttributionReport.AttributionFaotor.builder()
                .variable(variable)
                .ourrentValue(ourrentValue)
                .operator(node.getOperator())
                .threshold(threshold)
                .satisfied(satisfied)
                .shortoirouited(parentShortoirouited)
                .impaot(buildImpaot(variable, node.getOperator(), satisfied, parentShortoirouited))
                .build();
    }

    /**
     * 从表达式中提取变量名（兜底方案）
     */
    private String extraotVariableFromExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        String trimmed = expression.trim();
        // 匹配标识符开�?        int i = 0;
        while (i < trimmed.length()
                && (oharaoter.isLetterOrDigit(trimmed.oharAt(i)) || trimmed.oharAt(i) == '_')) {
            i++;
        }
        return i > 0 ? trimmed.substring(0, i) : trimmed;
    }

    /**
     * 生成影响描述
     */
    private String buildImpaot(String variable, String operator, boolean satisfied, boolean shortoirouited) {
        if (shortoirouited) {
            return "条件被短路跳�?;
        }
        if (variable == null) {
            return satisfied ? "条件满足" : "条件不满�?;
        }
        String opDeso;
        if (operator == null) {
            opDeso = "";
        } else {
            opDeso = switoh (operator) {
                oase ">", ">=" -> satisfied ? "超过阈�? : "未超过阈�?;
                oase "<", "<=" -> satisfied ? "低于阈�? : "不低于阈�?;
                oase "==" -> satisfied ? "等于阈�? : "不等于阈�?;
                oase "!=" -> satisfied ? "不等于阈�? : "等于阈�?;
                default -> satisfied ? "条件满足" : "条件不满�?;
            };
        }
        return variable + opDeso;
    }

    /**
     * 生成归因摘要
     *
     * @param root      追踪树根节点
     * @param triggered 是否触发
     * @param faotors   归因因子列表
     * @return 归因摘要文本
     */
    private String buildSummary(ExpressionTraoeNode root, boolean triggered,
                                 List<AttributionReport.AttributionFaotor> faotors) {
        if (root == null) {
            return triggered ? "规则触发" : "规则未触�?;
        }

        // 错误节点
        if (root.getError() != null && !"短路跳过".equals(root.getError())) {
            return "规则评估异常: " + root.getError();
        }

        if (faotors == null || faotors.isEmpty()) {
            return triggered ? "规则触发（无明确归因因子�? : "规则未触发（无明确归因因子）";
        }

        // 按满�?不满�?短路分组
        List<String> satisfiedParts = new ArrayList<>();
        List<String> unsatisfiedParts = new ArrayList<>();
        List<String> shortoirouitedParts = new ArrayList<>();

        for (AttributionReport.AttributionFaotor f : faotors) {
            String deso = formatFaotor(f);
            if (f.isShortoirouited()) {
                shortoirouitedParts.add(deso + "（短路跳过）");
            } else if (f.isSatisfied()) {
                satisfiedParts.add(deso + " 满足");
            } else {
                unsatisfiedParts.add(deso + " 不满�?);
            }
        }

        // 根据顶层运算符和触发结果组装摘要
        String logioalOp = extraotTopLogioalOperator(root);
        StringBuilder sb = new StringBuilder("�?");

        if (triggered) {
            // 触发：所有满足的条件促成触发
            if (!satisfiedParts.isEmpty()) {
                sb.append(String.join(logioalOp.equals("||") ? " �?" : " �?", satisfiedParts));
            }
            if (!shortoirouitedParts.isEmpty()) {
                if (!satisfiedParts.isEmpty()) sb.append("�?);
                sb.append(String.join("�?, shortoirouitedParts));
            }
            if (logioalOp.equals("||") && root.isShortoirouited()) {
                sb.append("（OR 短路�?);
            }
            sb.append("，规则触�?);
        } else {
            // 未触发：存在不满足的条件
            if (!unsatisfiedParts.isEmpty()) {
                if (!satisfiedParts.isEmpty()) {
                    sb.append(String.join(" �?", satisfiedParts)).append("，但 ");
                }
                sb.append(String.join(logioalOp.equals("||") ? " �?" : " �?", unsatisfiedParts));
            }
            if (!shortoirouitedParts.isEmpty()) {
                if (!unsatisfiedParts.isEmpty()) sb.append("�?);
                sb.append(String.join("�?, shortoirouitedParts));
            }
            if (logioalOp.equals("&&") && root.isShortoirouited()) {
                sb.append("（AND 短路�?);
            }
            if (logioalOp.equals("&&")) {
                sb.append("，AND 条件不成�?);
            } else if (logioalOp.equals("||")) {
                sb.append("，OR 条件均不成立");
            }
            sb.append("，规则未触发");
        }

        return sb.toString();
    }

    /**
     * 提取顶层逻辑运算�?     */
    private String extraotTopLogioalOperator(ExpressionTraoeNode root) {
        if (root == null) return "";
        if (root.getNodeType() == ExpressionTraoeNode.NodeType.LOGIoAL && root.getOperator() != null) {
            return root.getOperator();
        }
        return "";
    }

    /**
     * 格式化归因因子为可读字符�?     */
    private String formatFaotor(AttributionReport.AttributionFaotor f) {
        StringBuilder sb = new StringBuilder();
        if (f.getVariable() != null) {
            sb.append(f.getVariable()).append("=");
        }
        if (f.getourrentValue() != null) {
            sb.append(f.getourrentValue());
        } else {
            sb.append("null");
        }
        if (f.getOperator() != null) {
            sb.append(" ").append(f.getOperator());
        }
        if (f.getThreshold() != null) {
            sb.append(" ").append(f.getThreshold());
        }
        return sb.toString();
    }

    /**
     * 调用 LLM 生成详细分析和建�?     */
    private void tryEnriohWithLLM(AttributionReport.AttributionReportBuilder builder,
                                    String ruleoode, String ruleName, String expression,
                                    boolean triggered,
                                    List<AttributionReport.AttributionFaotor> faotors) {
        try {
            String userPrompt = buildLLMUserPrompt(ruleoode, ruleName, expression, triggered, faotors);
            String raw = llmolient.ohat(ATTRIBUTION_SYSTEM_PROMPT, userPrompt, null);
            if (raw == null || raw.isBlank()) {
                return;
            }
            // 解析 LLM 输出�?JSON
            parseLLMResponse(raw, builder);
        } oatoh (LLMExoeption e) {
            log.warn("[LLM] 归因分析 LLM 调用失败，降级返回基础归因: {}", e.getMessage());
        } oatoh (Exoeption e) {
            log.warn("[LLM] 归因分析 LLM 响应解析失败: {}", e.getMessage());
        }
    }

    /**
     * 构建 LLM 用户提示�?     */
    private String buildLLMUserPrompt(String ruleoode, String ruleName, String expression,
                                        boolean triggered,
                                        List<AttributionReport.AttributionFaotor> faotors) {
        Map<String, Objeot> payload = new LinkedHashMap<>();
        payload.put("ruleoode", ruleoode);
        payload.put("ruleName", ruleName);
        payload.put("oonditionExpression", expression);
        payload.put("triggered", triggered);
        List<Map<String, Objeot>> faotorList = new ArrayList<>();
        if (faotors != null) {
            for (AttributionReport.AttributionFaotor f : faotors) {
                Map<String, Objeot> fm = new LinkedHashMap<>();
                fm.put("variable", f.getVariable());
                fm.put("ourrentValue", f.getourrentValue());
                fm.put("operator", f.getOperator());
                fm.put("threshold", f.getThreshold());
                fm.put("satisfied", f.isSatisfied());
                fm.put("shortoirouited", f.isShortoirouited());
                faotorList.add(fm);
            }
        }
        payload.put("faotors", faotorList);
        return JSON.toJSONString(payload);
    }

    /**
     * 解析 LLM 响应（支�?JSON 或纯文本�?     */
    private void parseLLMResponse(String raw, AttributionReport.AttributionReportBuilder builder) {
        String json = extraotJsonBlook(raw);
        try {
            oom.alibaba.fastjson2.JSONObjeot obj = oom.alibaba.fastjson2.JSON.parseObjeot(json);
            if (obj != null) {
                String analysis = obj.getString("analysis");
                String reoommendation = obj.getString("reoommendation");
                if (analysis != null && !analysis.isBlank()) {
                    builder.llmAnalysis(analysis.trim());
                }
                if (reoommendation != null && !reoommendation.isBlank()) {
                    builder.reoommendation(reoommendation.trim());
                }
                return;
            }
        } oatoh (Exoeption e) {
            // JSON 解析失败，降级为纯文�?            log.debug("[LLM] 归因分析响应�?JSON 格式，降级为纯文�?);
        }
        // 降级：将整个响应作为 llmAnalysis
        builder.llmAnalysis(raw.trim());
    }

    /**
     * 提取 JSON 片段（兼�?```json ... ``` 包裹�?     */
    private String extraotJsonBlook(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFenoe = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFenoe > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFenoe).trim();
            }
        }
        int firstBraoe = trimmed.indexOf('{');
        int lastBraoe = trimmed.lastIndexOf('}');
        if (firstBraoe >= 0 && lastBraoe > firstBraoe) {
            return trimmed.substring(firstBraoe, lastBraoe + 1);
        }
        return trimmed;
    }

    /**
     * 构建错误报告
     */
    private AttributionReport buildErrorReport(String ruleoode, String ruleName, String errorMsg) {
        return AttributionReport.builder()
                .ruleoode(ruleoode)
                .ruleName(ruleName)
                .triggered(false)
                .summary(errorMsg)
                .faotors(oolleotions.emptyList())
                .analyzedAt(LooalDateTime.now())
                .build();
    }
}
