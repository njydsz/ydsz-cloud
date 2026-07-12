package com.njydsz.pmis.literule.server.agent;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * AI Agent 规则节点（P3-5）
 *
 * <p>将 LLM ReAct Agent 封装为规则链节点，实现"规则判断 → Agent 推理 → 规则处置"混合编排。
 * 对标 LiteFlow 将 ReAct Agent 作为规则链节点的能力。
 *
 * <p>本类实现 {@link Rule} 接口，可通过 {@code RuleNode.of(agentRuleNode)} 包装为
 * {@link com.njydsz.pmis.literule.server.orchestrator.RuleNode.NodeType#SINGLE} 节点，
 * 嵌入任意 THEN/WHEN/IF/SWITCH 等规则链中。评估时调用 {@link ReActAgentExecutor}
 * 执行 ReAct 推理循环，并将输出写入上下文（通过 expressionCache）。
 *
 * <p>用户提示词模板支持 {@code ${var}} 变量替换，变量从 {@link RuleContext#getFacts()} 取值。
 *
 * <p>异常降级：LLM 不可用时返回默认结果（"Agent 不可用"），severity=INFO，triggered=true。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Data
public class AgentRuleNode implements Rule {

    private static final Logger log = LoggerFactory.getLogger(AgentRuleNode.class);

    /** Agent 节点 ID（同时作为 ruleCode） */
    private String nodeId;

    /** Agent 名称（同时作为 ruleName） */
    private String agentName;

    /** Agent 系统提示词（角色/约束/输出格式） */
    private String systemPrompt;

    /** 用户提示词模板（支持 ${var} 变量替换，变量从 context.facts 取值） */
    private String userPromptTemplate;

    /** 最大推理迭代次数（默认 3） */
    private int maxIterations = 3;

    /** 可用工具列表（规则编码列表，Agent 可调用其他规则作为工具） */
    private List<String> tools;

    /** 输出变量名（Agent 结果写入 context 的变量名，通过 expressionCache 传递） */
    private String outputVariable;

    /** 超时毫秒（默认 5000ms，0=不超时） */
    private long timeoutMs = 5000L;

    /** ReAct 执行器（由工厂或配置注入） */
    private ReActAgentExecutor executor;

    /** 工具执行回调（ruleCode -> observation 字符串）；为 null 时工具不可用 */
    private Function<String, String> toolExecutor;

    /**
     * 默认构造（用于工厂方法 / JSON 反序列化）
     */
    public AgentRuleNode() {
    }

    /**
     * 全参构造
     */
    public AgentRuleNode(String nodeId, String agentName, String systemPrompt,
                         String userPromptTemplate, int maxIterations, List<String> tools,
                         String outputVariable, long timeoutMs, ReActAgentExecutor executor,
                         Function<String, String> toolExecutor) {
        this.nodeId = nodeId;
        this.agentName = agentName;
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = userPromptTemplate;
        this.maxIterations = maxIterations;
        this.tools = tools;
        this.outputVariable = outputVariable;
        this.timeoutMs = timeoutMs;
        this.executor = executor;
        this.toolExecutor = toolExecutor;
    }

    // ==================== Rule 接口实现 ====================

    @Override
    public String getCode() {
        return nodeId != null ? nodeId : ("agent-" + (agentName != null ? agentName : "default"));
    }

    @Override
    public String getName() {
        return agentName != null ? agentName : "AI Agent 节点";
    }

    @Override
    public String getCategory() {
        return "AGENT";
    }

    /**
     * 评估 Agent 节点
     *
     * <p>执行流程：
     * <ol>
     *   <li>渲染 userPromptTemplate（替换 ${var}）</li>
     *   <li>调用 ReActAgentExecutor 执行 ReAct 循环</li>
     *   <li>将 Agent 输出写入 context（通过 expressionCache，因 RuleContext 不可变）</li>
     *   <li>返回 RuleResult（triggered=true, severity=INFO）</li>
     * </ol>
     *
     * @param context 规则上下文
     * @return Agent 评估结果（始终 triggered=true）
     */
    @Override
    public RuleResult evaluate(RuleContext context) {
        long start = System.currentTimeMillis();

        // 执行器不可用：降级
        if (executor == null) {
            log.warn("[AgentRule] 执行器为 null，降级返回: nodeId={}", nodeId);
            return buildResult(ReActAgentExecutor.DEGRADED_OUTPUT, true, System.currentTimeMillis() - start, 0);
        }

        // 渲染用户提示词
        String userPrompt = renderTemplate(userPromptTemplate, context);

        // 执行 ReAct 循环
        List<String> effectiveTools = tools != null ? tools : Collections.emptyList();
        ReActAgentExecutor.AgentExecutionResult agentResult = executor.execute(
                systemPrompt, userPrompt, effectiveTools, toolExecutor, maxIterations, timeoutMs);

        long elapsedMs = System.currentTimeMillis() - start;

        // 将输出写入 context（通过 expressionCache，因 RuleContext.facts 不可变）
        if (outputVariable != null && !outputVariable.isEmpty()) {
            context.getExpressionCache().put(outputVariable, agentResult.getOutput());
            log.debug("[AgentRule] 输出已写入 context: var={}, nodeId={}", outputVariable, nodeId);
        }

        return buildResult(agentResult.getOutput(), agentResult.isDegraded(), elapsedMs, agentResult.getIterations());
    }

    // ==================== 内部方法 ====================

    /**
     * 渲染提示词模板，将 ${var} 替换为 context.facts 中的值
     *
     * @param template 模板字符串（含 ${var} 占位符）
     * @param context  规则上下文
     * @return 渲染后的字符串；template 为 null 时返回空串
     */
    String renderTemplate(String template, RuleContext context) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String rendered = template;
        Map<String, Object> facts = context.getFacts();
        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (rendered.contains(placeholder)) {
                String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                rendered = rendered.replace(placeholder, value);
            }
        }
        return rendered;
    }

    /**
     * 构建 RuleResult
     */
    private RuleResult buildResult(String output, boolean degraded, long elapsedMs, int iterations) {
        return RuleResult.builder()
                .ruleCode(getCode())
                .ruleName(getName())
                .category("AGENT")
                .triggered(true)
                .severity(RuleSeverity.INFO)
                .title(degraded ? "Agent 降级" : "Agent 推理完成")
                .description(output)
                .currentValue(output)
                .elapsedMs(elapsedMs)
                .drilldownAvailable(false)
                .build();
    }
}
