package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleExecutionTrace;
import com.njydsz.pmis.literule.config.RuleAdminService;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionTraceNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则归因分析服务（P3-3 LLM 辅助归因分析）
 *
 * <p>基于 P0-2 表达式追踪能力（{@link RuleAdminService#traceExpression} +
 * {@link ExpressionTraceNode}）和 {@link LLMClient}，为规则触发/未触发生成
 * 人类可读的归因分析报告。
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #analyze(String, Map)} - 按规则编码 + 事实数据归因</li>
 *   <li>{@link #analyze(ExpressionEvaluator.TraceResult, String, String)} - 基于追踪结果归因</li>
 *   <li>{@link #analyzeBatch(List)} - 批量归因</li>
 * </ul>
 *
 * <p>分析逻辑（不依赖 LLM）：
 * <ol>
 *   <li>调用 {@link RuleAdminService#traceExpression} 获取追踪树</li>
 *   <li>递归遍历 {@link ExpressionTraceNode} 树，提取每个 COMPARISON 节点信息</li>
 *   <li>构建 {@link AttributionReport.AttributionFactor} 列表</li>
 *   <li>生成 summary（如"因 amount=1500 > 1000 满足，但 score=750 > 800 不满足，AND 条件不成立"）</li>
 *   <li>如果 LLM 可用，调用 LLM 生成 llmAnalysis 和 recommendation</li>
 * </ol>
 *
 * <p>LLM 降级：LLM 不可用时仍返回基础归因（{@link AttributionReport#getSummary()} +
 * {@link AttributionReport#getFactors()}），llmAnalysis 和 recommendation 为 null。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public class RuleAttributionService {

    private static final Logger log = LoggerFactory.getLogger(RuleAttributionService.class);

    /** 归因分析系统提示词 */
    private static final String ATTRIBUTION_SYSTEM_PROMPT = "你是规则引擎归因分析专家。基于以下规则执行追踪数据，生成："
            + "1. 一段 2-4 句的归因分析，解释规则为何触发/未触发，使用业务语言"
            + "2. 一条优化建议（如调整阈值、增加条件、修改严重度等）"
            + "输出格式为 JSON：{\"analysis\": \"...\", \"recommendation\": \"...\"}，不要输出额外解释。";

    private final RuleAdminService ruleAdminService;
    private final LLMClient llmClient;

    /**
     * 构造归因分析服务
     *
     * @param ruleAdminService 规则管理服务（必需，用于查询规则定义和表达式追踪）
     * @param llmClient        LLM 客户端（可选，为 null 时仅返回基础归因）
     */
    public RuleAttributionService(RuleAdminService ruleAdminService, LLMClient llmClient) {
        this.ruleAdminService = ruleAdminService;
        this.llmClient = llmClient;
    }

    /**
     * 按规则编码 + 事实数据归因分析
     *
     * @param ruleCode 规则编码
     * @param facts    事实数据
     * @return 归因分析报告；规则不存在时返回 ruleCode 匹配但 summary 提示不存在的报告
     */
    public AttributionReport analyze(String ruleCode, Map<String, Object> facts) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return buildErrorReport(null, null, "规则编码不能为空");
        }
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return buildErrorReport(ruleCode, null, "规则不存在: " + ruleCode);
        }

        Map<String, Object> safeFacts = facts != null ? facts : Collections.emptyMap();
        ExpressionEvaluator.TraceResult traceResult = ruleAdminService.traceExpression(
                def.getConditionExpression(), safeFacts);

        AttributionReport report = analyze(traceResult, ruleCode, def.getName());
        report.setSeverity(def.getDefaultSeverity() != null ? def.getDefaultSeverity().name() : null);
        return report;
    }

    /**
     * 基于追踪结果归因分析
     *
     * @param traceResult 表达式追踪结果
     * @param ruleCode    规则编码
     * @param ruleName    规则名称
     * @return 归因分析报告
     */
    public AttributionReport analyze(ExpressionEvaluator.TraceResult traceResult,
                                      String ruleCode, String ruleName) {
        if (traceResult == null) {
            return buildErrorReport(ruleCode, ruleName, "追踪结果为空");
        }

        ExpressionTraceNode root = traceResult.traceTree();
        boolean triggered = traceResult.result();

        // 提取归因因子
        List<AttributionReport.AttributionFactor> factors = new ArrayList<>();
        collectFactors(root, factors, false);

        // 生成摘要
        String summary = buildSummary(root, triggered, factors);

        AttributionReport.AttributionReportBuilder builder = AttributionReport.builder()
                .ruleCode(ruleCode)
                .ruleName(ruleName)
                .triggered(triggered)
                .summary(summary)
                .factors(factors)
                .analyzedAt(LocalDateTime.now());

        // 错误节点：直接返回（不调用 LLM）
        if (root != null && root.getError() != null
                && !"短路跳过".equals(root.getError())) {
            return builder.build();
        }

        // LLM 增强（可选）
        if (llmClient != null && root != null) {
            tryEnrichWithLLM(builder, ruleCode, ruleName, root.getExpression(), triggered, factors);
        }

        return builder.build();
    }

    /**
     * 批量归因分析
     *
     * @param traces 执行轨迹列表
     * @return 归因分析报告列表（与输入顺序一致）
     */
    public List<AttributionReport> analyzeBatch(List<RuleExecutionTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return Collections.emptyList();
        }
        List<AttributionReport> reports = new ArrayList<>(traces.size());
        for (RuleExecutionTrace trace : traces) {
            Map<String, Object> facts = trace.getFactsSnapshot() != null
                    ? trace.getFactsSnapshot() : Collections.emptyMap();
            AttributionReport report = analyze(trace.getRuleCode(), facts);
            report.setRuleName(trace.getRuleName());
            report.setTriggered(trace.isTriggered());
            report.setSeverity(trace.getSeverity());
            reports.add(report);
        }
        return reports;
    }

    // ==================== 内部实现 ====================

    /**
     * 递归遍历追踪树，提取 COMPARISON 节点为归因因子
     *
     * <p>处理顺序说明：
     * <ol>
     *   <li>LOGICAL 节点优先处理：即使自身 shortCircuited=true（表示它短路了右侧子节点），
     *       仍需递归处理子节点以收集左侧已评估的条件因子和右侧被跳过的短路因子</li>
     *   <li>非 LOGICAL 节点（如被跳过的 ROOT/COMPARISON 子节点）：shortCircuited=true 时
     *       标记为短路因子并返回</li>
     *   <li>COMPARISON 节点：提取变量名/当前值/运算符/阈值为归因因子</li>
     * </ol>
     *
     * @param node             当前节点
     * @param factors          归因因子收集列表
     * @param parentShortCircuited 父节点是否短路
     */
    private void collectFactors(ExpressionTraceNode node,
                                 List<AttributionReport.AttributionFactor> factors,
                                 boolean parentShortCircuited) {
        if (node == null) {
            return;
        }

        // LOGICAL 节点优先处理：自身 shortCircuited=true 表示它短路了子节点，需继续递归
        if (node.getNodeType() == ExpressionTraceNode.NodeType.LOGICAL) {
            if (node.getChildren() != null) {
                boolean logicalShortCircuited = node.isShortCircuited();
                for (int i = 0; i < node.getChildren().size(); i++) {
                    ExpressionTraceNode child = node.getChildren().get(i);
                    // AND 短路时右侧子节点被跳过；OR 短路时右侧子节点被跳过
                    boolean childShortCircuited = logicalShortCircuited && i > 0;
                    collectFactors(child, factors, childShortCircuited || parentShortCircuited);
                }
            }
            return;
        }

        // 短路跳过的节点（非 LOGICAL）：标记为短路因子
        if (node.isShortCircuited() || "短路跳过".equals(node.getError())) {
            if (node.getExpression() != null) {
                factors.add(AttributionReport.AttributionFactor.builder()
                        .variable(extractVariableFromExpression(node.getExpression()))
                        .operator(null)
                        .threshold(null)
                        .satisfied(false)
                        .shortCircuited(true)
                        .impact("条件被短路跳过")
                        .build());
            }
            return;
        }

        // 错误节点（非短路）：跳过
        if (node.getError() != null && !"短路跳过".equals(node.getError())) {
            return;
        }

        switch (node.getNodeType()) {
            case COMPARISON -> {
                AttributionReport.AttributionFactor factor = buildFactorFromComparison(node, parentShortCircuited);
                if (factor != null) {
                    factors.add(factor);
                }
            }
            case ROOT -> {
                // ROOT 节点：递归处理子节点（如果有）
                if (node.getChildren() != null) {
                    for (ExpressionTraceNode child : node.getChildren()) {
                        collectFactors(child, factors, parentShortCircuited);
                    }
                }
            }
            default -> {
                // 其他类型节点（VARIABLE/LITERAL/ARITHMETIC 等）：递归处理子节点
                if (node.getChildren() != null) {
                    for (ExpressionTraceNode child : node.getChildren()) {
                        collectFactors(child, factors, parentShortCircuited);
                    }
                }
            }
        }
    }

    /**
     * 从 COMPARISON 节点构建归因因子
     */
    private AttributionReport.AttributionFactor buildFactorFromComparison(ExpressionTraceNode node,
                                                                           boolean parentShortCircuited) {
        String variable = null;
        Object currentValue = null;
        Object threshold = null;

        if (node.getChildren() != null && node.getChildren().size() >= 2) {
            ExpressionTraceNode left = node.getChildren().get(0);
            ExpressionTraceNode right = node.getChildren().get(1);
            if (left.getNodeType() == ExpressionTraceNode.NodeType.VARIABLE) {
                variable = left.getVariableName();
                currentValue = left.getVariableValue();
            }
            if (right.getNodeType() == ExpressionTraceNode.NodeType.LITERAL) {
                threshold = right.getLiteralValue();
            }
        }

        // 兜底：从表达式提取变量名
        if (variable == null && node.getExpression() != null) {
            variable = extractVariableFromExpression(node.getExpression());
        }

        boolean satisfied = Boolean.TRUE.equals(node.getResult());
        return AttributionReport.AttributionFactor.builder()
                .variable(variable)
                .currentValue(currentValue)
                .operator(node.getOperator())
                .threshold(threshold)
                .satisfied(satisfied)
                .shortCircuited(parentShortCircuited)
                .impact(buildImpact(variable, node.getOperator(), satisfied, parentShortCircuited))
                .build();
    }

    /**
     * 从表达式中提取变量名（兜底方案）
     */
    private String extractVariableFromExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        String trimmed = expression.trim();
        // 匹配标识符开头
        int i = 0;
        while (i < trimmed.length()
                && (Character.isLetterOrDigit(trimmed.charAt(i)) || trimmed.charAt(i) == '_')) {
            i++;
        }
        return i > 0 ? trimmed.substring(0, i) : trimmed;
    }

    /**
     * 生成影响描述
     */
    private String buildImpact(String variable, String operator, boolean satisfied, boolean shortCircuited) {
        if (shortCircuited) {
            return "条件被短路跳过";
        }
        if (variable == null) {
            return satisfied ? "条件满足" : "条件不满足";
        }
        String opDesc;
        if (operator == null) {
            opDesc = "";
        } else {
            opDesc = switch (operator) {
                case ">", ">=" -> satisfied ? "超过阈值" : "未超过阈值";
                case "<", "<=" -> satisfied ? "低于阈值" : "不低于阈值";
                case "==" -> satisfied ? "等于阈值" : "不等于阈值";
                case "!=" -> satisfied ? "不等于阈值" : "等于阈值";
                default -> satisfied ? "条件满足" : "条件不满足";
            };
        }
        return variable + opDesc;
    }

    /**
     * 生成归因摘要
     *
     * @param root      追踪树根节点
     * @param triggered 是否触发
     * @param factors   归因因子列表
     * @return 归因摘要文本
     */
    private String buildSummary(ExpressionTraceNode root, boolean triggered,
                                 List<AttributionReport.AttributionFactor> factors) {
        if (root == null) {
            return triggered ? "规则触发" : "规则未触发";
        }

        // 错误节点
        if (root.getError() != null && !"短路跳过".equals(root.getError())) {
            return "规则评估异常: " + root.getError();
        }

        if (factors == null || factors.isEmpty()) {
            return triggered ? "规则触发（无明确归因因子）" : "规则未触发（无明确归因因子）";
        }

        // 按满足/不满足/短路分组
        List<String> satisfiedParts = new ArrayList<>();
        List<String> unsatisfiedParts = new ArrayList<>();
        List<String> shortCircuitedParts = new ArrayList<>();

        for (AttributionReport.AttributionFactor f : factors) {
            String desc = formatFactor(f);
            if (f.isShortCircuited()) {
                shortCircuitedParts.add(desc + "（短路跳过）");
            } else if (f.isSatisfied()) {
                satisfiedParts.add(desc + " 满足");
            } else {
                unsatisfiedParts.add(desc + " 不满足");
            }
        }

        // 根据顶层运算符和触发结果组装摘要
        String logicalOp = extractTopLogicalOperator(root);
        StringBuilder sb = new StringBuilder("因 ");

        if (triggered) {
            // 触发：所有满足的条件促成触发
            if (!satisfiedParts.isEmpty()) {
                sb.append(String.join(logicalOp.equals("||") ? " 或 " : " 且 ", satisfiedParts));
            }
            if (!shortCircuitedParts.isEmpty()) {
                if (!satisfiedParts.isEmpty()) sb.append("，");
                sb.append(String.join("，", shortCircuitedParts));
            }
            if (logicalOp.equals("||") && root.isShortCircuited()) {
                sb.append("（OR 短路）");
            }
            sb.append("，规则触发");
        } else {
            // 未触发：存在不满足的条件
            if (!unsatisfiedParts.isEmpty()) {
                if (!satisfiedParts.isEmpty()) {
                    sb.append(String.join(" 且 ", satisfiedParts)).append("，但 ");
                }
                sb.append(String.join(logicalOp.equals("||") ? " 或 " : " 且 ", unsatisfiedParts));
            }
            if (!shortCircuitedParts.isEmpty()) {
                if (!unsatisfiedParts.isEmpty()) sb.append("，");
                sb.append(String.join("，", shortCircuitedParts));
            }
            if (logicalOp.equals("&&") && root.isShortCircuited()) {
                sb.append("（AND 短路）");
            }
            if (logicalOp.equals("&&")) {
                sb.append("，AND 条件不成立");
            } else if (logicalOp.equals("||")) {
                sb.append("，OR 条件均不成立");
            }
            sb.append("，规则未触发");
        }

        return sb.toString();
    }

    /**
     * 提取顶层逻辑运算符
     */
    private String extractTopLogicalOperator(ExpressionTraceNode root) {
        if (root == null) return "";
        if (root.getNodeType() == ExpressionTraceNode.NodeType.LOGICAL && root.getOperator() != null) {
            return root.getOperator();
        }
        return "";
    }

    /**
     * 格式化归因因子为可读字符串
     */
    private String formatFactor(AttributionReport.AttributionFactor f) {
        StringBuilder sb = new StringBuilder();
        if (f.getVariable() != null) {
            sb.append(f.getVariable()).append("=");
        }
        if (f.getCurrentValue() != null) {
            sb.append(f.getCurrentValue());
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
     * 调用 LLM 生成详细分析和建议
     */
    private void tryEnrichWithLLM(AttributionReport.AttributionReportBuilder builder,
                                    String ruleCode, String ruleName, String expression,
                                    boolean triggered,
                                    List<AttributionReport.AttributionFactor> factors) {
        try {
            String userPrompt = buildLLMUserPrompt(ruleCode, ruleName, expression, triggered, factors);
            String raw = llmClient.chat(ATTRIBUTION_SYSTEM_PROMPT, userPrompt, null);
            if (raw == null || raw.isBlank()) {
                return;
            }
            // 解析 LLM 输出的 JSON
            parseLLMResponse(raw, builder);
        } catch (LLMException e) {
            log.warn("[LLM] 归因分析 LLM 调用失败，降级返回基础归因: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[LLM] 归因分析 LLM 响应解析失败: {}", e.getMessage());
        }
    }

    /**
     * 构建 LLM 用户提示词
     */
    private String buildLLMUserPrompt(String ruleCode, String ruleName, String expression,
                                        boolean triggered,
                                        List<AttributionReport.AttributionFactor> factors) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleCode", ruleCode);
        payload.put("ruleName", ruleName);
        payload.put("conditionExpression", expression);
        payload.put("triggered", triggered);
        List<Map<String, Object>> factorList = new ArrayList<>();
        if (factors != null) {
            for (AttributionReport.AttributionFactor f : factors) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("variable", f.getVariable());
                fm.put("currentValue", f.getCurrentValue());
                fm.put("operator", f.getOperator());
                fm.put("threshold", f.getThreshold());
                fm.put("satisfied", f.isSatisfied());
                fm.put("shortCircuited", f.isShortCircuited());
                factorList.add(fm);
            }
        }
        payload.put("factors", factorList);
        return com.alibaba.fastjson2.JSON.toJSONString(payload);
    }

    /**
     * 解析 LLM 响应（支持 JSON 或纯文本）
     */
    private void parseLLMResponse(String raw, AttributionReport.AttributionReportBuilder builder) {
        String json = extractJsonBlock(raw);
        try {
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
            if (obj != null) {
                String analysis = obj.getString("analysis");
                String recommendation = obj.getString("recommendation");
                if (analysis != null && !analysis.isBlank()) {
                    builder.llmAnalysis(analysis.trim());
                }
                if (recommendation != null && !recommendation.isBlank()) {
                    builder.recommendation(recommendation.trim());
                }
                return;
            }
        } catch (Exception e) {
            // JSON 解析失败，降级为纯文本
            log.debug("[LLM] 归因分析响应非 JSON 格式，降级为纯文本");
        }
        // 降级：将整个响应作为 llmAnalysis
        builder.llmAnalysis(raw.trim());
    }

    /**
     * 提取 JSON 片段（兼容 ```json ... ``` 包裹）
     */
    private String extractJsonBlock(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    /**
     * 构建错误报告
     */
    private AttributionReport buildErrorReport(String ruleCode, String ruleName, String errorMsg) {
        return AttributionReport.builder()
                .ruleCode(ruleCode)
                .ruleName(ruleName)
                .triggered(false)
                .summary(errorMsg)
                .factors(Collections.emptyList())
                .analyzedAt(LocalDateTime.now())
                .build();
    }
}
