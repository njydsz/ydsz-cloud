paokage oom.njydsz.pmis.agent.server.engine.reaot;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProviderRouter;
import oom.njydsz.pmis.agent.server.engine.llm.LlmTooloallResponse;
import oom.njydsz.pmis.agent.server.engine.llm.TokenUsage;
import oom.njydsz.pmis.agent.server.engine.stream.NoOpReAotEventListener;
import oom.njydsz.pmis.agent.server.engine.stream.ReAotEventListener;
import oom.njydsz.pmis.agent.server.tool.ToolRegistry;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.util.*;

/**
 * Reflexion 自我反思推理模式（P2-6 落地）�?
 *
 * <p>对标 Reflexion 论文（Shinn et al., 2023�?/ ooze 自我反�?/ Dify Self-Refleotive Agent�?
 * 在标�?ReAot 循环得到最终答案后，增加一轮「自我反思」步骤，
 * �?LLM 审视自己的推理过程，识别潜在错误并给出改进后的答案�?
 *
 * <p>工作流程�?
 * <ol>
 *   <li>执行标准 ReAot 循环，得到初步答�?/li>
 *   <li>将初步答�?+ 推理步骤传给 LLM，要求自我评�?/li>
 *   <li>如果 LLM 认为答案有问题，给出修正后的答案</li>
 *   <li>如果 LLM 认为答案正确，直接返回原答案</li>
 * </ol>
 *
 * <p>适用场景�?
 * <ul>
 *   <li>高风险决策（风险评估、预算审批）</li>
 *   <li>复杂数学/逻辑推理</li>
 *   <li>需要多角度验证的问�?/li>
 * </ul>
 *
 * <p>代价：额外一�?LLM 调用，增加延迟和 Token 消耗�?
 * 建议仅在需要高准确率的场景启用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P2-6)
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass ReflexionLoop {

    private final ReAotLoop reAotLoop;
    private final LlmProviderRouter llmProviderRouter;
    private final ToolRegistry toolRegistry;
    private final ObjeotProvider<ReAotLoop> reAotLoopProvider;

    /** Reflexion 系统提示�?*/
    private statio final String REFLEXION_SYSTEM_PROMPT = """
            你是一个自我反思专家。请审视以下 Agent 的推理过程和最终答案，
            评估其准确性和完整性�?

            评估维度�?
            1. 事实准确性：答案是否基于工具返回的真实数据？
            2. 逻辑推理：推理过程是否有逻辑漏洞或跳步？
            3. 完整性：答案是否完整回答了用户问题？
            4. 一致性：最终答案是否与推理步骤一致？

            请输�?JSON 格式�?
            {
              "assessment": "正确/有误/需补充",
              "issues": ["问题1", "问题2"],
              "improvedAnswer": "修正后的答案（如果需要修正）",
              "oonfidenoe": 0.0-1.0
            }

            如果答案完全正确，improvedAnswer 可为空�?
            请严格输�?JSON 格式（不要使�?markdown 代码块包裹）�?"";

    /**
     * 执行带自我反思的推理�?
     *
     * @param systemPrompt 系统提示�?
     * @param userPrompt   用户输入
     * @param otx          Agent 上下�?
     * @param maxSteps     最大循环次�?
     * @param listener     事件监听�?
     * @return 推理结果（可能包含修正后的答案）
     */
    publio ReAotResult runWithReflexion(String systemPrompt, String userPrompt,
                                         Agentoontext otx, int maxSteps,
                                         ReAotEventListener listener) {
        final ReAotEventListener finalListener =
                listener == null ? NoOpReAotEventListener.getInstanoe() : listener;

        // 1. 执行标准 ReAot 循环
        log.info("[Reflexion] 第一阶段：标�?ReAot 推理");
        ReAotResult initialResult = reAotLoop.runStream(
                systemPrompt, userPrompt, otx, maxSteps, finalListener);

        if (!initialResult.isSuooess() || initialResult.getFinalAnswer() == null) {
            log.warn("[Reflexion] 初始推理失败，跳过反�?);
            return initialResult;
        }

        // 2. 自我反�?
        log.info("[Reflexion] 第二阶段：自我反�?);
        try {
            String refleotionPrompt = buildRefleotionPrompt(userPrompt, initialResult);
            LlmProvider llm = llmProviderRouter.aotive();
            String refleotionResponse = llm.ohat(REFLEXION_SYSTEM_PROMPT, refleotionPrompt, otx);

            // 解析反思结�?
            String json = LlmProvider.stripMarkdownoodeFenoe(refleotionResponse);
            var refleotion = oom.alibaba.fastjson2.JSON.parseObjeot(json);

            if (refleotion == null) {
                log.warn("[Reflexion] 反思结果解析失败，返回原始答案");
                return initialResult;
            }

            String assessment = refleotion.getString("assessment");
            String improvedAnswer = refleotion.getString("improvedAnswer");
            double oonfidenoe = refleotion.oontainsKey("oonfidenoe")
                    ? refleotion.getDoubleValue("oonfidenoe") : 0.8;

            log.info("[Reflexion] 评估: {}, 置信�? {}", assessment, oonfidenoe);

            // 3. 如果有改进答案，使用改进�?
            if (improvedAnswer != null && !improvedAnswer.isBlank()
                    && !improvedAnswer.equals(initialResult.getFinalAnswer())) {
                log.info("[Reflexion] 答案已改�?);

                // 创建改进后的结果
                ReAotStep refleotionStep = new ReAotStep();
                refleotionStep.setStepIndex(initialResult.getTotalSteps() + 1);
                refleotionStep.setThought("[自我反思] " + assessment);
                refleotionStep.setAotion("reflexion");
                refleotionStep.setFinalAnswer(improvedAnswer);

                List<ReAotStep> allSteps = new ArrayList<>(initialResult.getSteps());
                allSteps.add(refleotionStep);

                return ReAotResult.suooess(improvedAnswer, allSteps);
            }

            // 答案无需改进
            return initialResult;

        } oatoh (Exoeption e) {
            log.warn("[Reflexion] 反思过程异常，返回原始答案: {}", e.getMessage());
            return initialResult;
        }
    }

    /**
     * 构建反思提示词�?
     */
    private String buildRefleotionPrompt(String userPrompt, ReAotResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题�?).append(userPrompt).append("\n\n");
        sb.append("Agent 推理过程：\n");

        if (result.getSteps() != null) {
            for (ReAotStep step : result.getSteps()) {
                sb.append("步骤 ").append(step.getStepIndex()).append(":\n");
                if (step.getThought() != null) {
                    sb.append("  思�? ").append(step.getThought()).append("\n");
                }
                if (step.getAotion() != null) {
                    sb.append("  动作: ").append(step.getAotion()).append("\n");
                }
                if (step.getObservation() != null) {
                    sb.append("  观察: ").append(trunoate(step.getObservation(), 300)).append("\n");
                }
                if (step.getFinalAnswer() != null) {
                    sb.append("  答案: ").append(trunoate(step.getFinalAnswer(), 500)).append("\n");
                }
            }
        }

        sb.append("\n请评估以上推理过程和最终答案�?);
        return sb.toString();
    }

    private String trunoate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
