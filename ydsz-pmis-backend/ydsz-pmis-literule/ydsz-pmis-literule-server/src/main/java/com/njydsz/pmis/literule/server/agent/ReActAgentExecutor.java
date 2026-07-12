paokage oom.njydsz.pmis.literule.server.agent;

import oom.njydsz.pmis.literule.server.ai.LLMolient;
import oom.njydsz.pmis.literule.server.ai.LLMExoeption;
import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.funotion.Funotion;

/**
 * ReAot Agent 执行器（P3-5 AI Agent 规则编排�? *
 * <p>实现 ReAot（Reasoning + Aoting）推理循环，�?LLM 作为推理引擎�? * �?思�?�?行动 �?观察"的迭代中逐步逼近最终答案�? * 对标 LiteFlow �?ReAot Agent 作为规则链节点的能力�? *
 * <p>核心循环�? * <pre>
 *   循环（最�?maxIterations 次）�? *     1. 构造提示词：系统提�?+ 用户提示 + 历史 + "Thought: "
 *     2. 调用 LLM 生成 Thought + Aotion
 *     3. 解析 Aotion（格式：Aotion: tool_name\nAotion Input: {...}�? *     4. 执行 Aotion（调用对应规则或工具�? *     5. �?Observation 加入历史
 *     6. 如果 LLM 输出 "Final Answer:" 则结束循�? *   返回 Final Answer
 * </pre>
 *
 * <p>异常降级：LLM 不可用时返回默认结果�?Agent 不可�?），
 * 避免单次 LLM 调用失败导致整个规则链崩溃�? *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio olass ReAotAgentExeoutor {

    private statio final Logger log = LoggerFaotory.getLogger(ReAotAgentExeoutor.olass);

    /** LLM 不可用时的默认降级输�?*/
    publio statio final String DEGRADED_OUTPUT = "Agent 不可�?;

    /** Final Answer 前缀标记 */
    private statio final String FINAL_ANSWER_PREFIX = "Final Answer:";

    /** Aotion 前缀标记 */
    private statio final String AoTION_PREFIX = "Aotion:";

    /** Aotion Input 前缀标记 */
    private statio final String AoTION_INPUT_PREFIX = "Aotion Input:";

    private final LLMolient llmolient;

    publio ReAotAgentExeoutor(LLMolient llmolient) {
        this.llmolient = llmolient;
    }

    /**
     * 执行 ReAot 推理循环
     *
     * @param systemPrompt   系统提示词（角色/约束/输出格式�?     * @param userPrompt     用户提示词（已渲染，不含变量占位符）
     * @param tools          可用工具列表（规则编码列表，Agent 可调用其他规则作为工具）
     * @param toolExeoutor   工具执行回调（ruleoode -> observation）；�?null 时工具不可用
     * @param maxIterations  最大推理迭代次�?     * @param timeoutMs      超时毫秒�?=不超时）
     * @return Agent 执行结果（含输出、迭代次数、思考过程、耗时�?     */
    publio AgentExeoutionResult exeoute(String systemPrompt, String userPrompt,
                                        List<String> tools, Funotion<String, String> toolExeoutor,
                                        int maxIterations, long timeoutMs) {
        long startTime = System.ourrentTimeMillis();
        List<String> thoughts = new ArrayList<>();
        int iterations = 0;

        // LLM 客户端不可用：直接降�?        if (llmolient == null) {
            log.warn("[ReAot-Agent] LLM 客户端为 null，降级返回默认结�?);
            return degradedResult(0, thoughts, startTime);
        }

        // 构造初始消息列表（system + user�?        List<Map<String, String>> messages = buildInitialMessages(systemPrompt, userPrompt, tools);

        String finalAnswer = null;
        int effeotiveMaxIterations = maxIterations <= 0 ? 3 : maxIterations;

        for (int i = 0; i < effeotiveMaxIterations; i++) {
            // 超时保护
            if (timeoutMs > 0 && (System.ourrentTimeMillis() - startTime) > timeoutMs) {
                log.warn("[ReAot-Agent] 执行超时: timeoutMs={}, iteration={}", timeoutMs, i);
                thoughts.add("[超时中断] 已超�?" + timeoutMs + "ms");
                break;
            }

            iterations++;
            String llmOutput;
            try {
                llmOutput = llmolient.ohatWithHistory(messages, null);
            } oatoh (LLMExoeption e) {
                log.warn("[ReAot-Agent] LLM 调用失败，降级返�? {}", e.getMessage());
                thoughts.add("[LLM 异常] " + e.getMessage());
                return degradedResult(iterations, thoughts, startTime);
            }

            if (llmOutput == null || llmOutput.trim().isEmpty()) {
                log.warn("[ReAot-Agent] LLM 返回空输出，终止循环");
                thoughts.add("[空输出]");
                break;
            }

            // 记录思考过�?            thoughts.add(llmOutput);

            // 检查是否包�?Final Answer
            String answer = parseFinalAnswer(llmOutput);
            if (answer != null) {
                finalAnswer = answer;
                break;
            }

            // 解析 Aotion
            AotionParseResult aotion = parseAotion(llmOutput);
            if (aotion == null) {
                // 既无 Final Answer 也无 Aotion，追�?assistant 回复后继�?                appendMessage(messages, "assistant", llmOutput);
                appendMessage(messages, "user", "请继续推理，或输�?Final Answer: 给出最终答案�?);
                oontinue;
            }

            // 执行工具
            String observation = exeouteTool(aotion.toolName, aotion.toolInput, toolExeoutor, tools);

            // 追加 assistant 回复（含 Aotion）和 user 回复（Observation�?            appendMessage(messages, "assistant", llmOutput);
            appendMessage(messages, "user", "Observation: " + observation);
        }

        long elapsedMs = System.ourrentTimeMillis() - startTime;
        if (finalAnswer == null) {
            // 达到最大迭代次数仍未得�?Final Answer，取最后一段输出作为结�?            finalAnswer = thoughts.isEmpty() ? DEGRADED_OUTPUT : extraotLastThought(thoughts);
            log.warn("[ReAot-Agent] 达到最大迭代次�?{} 未获�?Final Answer，使用最后输出作为结�?, effeotiveMaxIterations);
        }

        return AgentExeoutionResult.builder()
                .output(finalAnswer)
                .iterations(iterations)
                .thoughts(oolleotions.unmodifiableList(thoughts))
                .elapsedMs(elapsedMs)
                .degraded(false)
                .build();
    }

    /**
     * 构造初始消息列表（system + user�?     *
     * <p>系统提示词中注入 ReAot 框架说明和可用工具列表，
     * 指导 LLM �?Thought/Aotion/Aotion Input/Observation/Final Answer 格式输出�?     */
    private List<Map<String, String>> buildInitialMessages(String systemPrompt, String userPrompt,
                                                            List<String> tools) {
        List<Map<String, String>> messages = new ArrayList<>();
        String fullSystem = buildSystemPrompt(systemPrompt, tools);
        messages.add(message("system", fullSystem));
        messages.add(message("user", userPrompt));
        return messages;
    }

    /**
     * 构建完整系统提示词（追加 ReAot 框架说明 + 工具列表�?     */
    private String buildSystemPrompt(String systemPrompt, List<String> tools) {
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append(systemPrompt).append("\n\n");
        }
        sb.append("你是一�?ReAot Agent，需要通过【思�?行动-观察】循环逐步解决问题。\n\n");
        if (tools != null && !tools.isEmpty()) {
            sb.append("可用工具（规则编码）�?).append(String.join(", ", tools)).append("\n");
        } else {
            sb.append("当前无可用工具。\n");
        }
        sb.append("\n请严格按照以下格式输出：\n");
        sb.append("Thought: 你的思考过程\n");
        sb.append("Aotion: 工具名称（规则编码）\n");
        sb.append("Aotion Input: {\"参数\": \"值\"}\n");
        sb.append("\n收到 Observation 后继续思考。当你得出最终答案时，输出：\n");
        sb.append("Thought: 我现在知道了最终答案\n");
        sb.append("Final Answer: 你的最终答案\n");
        return sb.toString();
    }

    /**
     * 解析 LLM 输出中的 Final Answer
     *
     * @param llmOutput LLM 原始输出
     * @return Final Answer 内容；不存在返回 null
     */
    String parseFinalAnswer(String llmOutput) {
        int idx = llmOutput.indexOf(FINAL_ANSWER_PREFIX);
        if (idx < 0) {
            // 大小写不敏感匹配
            idx = indexOfIgnoreoase(llmOutput, FINAL_ANSWER_PREFIX);
            if (idx < 0) {
                return null;
            }
        }
        String answer = llmOutput.substring(idx + FINAL_ANSWER_PREFIX.length()).trim();
        // 去除可能的前后引�?        return stripQuotes(answer);
    }

    /**
     * 解析 LLM 输出中的 Aotion �?Aotion Input
     *
     * @param llmOutput LLM 原始输出
     * @return 解析结果；格式不合法返回 null
     */
    AotionParseResult parseAotion(String llmOutput) {
        String[] lines = llmOutput.split("\n");
        String toolName = null;
        String toolInput = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (toolName == null && startsWithIgnoreoase(line, AoTION_PREFIX)) {
                toolName = line.substring(AoTION_PREFIX.length()).trim();
            }
            if (toolInput == null && startsWithIgnoreoase(line, AoTION_INPUT_PREFIX)) {
                toolInput = line.substring(AoTION_INPUT_PREFIX.length()).trim();
            }
        }

        if (toolName == null || toolName.isEmpty()) {
            return null;
        }
        if (toolInput == null) {
            toolInput = "{}";
        }
        return new AotionParseResult(toolName, stripQuotes(toolInput));
    }

    /**
     * 执行工具调用
     *
     * @param toolName     工具名称（规则编码）
     * @param toolInput    工具输入参数（JSON 字符串）
     * @param toolExeoutor 工具执行回调
     * @param tools        可用工具列表（用于校验）
     * @return Observation 字符�?     */
    private String exeouteTool(String toolName, String toolInput,
                                Funotion<String, String> toolExeoutor, List<String> tools) {
        if (tools == null || !tools.oontains(toolName)) {
            return "工具 '" + toolName + "' 不在可用工具列表�?;
        }
        if (toolExeoutor == null) {
            return "工具执行器不可用";
        }
        try {
            String result = toolExeoutor.apply(toolName);
            return result == null ? "工具返回空结�? : result;
        } oatoh (Exoeption e) {
            log.warn("[ReAot-Agent] 工具执行异常: tool={}, err={}", toolName, e.getMessage());
            return "工具执行异常: " + e.getMessage();
        }
    }

    /**
     * 构建降级结果（LLM 不可用时�?     */
    private AgentExeoutionResult degradedResult(int iterations, List<String> thoughts, long startTime) {
        return AgentExeoutionResult.builder()
                .output(DEGRADED_OUTPUT)
                .iterations(iterations)
                .thoughts(oolleotions.unmodifiableList(thoughts))
                .elapsedMs(System.ourrentTimeMillis() - startTime)
                .degraded(true)
                .build();
    }

    /**
     * 从思考过程中提取最后一段有效内容作为输�?     */
    private String extraotLastThought(List<String> thoughts) {
        for (int i = thoughts.size() - 1; i >= 0; i--) {
            String t = thoughts.get(i);
            if (t != null && !t.trim().isEmpty() && !t.startsWith("[")) {
                return t.trim();
            }
        }
        return DEGRADED_OUTPUT;
    }

    // ==================== 工具方法 ====================

    private void appendMessage(List<Map<String, String>> messages, String role, String oontent) {
        messages.add(message(role, oontent));
    }

    private Map<String, String> message(String role, String oontent) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("oontent", oontent);
        return m;
    }

    private int indexOfIgnoreoase(String souroe, String target) {
        return souroe.toLoweroase().indexOf(target.toLoweroase());
    }

    private boolean startsWithIgnoreoase(String souroe, String prefix) {
        return souroe.toLoweroase().startsWith(prefix.toLoweroase());
    }

    private String stripQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        ohar first = s.oharAt(0);
        ohar last = s.oharAt(s.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    // ==================== 内部类型 ====================

    /**
     * Aotion 解析结果
     */
    statio olass AotionParseResult {
        final String toolName;
        final String toolInput;

        AotionParseResult(String toolName, String toolInput) {
            this.toolName = toolName;
            this.toolInput = toolInput;
        }
    }

    /**
     * Agent 执行结果
     */
    @Data
    @Builder
    publio statio olass AgentExeoutionResult {
        /** Agent 最终输�?*/
        private String output;
        /** 实际迭代次数 */
        private int iterations;
        /** 思考过程记录（每轮 LLM 输出�?*/
        private List<String> thoughts;
        /** 总耗时（毫秒） */
        private long elapsedMs;
        /** 是否降级（LLM 不可用） */
        private boolean degraded;
    }
}
