package com.njydsz.pmis.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.pmis.agent.domain.agent.AgentExecutor;
import com.njydsz.pmis.agent.domain.agent.ExecutionPlan;
import com.njydsz.pmis.agent.domain.conversation.ConversationMemory;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.domain.model.ChatChunk;
import com.njydsz.pmis.agent.domain.model.ChatMessage;
import com.njydsz.pmis.agent.domain.model.ChatRequest;
import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.domain.model.TokenUsage;
import com.njydsz.pmis.agent.server.config.AgentProperties;

/**
 * Plan-and-Execute Agent 执行器
 *
 * <p>先由 LLM 生成执行计划（Plan），再逐步执行每个步骤（Execute），
 * 最后汇总结果。适用于复杂多步任务。
 *
 * <p>执行流程：
 * <ol>
 *   <li><b>Plan</b> — 调用 LLM 将用户需求分解为可执行的步骤列表</li>
 *   <li><b>Execute</b> — 逐步执行每个步骤（每步一次 LLM 调用或工具调用）</li>
 *   <li><b>Synthesize</b> — 汇总所有步骤结果，生成最终回复</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class PlanExecuteAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(PlanExecuteAgentExecutor.class);
    private static final Pattern STEP_PATTERN = Pattern.compile("(?m)^\\s*(\\d+)[.、)\\]]\\s*(.+)");

    private final LlmClient llmClient;
    private final ConversationMemory memory;
    private final AgentProperties properties;

    public PlanExecuteAgentExecutor(LlmClient llmClient, ConversationMemory memory,
                                     AgentProperties properties) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.properties = properties;
    }

    @Override
    public ChatResponse execute(AgentExecutionRequest request) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : UUID.randomUUID().toString();
        log.info("[Plan-Execute] 开始: convId={}", convId);

        ExecutionPlan plan = generatePlan(request.getUserInput(), convId);
        log.info("[Plan-Execute] 计划生成: steps={}", plan.getSteps().size());

        plan.markExecuting();
        List<String> stepResults = new ArrayList<>();
        TokenUsage totalUsage = TokenUsage.zero();

        for (ExecutionPlan.PlanStep step : plan.getSteps()) {
            step.markExecuting();
            log.info("[Plan-Execute] 执行步骤 {}/{}: {}",
                    step.getIndex() + 1, plan.getSteps().size(), step.getDescription());

            ChatRequest stepRequest = ChatRequest.builder()
                    .model(properties.getLlm().getDefaultModel())
                    .messages(List.of(
                            ChatMessage.system("你是 PMIS 智能助手，正在执行一个多步任务的某一步。请简洁回答。"),
                            ChatMessage.user("任务目标: " + plan.getGoal() +
                                    "\n当前步骤: " + step.getDescription() +
                                    "\n已完成的步骤结果: " + String.join("; ", stepResults), null)))
                    .temperature(properties.getLlm().getTemperature())
                    .maxTokens(properties.getLlm().getMaxTokens())
                    .build();

            ChatResponse stepResponse = llmClient.chat(stepRequest);
            if (stepResponse.getUsage() != null) {
                totalUsage = totalUsage.add(stepResponse.getUsage());
            }
            stepResults.add(stepResponse.getContent());
            step.markCompleted();
        }

        ChatResponse finalResponse = synthesize(plan, stepResults, convId);
        if (finalResponse.getUsage() != null) {
            totalUsage = totalUsage.add(finalResponse.getUsage());
        }
        plan.markCompleted();

        memory.save(convId, ChatMessage.user(request.getUserInput(), convId));
        memory.save(convId, ChatMessage.assistant(finalResponse.getContent(), convId, totalUsage));

        log.info("[Plan-Execute] 完成: convId={}, steps={}, tokens={}",
                convId, plan.getSteps().size(), totalUsage.getTotalTokens());
        return new ChatResponse(finalResponse.getId(), finalResponse.getModel(),
                ChatMessage.assistant(finalResponse.getContent(), convId, totalUsage),
                totalUsage, "stop", List.of());
    }

    @Override
    public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
        ChatResponse response = execute(request);
        chunkConsumer.accept(ChatChunk.content(response.getId(), response.getModel(),
                response.getContent()));
        chunkConsumer.accept(ChatChunk.finish(response.getId(), response.getModel(),
                "stop", response.getUsage()));
    }

    @Override
    public String getType() {
        return "plan_execute";
    }

    @Override
    public boolean supports(String type) {
        return "plan_execute".equalsIgnoreCase(type) || "plan-execute".equalsIgnoreCase(type);
    }

    private ExecutionPlan generatePlan(String userInput, String convId) {
        String planPrompt = """
                你是 PMIS 项目管理系统的任务规划器。
                请将以下用户需求分解为 2-5 个可执行的步骤。

                用户需求: %s

                请按以下格式输出（每行一个步骤）：
                1. 第一步描述
                2. 第二步描述
                3. ...
                """.formatted(userInput);

        ChatRequest planRequest = ChatRequest.builder()
                .model(properties.getLlm().getDefaultModel())
                .messages(List.of(
                        ChatMessage.system("你是任务规划器，只输出编号步骤列表，不加额外解释。"),
                        ChatMessage.user(planPrompt, null)))
                .temperature(0.3)
                .maxTokens(512)
                .build();

        ChatResponse planResponse = llmClient.chat(planRequest);
        return parsePlan(userInput, planResponse.getContent());
    }

    private ExecutionPlan parsePlan(String goal, String planText) {
        List<ExecutionPlan.PlanStep> steps = new ArrayList<>();
        Matcher matcher = STEP_PATTERN.matcher(planText);
        int index = 0;
        while (matcher.find()) {
            String description = matcher.group(2).trim();
            if (!description.isEmpty()) {
                steps.add(new ExecutionPlan.PlanStep(index++, description, description));
            }
        }
        if (steps.isEmpty()) {
            steps.add(new ExecutionPlan.PlanStep(0, "直接回答用户问题", "回答"));
        }
        return new ExecutionPlan(UUID.randomUUID().toString(), goal, steps);
    }

    private ChatResponse synthesize(ExecutionPlan plan, List<String> stepResults, String convId) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("以下是任务执行的结果汇总，请整理为对用户友好的回复。\n\n");
        prompt.append("任务目标: ").append(plan.getGoal()).append("\n\n");
        for (int i = 0; i < plan.getSteps().size(); i++) {
            prompt.append("步骤 ").append(i + 1).append(": ").append(plan.getSteps().get(i).getDescription()).append("\n");
            prompt.append("结果: ").append(stepResults.get(i)).append("\n\n");
        }
        prompt.append("请汇总以上结果，给出简洁清晰的最终回复。");

        ChatRequest synReq = ChatRequest.builder()
                .model(properties.getLlm().getDefaultModel())
                .messages(List.of(
                        ChatMessage.system("你是 PMIS 智能助手，请汇总任务执行结果。"),
                        ChatMessage.user(prompt.toString(), null)))
                .temperature(properties.getLlm().getTemperature())
                .maxTokens(properties.getLlm().getMaxTokens())
                .build();

        return llmClient.chat(synReq);
    }
}
