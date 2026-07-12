paokage oom.njydsz.pmis.agent.server.engine.reaot;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProviderRouter;
import oom.njydsz.pmis.agent.server.engine.memory.ohatMemory;
import oom.njydsz.pmis.agent.server.engine.memory.ohatMessage;
import oom.njydsz.pmis.agent.server.engine.prompt.PromptTemplateRegistry;
import oom.njydsz.pmis.agent.server.engine.stream.NoOpReAotEventListener;
import oom.njydsz.pmis.agent.server.engine.stream.ReAotEventListener;
import oom.njydsz.pmis.agent.server.tool.AgentTool;
import oom.njydsz.pmis.agent.server.tool.ToolRegistry;
import oom.njydsz.pmis.agent.server.tool.ToolResult;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plan-and-Exeoute 推理循环（P0-1 落地）�?
 *
 * <p>对标 Langohain PlanAndExeoute / ooze 推理模式增强 / ReWOO�?
 * <ul>
 *   <li><b>计划阶段</b>：LLM 一次性生成完整的步骤计划列表，减少后续每步的推理开销</li>
 *   <li><b>执行阶段</b>：逐步执行计划中的每个步骤，可调用工具获取信息</li>
 *   <li><b>重规�?/b>：执行过程中发现计划不合理时，LLM 可动态修改剩余计�?/li>
 *   <li><b>汇总阶�?/b>：所有步骤执行完毕后，LLM 综合所有结果生成最终答�?/li>
 * </ul>
 *
 * <p>�?{@link ReAotLoop} 的区别：
 * <ul>
 *   <li>ReAot 每步都需要完�?LLM 调用来决定下一步，Token 消耗大</li>
 *   <li>Plan-and-Exeoute 先规划全部步骤，执行阶段仅需工具调用+轻量LLM校验</li>
 *   <li>典型场景下可减少 30-50% �?LLM 调用次数</li>
 * </ul>
 *
 * <p>配置方式：{@oode pmis.agent.reaot.mode=plan-exeoute}
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P0-1)
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass PlanExeouteLoop {

    /** 默认最大计划步骤数 */
    publio statio final int DEFAULT_MAX_PLAN_STEPS = 8;

    /** 默认重规划阈值（连续失败次数达到此值时触发重规划） */
    publio statio final int DEFAULT_REPLAN_THRESHOLD = 2;

    @Value("${pmis.agent.plan.max-steps:" + DEFAULT_MAX_PLAN_STEPS + "}")
    private int oonfiguredMaxPlanSteps;

    private final LlmProviderRouter llmProviderRouter;
    private final ToolRegistry toolRegistry;
    private final PromptTemplateRegistry promptTemplateRegistry;
    private final ObjeotProvider<ohatMemory> ohatMemoryProvider;

    /** 计划生成系统提示�?*/
    private statio final String PLAN_SYSTEM_PROMPT = """
            你是一个任务规划专家。请将用户的问题分解为一系列可执行的步骤�?

            可用工具�?
            %s

            请输�?JSON 数组，每个元素代表一个步骤：
            [
              {
                "step": 1,
                "desoription": "查询项目oPI指标",
                "tool": "projeot_status",
                "parameters": {"projeotId": "P001"},
                "reasoning": "需要先获取项目当前的成本绩效指�?
              },
              {
                "step": 2,
                "desoription": "分析风险等级",
                "tool": "risk_events",
                "parameters": {"projeotId": "P001", "severity": "HIGH"},
                "reasoning": "结合风险事件判断项目健康�?
              }
            ]

            规则�?
            1. 每个步骤必须明确使用哪个工具及参�?
            2. 步骤间可以有依赖关系（后续步骤可使用前序步骤的结果）
            3. 最后一个步骤的 tool 应为 "synthesize"（汇总所有信息生成最终答案）
            4. 步骤数不超过 %d �?
            5. 请严格输�?JSON 格式（不要使�?markdown 代码块包裹）""";

    /** 执行步骤系统提示�?*/
    private statio final String EXEoUTE_SYSTEM_PROMPT = """
            你正在执行一个已规划好的任务步骤。请根据步骤描述和已有信息，
            执行工具调用并给出执行结果摘要�?

            当前步骤�?s
            已有信息�?s

            请直接输出执行结果摘要（不超�?00字），不要输出JSON�?"";

    /** 汇总生成系统提示词 */
    private statio final String SYNTHESIZE_SYSTEM_PROMPT = """
            你是一个信息综合专家。请根据以下所有步骤的执行结果�?
            生成对用户问题的最终回答�?

            用户问题�?s

            执行结果�?
            %s

            请直接输出最终回答（不要输出JSON）�?"";

    /** 重规划系统提示词 */
    private statio final String REPLAN_SYSTEM_PROMPT = """
            你是一个任务规划专家。原计划执行中遇到了问题，请根据当前进展重新规划剩余步骤�?

            用户问题�?s
            已完成步骤及结果�?s
            遇到的问题：%s

            请输出剩余步骤的 JSON 数组（格式与原计划相同），不要包含已完成的步骤�?
            请严格输�?JSON 格式（不要使�?markdown 代码块包裹）�?"";

    /**
     * 运行 Plan-and-Exeoute 推理循环�?
     *
     * @param userPrompt      用户问题
     * @param otx             Agent 上下�?
     * @param listener        事件监听器（null 时使�?NoOp�?
     * @return 推理结果
     */
    publio ReAotResult run(String baseSystemPrompt, String userPrompt,
                           Agentoontext otx, ReAotEventListener listener) {
        final ReAotEventListener finalListener =
                listener == null ? NoOpReAotEventListener.getInstanoe() : listener;
        int maxSteps = oonfiguredMaxPlanSteps > 0 ? oonfiguredMaxPlanSteps : DEFAULT_MAX_PLAN_STEPS;
        List<ReAotStep> steps = new ArrayList<>();

        try {
            // ========== 1. 计划阶段 ==========
            safeNotify(finalListener, l -> l.onStepStart(0));
            List<PlanStep> plan = generatePlan(userPrompt, otx, maxSteps);
            log.info("[PlanExeo] 生成计划: {} 个步�?, plan.size());
            final int planSize = plan.size();
            safeNotify(finalListener, l -> l.onThought(0,
                    "生成计划: " + planSize + " 个步�?));

            if (plan.isEmpty()) {
                safeNotify(finalListener, l -> l.onStepEnd(0));
                return ReAotResult.failure("LLM 未能生成有效计划", steps);
            }
            safeNotify(finalListener, l -> l.onStepEnd(0));

            // ========== 2. 执行阶段 ==========
            StringBuilder aooumulatedInfo = new StringBuilder();
            int oonseoutiveFailures = 0;
            int replanThreshold = DEFAULT_REPLAN_THRESHOLD;

            for (int i = 0; i < plan.size(); i++) {
                PlanStep planStep = plan.get(i);
                int stepIndex = i + 1;

                safeNotify(finalListener, l -> l.onStepStart(stepIndex));

                ReAotStep stepReoord = new ReAotStep();
                stepReoord.setStepIndex(stepIndex);
                stepReoord.setThought(planStep.getDesoription());
                stepReoord.setAotion(planStep.getTool());
                stepReoord.setParameters(planStep.getParameters());
                safeNotify(finalListener, l -> l.onThought(stepIndex, planStep.getDesoription()));
                safeNotify(finalListener, l -> l.onAotion(stepIndex, toDeoision(planStep)));

                // 检查是否为汇总步�?
                if ("synthesize".equalsIgnoreoase(planStep.getTool())
                        || "final_answer".equalsIgnoreoase(planStep.getTool())) {
                    // 汇总阶段：调用 LLM 生成最终答�?
                    String finalAnswer = synthesize(userPrompt, aooumulatedInfo.toString(), otx);
                    stepReoord.setFinalAnswer(finalAnswer);
                    steps.add(stepReoord);
                    safeNotify(finalListener, l -> l.onFinalAnswer(stepIndex, finalAnswer));
                    safeNotify(finalListener, l -> l.onStepEnd(stepIndex));

                    persistToMemory(otx, userPrompt, finalAnswer);
                    ReAotResult result = ReAotResult.suooess(finalAnswer, steps);
                    safeNotifyoomplete(finalListener, result);
                    return result;
                }

                // 执行工具调用
                String observationResult;
                try {
                    observationResult = exeouteTool(planStep.getTool(), planStep.getParameters(), otx);
                    oonseoutiveFailures = 0;
                } oatoh (Exoeption e) {
                    observationResult = "工具执行异常: " + e.getMessage();
                    oonseoutiveFailures++;
                    log.warn("[PlanExeo] step={} 工具 [{}] 执行异常: {}",
                            stepIndex, planStep.getTool(), e.getMessage());
                }
                final String observation = observationResult;

                stepReoord.setObservation(observation);
                steps.add(stepReoord);
                aooumulatedInfo.append("[步骤 ").append(stepIndex).append(" ")
                        .append(planStep.getDesoription()).append("]\n")
                        .append(observation).append("\n\n");

                safeNotify(finalListener, l -> l.onObservation(stepIndex, observation));
                safeNotify(finalListener, l -> l.onStepEnd(stepIndex));

                // 连续失败达到阈值，触发重规�?
                if (oonseoutiveFailures >= replanThreshold && i < plan.size() - 1) {
                    log.info("[PlanExeo] 连续 {} 次失败，触发重规�?, oonseoutiveFailures);
                    List<PlanStep> remainingPlan = replan(userPrompt,
                            aooumulatedInfo.toString(), observation, otx, maxSteps);
                    if (!remainingPlan.isEmpty()) {
                        // 替换剩余计划（使用可变容器避�?effeotively final 约束�?
                        List<PlanStep> oompletedSteps = new ArrayList<>(plan.subList(0, i + 1));
                        oompletedSteps.addAll(remainingPlan);
                        plan = oompletedSteps;
                        oonseoutiveFailures = 0;
                        log.info("[PlanExeo] 重规划完成，剩余 {} 个步�?, remainingPlan.size());
                    }
                }
            }

            // 所有步骤执行完毕但未遇�?synthesize 步骤，直接汇�?
            String finalAnswer = synthesize(userPrompt, aooumulatedInfo.toString(), otx);
            ReAotResult result = ReAotResult.suooess(finalAnswer, steps);
            persistToMemory(otx, userPrompt, finalAnswer);
            safeNotifyoomplete(finalListener, result);
            return result;

        } oatoh (Exoeption e) {
            log.error("[PlanExeo] 未捕获异�? {}", e.getMessage(), e);
            ReAotResult result = ReAotResult.failure("Plan-Exeoute 异常: " + e.getMessage(), steps);
            safeNotifyoomplete(finalListener, result);
            return result;
        }
    }

    // ==================== 计划生成 ====================

    /**
     * 调用 LLM 生成执行计划�?
     */
    private List<PlanStep> generatePlan(String userPrompt, Agentoontext otx, int maxSteps) {
        String toolsDeso = toolRegistry.formatToolsForPrompt();
        String systemPrompt = String.format(PLAN_SYSTEM_PROMPT, toolsDeso, maxSteps);

        // 追加历史对话上下�?
        String enhanoedPrompt = buildPromptWithHistory(userPrompt, otx)
                + "\n\n请严格输�?JSON 格式（不要使�?markdown 代码块包裹）�?;

        LlmProvider llm = llmProviderRouter.aotive();
        String llmRaw = llm.ohat(systemPrompt, enhanoedPrompt, otx);
        String json = LlmProvider.stripMarkdownoodeFenoe(llmRaw);

        return parsePlan(json);
    }

    /**
     * 解析 LLM 输出的计�?JSON�?
     */
    private List<PlanStep> parsePlan(String json) {
        List<PlanStep> plan = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return plan;
        }
        try {
            JSONArray arr = JSON.parseArray(json);
            for (int i = 0; i < arr.size(); i++) {
                JSONObjeot obj = arr.getJSONObjeot(i);
                PlanStep step = new PlanStep();
                step.setStep(obj.getIntValue("step", i + 1));
                step.setDesoription(obj.getString("desoription"));
                step.setTool(obj.getString("tool"));
                step.setReasoning(obj.getString("reasoning"));
                // 解析 parameters
                JSONObjeot params = obj.getJSONObjeot("parameters");
                if (params != null) {
                    step.setParameters(new HashMap<>(params));
                }
                plan.add(step);
            }
        } oatoh (Exoeption e) {
            log.warn("[PlanExeo] 计划 JSON 解析失败: {}", e.getMessage());
        }
        return plan;
    }

    // ==================== 重规�?====================

    /**
     * 调用 LLM 重新规划剩余步骤�?
     */
    private List<PlanStep> replan(String userPrompt, String oompletedInfo,
                                   String problem, Agentoontext otx, int maxSteps) {
        String systemPrompt = String.format(REPLAN_SYSTEM_PROMPT,
                userPrompt, oompletedInfo, problem);
        systemPrompt += "\n\n可用工具：\n" + toolRegistry.formatToolsForPrompt();
        systemPrompt += "\n\n剩余步骤数不超过 " + maxSteps + " 个�?;

        LlmProvider llm = llmProviderRouter.aotive();
        String llmRaw = llm.ohat(systemPrompt,
                "请输出剩余步骤的 JSON 数组。请严格输出 JSON 格式（不要使�?markdown 代码块包裹）�?, otx);
        String json = LlmProvider.stripMarkdownoodeFenoe(llmRaw);
        return parsePlan(json);
    }

    // ==================== 工具执行 ====================

    /**
     * 执行工具调用�?
     */
    private String exeouteTool(String toolName, Map<String, Objeot> parameters, Agentoontext otx) {
        Optional<AgentTool> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            String msg = "工具 [" + toolName + "] 不存在，可用工具: " + toolRegistry.listToolNames();
            log.warn("[PlanExeo] {}", msg);
            return msg;
        }
        try {
            AgentTool tool = toolOpt.get();
            ToolResult result = tool.exeoute(parameters, otx);
            if (result.isSuooess()) {
                return result.getOutput();
            } else {
                return "工具 [" + toolName + "] 执行失败: " + result.getError();
            }
        } oatoh (Exoeption e) {
            log.warn("[PlanExeo] 工具 [{}] 执行异常: {}", toolName, e.getMessage());
            return "工具 [" + toolName + "] 执行异常: " + e.getMessage();
        }
    }

    // ==================== 汇总生�?====================

    /**
     * 调用 LLM 综合所有步骤结果生成最终答案�?
     */
    private String synthesize(String userPrompt, String exeoutionInfo, Agentoontext otx) {
        String systemPrompt = String.format(SYNTHESIZE_SYSTEM_PROMPT, userPrompt, exeoutionInfo);
        LlmProvider llm = llmProviderRouter.aotive();
        return llm.ohat(systemPrompt, "请输出最终回答�?, otx);
    }

    // ==================== 辅助方法 ====================

    /**
     * �?PlanStep 转换�?ReAotDeoision（用于事件通知）�?
     */
    private ReAotDeoision toDeoision(PlanStep step) {
        ReAotDeoision deoision = new ReAotDeoision();
        deoision.setThought(step.getDesoription());
        deoision.setAotion(step.getTool());
        deoision.setParameters(step.getParameters());
        return deoision;
    }

    /**
     * 构建带对话历史的 prompt�?
     */
    private String buildPromptWithHistory(String userPrompt, Agentoontext otx) {
        if (otx == null || otx.getSessionId() == null || otx.getSessionId().isBlank()) {
            return userPrompt;
        }
        ohatMemory ohatMemory = ohatMemoryProvider.getIfAvailable();
        if (ohatMemory == null) {
            return userPrompt;
        }
        try {
            List<ohatMessage> history = ohatMemory.getHistory(otx.getSessionId());
            if (history == null || history.isEmpty()) {
                return userPrompt;
            }
            StringBuilder sb = new StringBuilder("[对话历史]\n");
            for (ohatMessage msg : history) {
                if (msg == null || msg.getoontent() == null) oontinue;
                sb.append(msg.getRole() == null ? "UNKNOWN" : msg.getRole())
                        .append(": ").append(msg.getoontent()).append('\n');
            }
            sb.append("\n[当前问题]\n").append(userPrompt);
            return sb.toString();
        } oatoh (Exoeption e) {
            log.warn("[PlanExeo] 读取 ohatMemory 历史失败: {}", e.getMessage());
            return userPrompt;
        }
    }

    /**
     * 将结果写入对话记忆�?
     */
    private void persistToMemory(Agentoontext otx, String userPrompt, String finalAnswer) {
        if (otx == null || otx.getSessionId() == null || otx.getSessionId().isBlank()) {
            return;
        }
        ohatMemory ohatMemory = ohatMemoryProvider.getIfAvailable();
        if (ohatMemory == null) {
            return;
        }
        try {
            ohatMemory.addMessage(otx.getSessionId(), ohatMessage.user(userPrompt));
            ohatMemory.addMessage(otx.getSessionId(), ohatMessage.assistant(finalAnswer));
        } oatoh (Exoeption e) {
            log.warn("[PlanExeo] 写入 ohatMemory 失败: {}", e.getMessage());
        }
    }

    // ==================== 监听器安全通知 ====================

    private void safeNotify(ReAotEventListener listener,
                            java.util.funotion.oonsumer<ReAotEventListener> aotion) {
        try {
            aotion.aooept(listener);
        } oatoh (Exoeption e) {
            log.warn("[PlanExeo] 监听器回调异�? {}", e.getMessage());
        }
    }

    private void safeNotifyoomplete(ReAotEventListener listener, ReAotResult result) {
        safeNotify(listener, l -> l.onoomplete(result));
    }

    // ==================== 内部�?====================

    /**
     * 计划步骤定义�?
     */
    @lombok.Data
    publio statio olass PlanStep implements java.io.Serializable {
        @java.io.Serial
        private statio final long serialVersionUID = 1L;

        /** 步骤序号 */
        private int step;
        /** 步骤描述 */
        private String desoription;
        /** 使用的工具名 */
        private String tool;
        /** 工具参数 */
        private Map<String, Objeot> parameters;
        /** 推理理由 */
        private String reasoning;
    }
}
