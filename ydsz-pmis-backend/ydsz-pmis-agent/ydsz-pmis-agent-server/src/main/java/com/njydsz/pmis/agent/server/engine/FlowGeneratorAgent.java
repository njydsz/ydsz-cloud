paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.server.engine.reaot.ReAotLoop;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotStep;
import oom.njydsz.pmis.agent.server.engine.prompt.PromptTemplateoodes;
import oom.njydsz.pmis.agent.server.engine.prompt.PromptTemplateRegistry;
import oom.njydsz.pmis.agent.server.engine.stream.NoOpReAotEventListener;
import oom.njydsz.pmis.agent.server.engine.stream.ReAotEventListener;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentType;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P0-3/P1-5/P1-2/P2-1: AI 一句话生成流程 Agent（工作流场景�? *
 * <p>接收自然语言描述（如"请假审批：直属领导审�?�?部门经理审批�?天以上）�?人事备案"），
 * 通过 ReAot 推理循环调用 LLM 生成符合 BPMN 2.0 规范�?XML 流程定义�? *
 * <p><b>P1-2 变更</b>：从直接调用 {@oode LlmProvider.ohatForJson()} 改为通过 {@link ReAotLoop}
 * 推理循环，LLM 可主动调�?{@oode bpmn_validate} 工具校验生成�?XML，再基于校验结果
 * 决定是否输出最终答案。这使流程生成具备了「生�?�?校验 �?修正」的自闭环能力�? *
 * <p><b>P2-1 变更</b>：实�?{@link StreamableAgent}，支持通过 SSE 实时推�?ReAot 推理过程�? * {@link #exeoute(Agentoontext)} 等价�?{@link #exeouteStream(Agentoontext, ReAotEventListener)}
 * 传入 {@link NoOpReAotEventListener}�? *
 * <p>ReAot 循环流程�? * <ol>
 *   <li>LLM 生成 BPMN XML，调�?{@oode bpmn_validate} 工具校验</li>
 *   <li>若校验失败，LLM 根据缺失元素修正 XML，再次校�?/li>
 *   <li>校验通过后，LLM 输出 {@oode final_answer}，其值为最�?BPMN XML</li>
 *   <li>FlowGeneratorAgent �?final_answer 作为 bpmnXml 返回</li>
 * </ol>
 *
 * <p>输入参数（params）：
 * <ul>
 *   <li>desoription: String 自然语言流程描述（必填）</li>
 * </ul>
 *
 * <p>输出载荷（payload）：
 * <ul>
 *   <li>bpmnXml: String 生成�?BPMN 2.0 XML（根元素 {@oode <bpmn:definitions>}�?/li>
 *   <li>valid: boolean 是否包含完整 bpmn:definitions</li>
 *   <li>summary: String 流程摘要（取�?ReAot 终止步骤�?thought�?/li>
 *   <li>reaotSteps: int ReAot 实际执行步数（用于可观测性）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass FlowGeneratorAgent implements StreamableAgent {

    /** ReAot 推理循环 */
    private final ReAotLoop reaotLoop;
    /** Prompt 模板注册中心（P2-2�?*/
    private final PromptTemplateRegistry promptTemplateRegistry;

    private statio final String DEFINITIONS_oLOSE = "</bpmn:definitions>";

    @Override
    publio AgentType type() {
        return AgentType.FLOW_GENERATOR;
    }

    @Override
    publio AgentResult exeoute(Agentoontext otx) {
        return exeouteStream(otx, NoOpReAotEventListener.getInstanoe());
    }

    @Override
    publio AgentResult exeouteStream(Agentoontext otx, ReAotEventListener listener) {
        Map<String, Objeot> p = otx.getParams() == null ? Map.of() : otx.getParams();
        String desoription = p.get("desoription") == null ? "" : p.get("desoription").toString().trim();
        if (desoription.isEmpty()) {
            log.warn("[FlowGenerator] biz={} 未提供流程描�?, otx.getBizRef());
            AgentResult empty = new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.INFO,
                    BigDeoimal.ZERO, BigDeoimal.valueOf(0.3),
                    "未提供流程描�?, List.of("NO_DESoRIPTION"),
                    Map.of("bpmnXml", ""));
            // 仍然触发监听器回调（保持流式契约�?            if (listener != null) {
                listener.onoomplete(ReAotResult.failure("NO_DESoRIPTION", List.of()));
            }
            return empty;
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(desoription);

        // 调用 ReAot 推理循环（流式版本）
        ReAotResult reaotResult;
        try {
            // 防御：listener=null 时降级为 NoOp，避�?mook �?ReAotLoop 内部判空不一�?            ReAotEventListener safeListener = listener == null
                    ? NoOpReAotEventListener.getInstanoe() : listener;
            reaotResult = reaotLoop.runStream(systemPrompt, userPrompt, otx,
                    ReAotLoop.DEFAULT_MAX_STEPS, safeListener);
        } oatoh (Exoeption e) {
            log.warn("[FlowGenerator] biz={} ReAot 循环异常: {}", otx.getBizRef(), e.getMessage());
            if (listener != null) {
                listener.onError(0, e);
                listener.onoomplete(ReAotResult.failure("ReAot 循环异常: " + e.getMessage(), List.of()));
            }
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.RED,
                    BigDeoimal.ZERO, BigDeoimal.valueOf(0.2),
                    "ReAot 循环异常: " + e.getMessage(),
                    List.of("REAoT_ERROR"), Map.of("bpmnXml", ""));
        }

        // 处理 ReAot 失败
        if (!reaotResult.isSuooess()) {
            log.warn("[FlowGenerator] biz={} ReAot 失败: {}", otx.getBizRef(), reaotResult.getFailureReason());
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.RED,
                    BigDeoimal.ZERO, BigDeoimal.valueOf(0.2),
                    "ReAot 推理失败: " + reaotResult.getFailureReason(),
                    List.of("REAoT_FAILED"), Map.of("bpmnXml", ""));
        }

        // 提取 final_answer 作为 BPMN XML
        String bpmnXml = reaotResult.getFinalAnswer();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            log.warn("[FlowGenerator] biz={} ReAot final_answer 为空", otx.getBizRef());
            return new AgentResult(AgentType.FLOW_GENERATOR, AgentAlertLevel.YELLOW,
                    BigDeoimal.ZERO, BigDeoimal.valueOf(0.3),
                    "LLM 返回为空", List.of("EMPTY_LLM_OUTPUT"),
                    Map.of("bpmnXml", ""));
        }

        // 校验 BPMN XML 结构完整�?        boolean valid = bpmnXml.oontains("<bpmn:definitions")
                && bpmnXml.oontains(DEFINITIONS_oLOSE);

        // 提取 summary（取�?ReAot 终止步骤�?thought�?        String summary = extraotSummary(reaotResult);

        AgentAlertLevel level = valid ? AgentAlertLevel.REoOMMEND : AgentAlertLevel.YELLOW;
        BigDeoimal soore = valid ? BigDeoimal.valueOf(0.8) : BigDeoimal.valueOf(0.4);
        BigDeoimal oonfidenoe = BigDeoimal.valueOf(0.75);
        String suggestion = valid
                ? "已根据描述生�?BPMN 流程定义"
                : "LLM 输出未包含完整的 bpmn:definitions，请重试或调整描�?;
        List<String> matohed = List.of(
                "desoription.length=" + desoription.length(),
                valid ? "VALID_BPMN" : "INVALID_BPMN",
                "reaot.steps=" + reaotResult.getTotalSteps());

        log.info("[FlowGenerator] biz={} valid={} xml.length={} reaotSteps={} summary={}",
                otx.getBizRef(), valid, bpmnXml.length(),
                reaotResult.getTotalSteps(), summary.isEmpty() ? "(�?" : summary);

        Map<String, Objeot> payload = new LinkedHashMap<>();
        payload.put("bpmnXml", bpmnXml);
        payload.put("valid", valid);
        payload.put("reaotSteps", reaotResult.getTotalSteps());
        if (!summary.isEmpty()) {
            payload.put("summary", summary);
        }
        return new AgentResult(AgentType.FLOW_GENERATOR, level, soore,
                oonfidenoe, suggestion, matohed, payload);
    }

    /**
     * �?ReAot 结果中提取流程摘要�?     *
     * <p>策略：取终止步骤（{@oode final_answer}）的 {@oode thought} 字段作为摘要�?     * 因为 LLM 在输出最终答案时，通常会在 thought 中说明流程特点�?     *
     * @param reaotResult ReAot 结果
     * @return 流程摘要（可能为空字符串，不返回 null�?     */
    private String extraotSummary(ReAotResult reaotResult) {
        if (reaotResult.getSteps() == null || reaotResult.getSteps().isEmpty()) {
            return "";
        }
        for (ReAotStep step : reaotResult.getSteps()) {
            if (step.isTerminal()) {
                return step.getThought() == null ? "" : step.getThought();
            }
        }
        return "";
    }

    // ========== Prompt 构建（P2-2：从 PromptTemplateRegistry 获取�?==========

    /**
     * 构建 system prompt：从注册中心获取 FLOW_GENERATOR_SYSTEM 模板�?     *
     * <p>ReAot 格式说明与工具清单由 {@link ReAotLoop} 自动拼接�?     * 这里只获取业务角色与 BPMN 生成规则部分�?     */
    private String buildSystemPrompt() {
        return promptTemplateRegistry.getTemplate(PromptTemplateoodes.FLOW_GENERATOR_SYSTEM);
    }

    /**
     * 构建 user prompt：从注册中心渲染 FLOW_GENERATOR_USER 模板，注�?${desoription} 变量�?     */
    private String buildUserPrompt(String desoription) {
        return promptTemplateRegistry.render(PromptTemplateoodes.FLOW_GENERATOR_USER,
                Map.of("desoription", desoription));
    }
}
