package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P0-3: AI 一句话生成流程 Agent（工作流场景）
 *
 * <p>接收自然语言描述（如"请假审批：直属领导审批 → 部门经理审批（3天以上）→ 人事备案"），
 * 构建 prompt 调用 LLM 生成符合 BPMN 2.0 规范的 XML 流程定义。
 *
 * <p>输入参数（params）：
 * <ul>
 *   <li>description: String 自然语言流程描述（必填）</li>
 * </ul>
 *
 * <p>输出载荷（payload）：
 * <ul>
 *   <li>bpmnXml: String 生成的 BPMN 2.0 XML（根元素 {@code <bpmn:definitions>}）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowGeneratorAgent implements Agent {

    /** LLM Provider 路由器 */
    private final LlmProviderRouter llmProviderRouter;

    /** 匹配 ```xml ... ``` 代码块（DOTALL 模式） */
    private static final Pattern XML_BLOCK = Pattern.compile("(?s)```xml\\s*(.*?)\\s*```");

    private static final String DEFINITIONS_CLOSE = "</bpmn:definitions>";

    @Override
    public AgentType type() {
        return AgentType.FLOW_GENERATOR;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        String description = p.get("description") == null ? "" : p.get("description").toString().trim();
        if (description.isEmpty()) {
            log.warn("[FlowGenerator] biz={} 未提供流程描述", ctx.getBizRef());
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.INFO,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.3),
                    "未提供流程描述", List.of("NO_DESCRIPTION"),
                    Map.of("bpmnXml", ""));
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(description);

        String llmOutput;
        try {
            llmOutput = llmProviderRouter.active().chat(systemPrompt, userPrompt, ctx);
        } catch (Exception e) {
            log.warn("[FlowGenerator] biz={} LLM 调用异常: {}", ctx.getBizRef(), e.getMessage());
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.RED,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.2),
                    "LLM 调用失败: " + e.getMessage(),
                    List.of("LLM_ERROR"), Map.of("bpmnXml", ""));
        }
        if (llmOutput == null || llmOutput.isBlank()) {
            log.warn("[FlowGenerator] biz={} LLM 返回为空", ctx.getBizRef());
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.YELLOW,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.3),
                    "LLM 返回为空", List.of("EMPTY_LLM_OUTPUT"),
                    Map.of("bpmnXml", ""));
        }

        String bpmnXml = extractXml(llmOutput);
        boolean valid = bpmnXml.contains("<bpmn:definitions")
                && bpmnXml.contains(DEFINITIONS_CLOSE);

        AgentAlertLevel level = valid ? AgentAlertLevel.RECOMMEND : AgentAlertLevel.YELLOW;
        BigDecimal score = valid ? BigDecimal.valueOf(0.8) : BigDecimal.valueOf(0.4);
        BigDecimal confidence = BigDecimal.valueOf(0.75);
        String suggestion = valid
                ? "已根据描述生成 BPMN 流程定义"
                : "LLM 输出未包含完整的 bpmn:definitions，请重试或调整描述";
        List<String> matched = List.of(
                "description.length=" + description.length(),
                valid ? "VALID_BPMN" : "INVALID_BPMN");

        log.info("[FlowGenerator] biz={} valid={} xml.length={} provider={}",
                ctx.getBizRef(), valid, bpmnXml.length(),
                llmProviderRouter.getActiveProviderName());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bpmnXml", bpmnXml);
        payload.put("valid", valid);
        return new AgentResult(AgentType.FLOW_GENERATOR, level, score,
                confidence, suggestion, matched, payload);
    }

    // ========== Prompt 构建 ==========

    /**
     * 构建 system prompt：约束 LLM 输出符合 BPMN 2.0 规范的 XML。
     */
    private String buildSystemPrompt() {
        return """
                你是一名资深的工作流（BPMN 2.0）建模专家。请根据用户提供的自然语言流程描述，
                生成一段符合 BPMN 2.0 规范的 XML 流程定义。

                要求：
                1. 根元素必须为 <bpmn:definitions>，并声明 bpmn / bpmndi / dc / di 命名空间；
                   targetNamespace 使用 "http://njydsz.com/pmis/flow"。
                2. 流程必须包含：开始节点（startEvent）、至少一个审批节点（userTask）、结束节点（endEvent）。
                3. 当描述中存在条件分支（如"3天以上需经理审批"）时，使用 exclusiveGateway（排他网关）
                   配合 sequenceFlow 的 conditionExpression 表达分支。
                4. 节点之间使用 <bpmn:sequenceFlow> 连接，sourceRef / targetRef 引用节点 id。
                5. 为每个节点设置语义化 id 与中文 name。
                6. 仅输出 XML 本身，不要任何解释性文字；可将 XML 包裹在 ```xml ``` 代码块中。
                7. 输出必须是合法、可直接部署的 BPMN 2.0 XML。""";
    }

    /**
     * 构建 user prompt：用户描述。
     */
    private String buildUserPrompt(String description) {
        return "请根据以下描述生成 BPMN 2.0 流程定义 XML：\n\n" + description;
    }

    // ========== XML 提取 ==========

    /**
     * 从 LLM 响应中提取 BPMN XML。
     *
     * <p>优先提取 ```xml ... ``` 代码块；其次截取 <?xml 或 <bpmn:definitions 到
     * </bpmn:definitions> 之间的内容；兜底返回原文去空白。
     *
     * @param output LLM 原始输出
     * @return 提取出的 XML 文本
     */
    private String extractXml(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        // 1. 优先提取 ```xml ... ``` 代码块
        Matcher m = XML_BLOCK.matcher(output);
        if (m.find()) {
            return m.group(1).trim();
        }
        // 2. 截取裸 XML：从 <?xml 或 <bpmn:definitions 开始，到 </bpmn:definitions> 结束
        int idxXml = output.indexOf("<?xml");
        int idxDef = output.indexOf("<bpmn:definitions");
        int start = idxXml >= 0 ? idxXml : idxDef;
        int end = output.lastIndexOf(DEFINITIONS_CLOSE);
        if (start >= 0 && end > start) {
            return output.substring(start, end + DEFINITIONS_CLOSE.length()).trim();
        }
        // 3. 兜底：返回原文（去首尾空白）
        return output.trim();
    }
}
