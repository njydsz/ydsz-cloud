package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.engine.react.ReActLoop;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import com.njydsz.pmis.agent.engine.react.ReActStep;
import com.njydsz.pmis.agent.engine.prompt.PromptTemplateCodes;
import com.njydsz.pmis.agent.engine.prompt.PromptTemplateRegistry;
import com.njydsz.pmis.agent.engine.stream.NoOpReActEventListener;
import com.njydsz.pmis.agent.engine.stream.ReActEventListener;
import com.njydsz.pmis.agent.enums.agent.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.agent.AgentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P0-3/P1-5/P1-2/P2-1: AI 一句话生成流程 Agent（工作流场景）
 *
 * <p>接收自然语言描述（如"请假审批：直属领导审批 → 部门经理审批（3天以上）→ 人事备案"），
 * 通过 ReAct 推理循环调用 LLM 生成符合 BPMN 2.0 规范的 XML 流程定义。
 *
 * <p><b>P1-2 变更</b>：从直接调用 {@code LlmProvider.chatForJson()} 改为通过 {@link ReActLoop}
 * 推理循环，LLM 可主动调用 {@code bpmn_validate} 工具校验生成的 XML，再基于校验结果
 * 决定是否输出最终答案。这使流程生成具备了「生成 → 校验 → 修正」的自闭环能力。
 *
 * <p><b>P2-1 变更</b>：实现 {@link StreamableAgent}，支持通过 SSE 实时推送 ReAct 推理过程。
 * {@link #execute(AgentContext)} 等价于 {@link #executeStream(AgentContext, ReActEventListener)}
 * 传入 {@link NoOpReActEventListener}。
 *
 * <p>ReAct 循环流程：
 * <ol>
 *   <li>LLM 生成 BPMN XML，调用 {@code bpmn_validate} 工具校验</li>
 *   <li>若校验失败，LLM 根据缺失元素修正 XML，再次校验</li>
 *   <li>校验通过后，LLM 输出 {@code final_answer}，其值为最终 BPMN XML</li>
 *   <li>FlowGeneratorAgent 将 final_answer 作为 bpmnXml 返回</li>
 * </ol>
 *
 * <p>输入参数（params）：
 * <ul>
 *   <li>description: String 自然语言流程描述（必填）</li>
 * </ul>
 *
 * <p>输出载荷（payload）：
 * <ul>
 *   <li>bpmnXml: String 生成的 BPMN 2.0 XML（根元素 {@code <bpmn:definitions>}）</li>
 *   <li>valid: boolean 是否包含完整 bpmn:definitions</li>
 *   <li>summary: String 流程摘要（取自 ReAct 终止步骤的 thought）</li>
 *   <li>reactSteps: int ReAct 实际执行步数（用于可观测性）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowGeneratorAgent implements StreamableAgent {

    /** ReAct 推理循环 */
    private final ReActLoop reactLoop;
    /** Prompt 模板注册中心（P2-2） */
    private final PromptTemplateRegistry promptTemplateRegistry;

    private static final String DEFINITIONS_CLOSE = "</bpmn:definitions>";

    @Override
    public AgentType type() {
        return AgentType.FLOW_GENERATOR;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        return executeStream(ctx, NoOpReActEventListener.getInstance());
    }

    @Override
    public AgentResult executeStream(AgentContext ctx, ReActEventListener listener) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        String description = p.get("description") == null ? "" : p.get("description").toString().trim();
        if (description.isEmpty()) {
            log.warn("[FlowGenerator] biz={} 未提供流程描述", ctx.getBizRef());
            AgentResult empty = new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.INFO,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.3),
                    "未提供流程描述", List.of("NO_DESCRIPTION"),
                    Map.of("bpmnXml", ""));
            // 仍然触发监听器回调（保持流式契约）
            if (listener != null) {
                listener.onComplete(ReActResult.failure("NO_DESCRIPTION", List.of()));
            }
            return empty;
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(description);

        // 调用 ReAct 推理循环（流式版本）
        ReActResult reactResult;
        try {
            // 防御：listener=null 时降级为 NoOp，避免 mock 与 ReActLoop 内部判空不一致
            ReActEventListener safeListener = listener == null
                    ? NoOpReActEventListener.getInstance() : listener;
            reactResult = reactLoop.runStream(systemPrompt, userPrompt, ctx,
                    ReActLoop.DEFAULT_MAX_STEPS, safeListener);
        } catch (Exception e) {
            log.warn("[FlowGenerator] biz={} ReAct 循环异常: {}", ctx.getBizRef(), e.getMessage());
            if (listener != null) {
                listener.onError(0, e);
                listener.onComplete(ReActResult.failure("ReAct 循环异常: " + e.getMessage(), List.of()));
            }
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.RED,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.2),
                    "ReAct 循环异常: " + e.getMessage(),
                    List.of("REACT_ERROR"), Map.of("bpmnXml", ""));
        }

        // 处理 ReAct 失败
        if (!reactResult.isSuccess()) {
            log.warn("[FlowGenerator] biz={} ReAct 失败: {}", ctx.getBizRef(), reactResult.getFailureReason());
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.RED,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.2),
                    "ReAct 推理失败: " + reactResult.getFailureReason(),
                    List.of("REACT_FAILED"), Map.of("bpmnXml", ""));
        }

        // 提取 final_answer 作为 BPMN XML
        String bpmnXml = reactResult.getFinalAnswer();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            log.warn("[FlowGenerator] biz={} ReAct final_answer 为空", ctx.getBizRef());
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.YELLOW,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.3),
                    "LLM 返回为空", List.of("EMPTY_LLM_OUTPUT"),
                    Map.of("bpmnXml", ""));
        }

        // 校验 BPMN XML 结构完整性
        boolean valid = bpmnXml.contains("<bpmn:definitions")
                && bpmnXml.contains(DEFINITIONS_CLOSE);

        // 提取 summary（取自 ReAct 终止步骤的 thought）
        String summary = extractSummary(reactResult);

        AgentAlertLevel level = valid ? AgentAlertLevel.RECOMMEND : AgentAlertLevel.YELLOW;
        BigDecimal score = valid ? BigDecimal.valueOf(0.8) : BigDecimal.valueOf(0.4);
        BigDecimal confidence = BigDecimal.valueOf(0.75);
        String suggestion = valid
                ? "已根据描述生成 BPMN 流程定义"
                : "LLM 输出未包含完整的 bpmn:definitions，请重试或调整描述";
        List<String> matched = List.of(
                "description.length=" + description.length(),
                valid ? "VALID_BPMN" : "INVALID_BPMN",
                "react.steps=" + reactResult.getTotalSteps());

        log.info("[FlowGenerator] biz={} valid={} xml.length={} reactSteps={} summary={}",
                ctx.getBizRef(), valid, bpmnXml.length(),
                reactResult.getTotalSteps(), summary.isEmpty() ? "(空)" : summary);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bpmnXml", bpmnXml);
        payload.put("valid", valid);
        payload.put("reactSteps", reactResult.getTotalSteps());
        if (!summary.isEmpty()) {
            payload.put("summary", summary);
        }
        return new AgentResult(AgentType.FLOW_GENERATOR, level, score,
                confidence, suggestion, matched, payload);
    }

    /**
     * 从 ReAct 结果中提取流程摘要。
     *
     * <p>策略：取终止步骤（{@code final_answer}）的 {@code thought} 字段作为摘要。
     * 因为 LLM 在输出最终答案时，通常会在 thought 中说明流程特点。
     *
     * @param reactResult ReAct 结果
     * @return 流程摘要（可能为空字符串，不返回 null）
     */
    private String extractSummary(ReActResult reactResult) {
        if (reactResult.getSteps() == null || reactResult.getSteps().isEmpty()) {
            return "";
        }
        for (ReActStep step : reactResult.getSteps()) {
            if (step.isTerminal()) {
                return step.getThought() == null ? "" : step.getThought();
            }
        }
        return "";
    }

    // ========== Prompt 构建（P2-2：从 PromptTemplateRegistry 获取） ==========

    /**
     * 构建 system prompt：从注册中心获取 FLOW_GENERATOR_SYSTEM 模板。
     *
     * <p>ReAct 格式说明与工具清单由 {@link ReActLoop} 自动拼接，
     * 这里只获取业务角色与 BPMN 生成规则部分。
     */
    private String buildSystemPrompt() {
        return promptTemplateRegistry.getTemplate(PromptTemplateCodes.FLOW_GENERATOR_SYSTEM);
    }

    /**
     * 构建 user prompt：从注册中心渲染 FLOW_GENERATOR_USER 模板，注入 ${description} 变量。
     */
    private String buildUserPrompt(String description) {
        return promptTemplateRegistry.render(PromptTemplateCodes.FLOW_GENERATOR_USER,
                Map.of("description", description));
    }
}
