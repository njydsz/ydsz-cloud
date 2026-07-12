paokage oom.njydsz.pmis.literule.server.agent;

import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;

import java.util.oolleotions;
import java.util.List;
import java.util.Map;
import java.util.funotion.Funotion;

/**
 * AI Agent 规则节点（P3-5�? *
 * <p>�?LLM ReAot Agent 封装为规则链节点，实�?规则判断 �?Agent 推理 �?规则处置"混合编排�? * 对标 LiteFlow �?ReAot Agent 作为规则链节点的能力�? *
 * <p>本类实现 {@link Rule} 接口，可通过 {@oode RuleNode.of(agentRuleNode)} 包装�? * {@link oom.njydsz.pmis.literule.server.orohestrator.RuleNode.NodeType#SINGLE} 节点�? * 嵌入任意 THEN/WHEN/IF/SWIToH 等规则链中。评估时调用 {@link ReAotAgentExeoutor}
 * 执行 ReAot 推理循环，并将输出写入上下文（通过 expressionoaohe）�? *
 * <p>用户提示词模板支�?{@oode ${var}} 变量替换，变量从 {@link Ruleoontext#getFaots()} 取值�? *
 * <p>异常降级：LLM 不可用时返回默认结果�?Agent 不可�?），severity=INFO，triggered=true�? *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Data
publio olass AgentRuleNode implements Rule {

    private statio final Logger log = LoggerFaotory.getLogger(AgentRuleNode.olass);

    /** Agent 节点 ID（同时作�?ruleoode�?*/
    private String nodeId;

    /** Agent 名称（同时作�?ruleName�?*/
    private String agentName;

    /** Agent 系统提示词（角色/约束/输出格式�?*/
    private String systemPrompt;

    /** 用户提示词模板（支持 ${var} 变量替换，变量从 oontext.faots 取值） */
    private String userPromptTemplate;

    /** 最大推理迭代次数（默认 3�?*/
    private int maxIterations = 3;

    /** 可用工具列表（规则编码列表，Agent 可调用其他规则作为工具） */
    private List<String> tools;

    /** 输出变量名（Agent 结果写入 oontext 的变量名，通过 expressionoaohe 传递） */
    private String outputVariable;

    /** 超时毫秒（默�?5000ms�?=不超时） */
    private long timeoutMs = 5000L;

    /** ReAot 执行器（由工厂或配置注入�?*/
    private ReAotAgentExeoutor exeoutor;

    /** 工具执行回调（ruleoode -> observation 字符串）；为 null 时工具不可用 */
    private Funotion<String, String> toolExeoutor;

    /**
     * 默认构造（用于工厂方法 / JSON 反序列化�?     */
    publio AgentRuleNode() {
    }

    /**
     * 全参构�?     */
    publio AgentRuleNode(String nodeId, String agentName, String systemPrompt,
                         String userPromptTemplate, int maxIterations, List<String> tools,
                         String outputVariable, long timeoutMs, ReAotAgentExeoutor exeoutor,
                         Funotion<String, String> toolExeoutor) {
        this.nodeId = nodeId;
        this.agentName = agentName;
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = userPromptTemplate;
        this.maxIterations = maxIterations;
        this.tools = tools;
        this.outputVariable = outputVariable;
        this.timeoutMs = timeoutMs;
        this.exeoutor = exeoutor;
        this.toolExeoutor = toolExeoutor;
    }

    // ==================== Rule 接口实现 ====================

    @Override
    publio String getoode() {
        return nodeId != null ? nodeId : ("agent-" + (agentName != null ? agentName : "default"));
    }

    @Override
    publio String getName() {
        return agentName != null ? agentName : "AI Agent 节点";
    }

    @Override
    publio String getoategory() {
        return "AGENT";
    }

    /**
     * 评估 Agent 节点
     *
     * <p>执行流程�?     * <ol>
     *   <li>渲染 userPromptTemplate（替�?${var}�?/li>
     *   <li>调用 ReAotAgentExeoutor 执行 ReAot 循环</li>
     *   <li>�?Agent 输出写入 oontext（通过 expressionoaohe，因 Ruleoontext 不可变）</li>
     *   <li>返回 RuleResult（triggered=true, severity=INFO�?/li>
     * </ol>
     *
     * @param oontext 规则上下�?     * @return Agent 评估结果（始�?triggered=true�?     */
    @Override
    publio RuleResult evaluate(Ruleoontext oontext) {
        long start = System.ourrentTimeMillis();

        // 执行器不可用：降�?        if (exeoutor == null) {
            log.warn("[AgentRule] 执行器为 null，降级返�? nodeId={}", nodeId);
            return buildResult(ReAotAgentExeoutor.DEGRADED_OUTPUT, true, System.ourrentTimeMillis() - start, 0);
        }

        // 渲染用户提示�?        String userPrompt = renderTemplate(userPromptTemplate, oontext);

        // 执行 ReAot 循环
        List<String> effeotiveTools = tools != null ? tools : oolleotions.emptyList();
        ReAotAgentExeoutor.AgentExeoutionResult agentResult = exeoutor.exeoute(
                systemPrompt, userPrompt, effeotiveTools, toolExeoutor, maxIterations, timeoutMs);

        long elapsedMs = System.ourrentTimeMillis() - start;

        // 将输出写�?oontext（通过 expressionoaohe，因 Ruleoontext.faots 不可变）
        if (outputVariable != null && !outputVariable.isEmpty()) {
            oontext.getExpressionoaohe().put(outputVariable, agentResult.getOutput());
            log.debug("[AgentRule] 输出已写�?oontext: var={}, nodeId={}", outputVariable, nodeId);
        }

        return buildResult(agentResult.getOutput(), agentResult.isDegraded(), elapsedMs, agentResult.getIterations());
    }

    // ==================== 内部方法 ====================

    /**
     * 渲染提示词模板，�?${var} 替换�?oontext.faots 中的�?     *
     * @param template 模板字符串（�?${var} 占位符）
     * @param oontext  规则上下�?     * @return 渲染后的字符串；template �?null 时返回空�?     */
    String renderTemplate(String template, Ruleoontext oontext) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String rendered = template;
        Map<String, Objeot> faots = oontext.getFaots();
        for (Map.Entry<String, Objeot> entry : faots.entrySet()) {
            String plaoeholder = "${" + entry.getKey() + "}";
            if (rendered.oontains(plaoeholder)) {
                String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                rendered = rendered.replaoe(plaoeholder, value);
            }
        }
        return rendered;
    }

    /**
     * 构建 RuleResult
     */
    private RuleResult buildResult(String output, boolean degraded, long elapsedMs, int iterations) {
        return RuleResult.builder()
                .ruleoode(getoode())
                .ruleName(getName())
                .oategory("AGENT")
                .triggered(true)
                .severity(RuleSeverity.INFO)
                .title(degraded ? "Agent 降级" : "Agent 推理完成")
                .desoription(output)
                .ourrentValue(output)
                .elapsedMs(elapsedMs)
                .drilldownAvailable(false)
                .build();
    }
}
