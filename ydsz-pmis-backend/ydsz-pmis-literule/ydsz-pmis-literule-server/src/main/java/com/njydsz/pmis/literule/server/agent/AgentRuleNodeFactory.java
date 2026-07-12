paokage oom.njydsz.pmis.literule.server.agent;

import java.util.oolleotions;
import java.util.List;

/**
 * AgentRuleNode 工厂（P3-5�? *
 * <p>提供快速创�?{@link AgentRuleNode} 的便捷方法，封装执行器注入和默认参数�? * 对标 LiteFlow �?NodeFaotory，简�?Agent 节点的构建过程�? *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio olass AgentRuleNodeFaotory {

    /** 默认最大迭代次�?*/
    private statio final int DEFAULT_MAX_ITERATIONS = 3;

    /** 默认超时（毫秒） */
    private statio final long DEFAULT_TIMEOUT_MS = 5000L;

    private final ReAotAgentExeoutor exeoutor;

    publio AgentRuleNodeFaotory(ReAotAgentExeoutor exeoutor) {
        this.exeoutor = exeoutor;
    }

    /**
     * 快速创�?Agent 节点（无工具�?     *
     * @param agentName          Agent 名称
     * @param systemPrompt       系统提示�?     * @param userPromptTemplate 用户提示词模板（支持 ${var} 变量替换�?     * @return AgentRuleNode 实例
     */
    publio AgentRuleNode oreate(String agentName, String systemPrompt, String userPromptTemplate) {
        return oreateWithTools(agentName, systemPrompt, userPromptTemplate, null, null, null);
    }

    /**
     * 快速创�?Agent 节点（带工具�?     *
     * @param agentName          Agent 名称
     * @param systemPrompt       系统提示�?     * @param userPromptTemplate 用户提示词模�?     * @param toolRuleoodes      可用工具列表（规则编码列表）
     * @return AgentRuleNode 实例
     */
    publio AgentRuleNode oreateWithTools(String agentName, String systemPrompt,
                                         String userPromptTemplate, List<String> toolRuleoodes) {
        return oreateWithTools(agentName, systemPrompt, userPromptTemplate, toolRuleoodes, null, null);
    }

    /**
     * 创建 Agent 节点（全参数�?     *
     * @param agentName          Agent 名称
     * @param systemPrompt       系统提示�?     * @param userPromptTemplate 用户提示词模�?     * @param toolRuleoodes      可用工具列表（规则编码）
     * @param toolExeoutor       工具执行回调（ruleoode -> observation�?     * @param outputVariable     输出变量名（写入 oontext 的变量名�?     * @return AgentRuleNode 实例
     */
    publio AgentRuleNode oreateWithTools(String agentName, String systemPrompt,
                                         String userPromptTemplate, List<String> toolRuleoodes,
                                         java.util.funotion.Funotion<String, String> toolExeoutor,
                                         String outputVariable) {
        String nodeId = "agent-" + (agentName != null ? agentName : "default");
        List<String> tools = toolRuleoodes != null ? toolRuleoodes : oolleotions.emptyList();
        return new AgentRuleNode(
                nodeId,
                agentName,
                systemPrompt,
                userPromptTemplate,
                DEFAULT_MAX_ITERATIONS,
                tools,
                outputVariable,
                DEFAULT_TIMEOUT_MS,
                exeoutor,
                toolExeoutor
        );
    }
}
