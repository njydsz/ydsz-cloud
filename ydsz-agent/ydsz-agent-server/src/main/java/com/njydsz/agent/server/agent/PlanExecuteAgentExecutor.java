package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.ExecutionPlan;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.common.util.id.IdGenerator;

/**
 * Plan-and-Execute Agent 执行器
 *
 * <p>先由 LLM 生成执行计划（Plan），再逐步执行每个步骤（Execute）， 最后汇总结果。适用于复杂多步任务。
 *
 * <p>执行流程：
 *
 * <ol>
 *   <li><b>Plan</b> — 调用 LLM 将用户需求分解为可执行的步骤列表
 *   <li><b>Execute</b> — 逐步执行每个步骤（每步一次 LLM 调用或工具调用）
 *   <li><b>Synthesize</b> — 汇总所有步骤结果，生成最终回复
 * </ol>
 *
 * <p><b>流式支持</b>：逐步骤推送进度（step 级流式），用户可见每个步骤的执行结果。 与简单同步执行后包装为流式不同，本执行器在步骤执行完成后立即推送中间结果。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class PlanExecuteAgentExecutor extends AbstractAgentExecutor {

  // 解析 LLM 返回的编号步骤列表：匹配行首「数字 + 分隔符(.、)、])」+ 步骤描述
  private static final Pattern STEP_PATTERN = Pattern.compile("(?m)^\\s*(\\d+)[.、)\\]]\\s*(.+)");

  /** 步骤进度消息模板 */
  private static final String STEP_PROGRESS_TEMPLATE = "[步骤 %d/%d] %s\n结果: %s";
  /** 规划完成消息模板 */
  private static final String PLAN_READY_TEMPLATE = "[规划完成] 共 %d 个步骤\n";
  /** 最终汇总提示 */
  private static final String SYNTHESIZING_MESSAGE = "[汇总中] 正在整理最终回复...\n";

  /** 日志中步骤结果的截断长度 */
  private static final int STEP_RESULT_TRUNCATE_LENGTH = 200;

  /** 规划阶段温度：偏低以保证步骤分解稳定、可复现 */
  private static final double PLAN_TEMPERATURE = 0.3;

  /** 规划输出最大 Token 数：规划输出较短，超出将被截断导致解析失败 */
  private static final int PLAN_MAX_TOKENS = 512;

  private final ToolRegistry toolRegistry;

  public PlanExecuteAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      ToolRegistry toolRegistry,
      AgentProperties properties,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService,
      PromptTemplateProvider promptTemplateProvider) {
    super(
        llmClient,
        memory,
        properties,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService,
        promptTemplateProvider);
    this.toolRegistry = toolRegistry;
  }

  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "PLAN_EXECUTE");
    log.info("[Plan-Execute] 开始: convId={}, traceId={}", convId, traceId);

    long planStart = System.currentTimeMillis();
    ExecutionPlan plan = generatePlan(request.getUserInput(), convId);
    long planDuration = System.currentTimeMillis() - planStart;
    traceRecorder.recordStep(
        traceId,
        "PLAN_GENERATE",
        "Generated plan with " + plan.getSteps().size() + " steps",
        request.getUserInput(),
        plan,
        planDuration);
    log.info("[Plan-Execute] 计划生成: steps={}", plan.getSteps().size());

    plan.markExecuting();
    List<String> stepResults = new ArrayList<>();
    TokenUsage totalUsage = TokenUsage.zero();
    // 最大重规划次数 2：单步连续失败后允许重试生成剩余步骤两次，超过则抛出原始异常
    int maxReplans = 2;
    int replanCount = 0;

    int stepIdx = 0;
    while (stepIdx < plan.getSteps().size()) {
      ExecutionPlan.PlanStep step = plan.getSteps().get(stepIdx);
      step.markExecuting();
      log.info(
          "[Plan-Execute] 执行步骤 {}/{}: {}",
          stepIdx + 1,
          plan.getSteps().size(),
          step.getDescription());

      ChatRequest stepRequest =
          ChatRequest.builder()
              .model(properties.getLlm().getDefaultModel())
              .messages(
                  List.of(
                      ChatMessage.system("你是 YDSZ 智能助手，正在执行一个多步任务的某一步。请简洁回答。"),
                      ChatMessage.user(
                          "任务目标: "
                              + plan.getGoal()
                              + "\n当前步骤: "
                              + step.getDescription()
                              + "\n已完成的步骤结果: "
                              + String.join("; ", stepResults),
                          null)))
              .temperature(properties.getLlm().getTemperature())
              .maxTokens(properties.getLlm().getMaxTokens())
              .build();

      long stepStart = System.currentTimeMillis();
      ChatResponse stepResponse;
      try {
        stepResponse = llmClient.chat(stepRequest);
      } catch (Exception e) {
        long stepDuration = System.currentTimeMillis() - stepStart;
        log.warn(
            "[Plan-Execute] 步骤 {} 执行失败: {}, error={}",
            stepIdx + 1,
            step.getDescription(),
            e.getMessage());
        traceRecorder.recordStep(
            traceId,
            "STEP_FAILED",
            "Step " + (stepIdx + 1) + " failed: " + step.getDescription(),
            stepRequest,
            e.getMessage(),
            stepDuration);
        step.markFailed();

        if (replanCount < maxReplans) {
          replanCount++;
          log.info("[Plan-Execute] 触发重规划 {}/{}", replanCount, maxReplans);
          traceRecorder.recordStep(
              traceId,
              "REPLAN",
              "Replanning after step " + (stepIdx + 1) + " failure",
              plan.getGoal(),
              "replan_" + replanCount,
              0);

          List<ExecutionPlan.PlanStep> newSteps =
              regeneratePlan(
                  plan.getGoal(), stepResults, step.getDescription(), e.getMessage(), convId);
          if (!newSteps.isEmpty()) {
            plan.replaceRemainingSteps(stepIdx, newSteps);
            continue;
          }
        }
        throw e;
      }
      long stepDuration = System.currentTimeMillis() - stepStart;

      agentMetrics.recordLlmCall(
          llmClient.getProvider(),
          properties.getLlm().getDefaultModel(),
          stepDuration,
          stepResponse,
          null);
      if (stepResponse.getUsage() != null && costAnalysisService != null) {
        costAnalysisService.recordUsage(
            convId, properties.getLlm().getDefaultModel(), stepResponse.getUsage());
      }
      traceRecorder.recordStep(
          traceId,
          "STEP_EXECUTE",
          "Step " + (stepIdx + 1) + ": " + step.getDescription(),
          stepRequest,
          stepResponse,
          stepDuration);

      if (stepResponse.getUsage() != null) {
        totalUsage = totalUsage.add(stepResponse.getUsage());
      }
      stepResults.add(stepResponse.getContent());
      step.markCompleted();
      stepIdx++;
    }

    long synthStart = System.currentTimeMillis();
    ChatResponse finalResponse = synthesize(plan, stepResults, convId);
    long synthDuration = System.currentTimeMillis() - synthStart;
    agentMetrics.recordLlmCall(
        llmClient.getProvider(),
        properties.getLlm().getDefaultModel(),
        synthDuration,
        finalResponse,
        null);
    if (finalResponse.getUsage() != null && costAnalysisService != null) {
      costAnalysisService.recordUsage(
          convId, properties.getLlm().getDefaultModel(), finalResponse.getUsage());
    }
    traceRecorder.recordStep(
        traceId, "SYNTHESIZE", "Final synthesis", plan, finalResponse, synthDuration);
    if (finalResponse.getUsage() != null) {
      totalUsage = totalUsage.add(finalResponse.getUsage());
    }
    plan.markCompleted();
    traceRecorder.endTrace(traceId, "SUCCESS");

    memory.save(convId, ChatMessage.user(request.getUserInput(), convId));
    memory.save(convId, ChatMessage.assistant(finalResponse.getContent(), convId, totalUsage));

    log.info(
        "[Plan-Execute] 完成: convId={}, steps={}, tokens={}, traceId={}",
        convId,
        plan.getSteps().size(),
        totalUsage.getTotalTokens(),
        traceId);
    return new ChatResponse(
        finalResponse.getId(),
        finalResponse.getModel(),
        ChatMessage.assistant(finalResponse.getContent(), convId, totalUsage),
        totalUsage,
        "stop",
        List.of());
  }

  /**
   * 流式执行 Plan-Execute（step 级流式）。
   *
   * <p>与 {@link #execute} 同步执行后包装为流式不同，本方法在步骤执行完成后立即推送中间结果， 用户可实时看到每个步骤的执行进度。推送顺序：
   *
   * <ol>
   *   <li>规划完成消息（步骤数）
   *   <li>每个步骤的结果（步骤描述 + 执行结果）
   *   <li>最终汇总结果
   * </ol>
   */
  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "PLAN_EXECUTE_STREAM");
    log.info("[Plan-Execute-Stream] 开始: convId={}, traceId={}", convId, traceId);

    String responseId = IdGenerator.nextIdStr();
    String model = properties.getLlm().getDefaultModel();

    long planStart = System.currentTimeMillis();
    ExecutionPlan plan = generatePlan(request.getUserInput(), convId);
    long planDuration = System.currentTimeMillis() - planStart;
    traceRecorder.recordStep(
        traceId,
        "PLAN_GENERATE",
        "Generated plan with " + plan.getSteps().size() + " steps",
        request.getUserInput(),
        plan,
        planDuration);

    // 推送规划完成消息
    chunkConsumer.accept(
        ChatChunk.content(
            responseId,
            model,
            PLAN_READY_TEMPLATE.formatted(plan.getSteps().size())));
    log.info("[Plan-Execute-Stream] 计划生成: steps={}", plan.getSteps().size());

    plan.markExecuting();
    List<String> stepResults = new ArrayList<>();
    TokenUsage totalUsage = TokenUsage.zero();
    int maxReplans = 2;
    int replanCount = 0;

    int stepIdx = 0;
    while (stepIdx < plan.getSteps().size()) {
      ExecutionPlan.PlanStep step = plan.getSteps().get(stepIdx);
      step.markExecuting();

      ChatRequest stepRequest =
          ChatRequest.builder()
              .model(model)
              .messages(
                  List.of(
                      ChatMessage.system("你是 YDSZ 智能助手，正在执行一个多步任务的某一步。请简洁回答。"),
                      ChatMessage.user(
                          "任务目标: "
                              + plan.getGoal()
                              + "\n当前步骤: "
                              + step.getDescription()
                              + "\n已完成的步骤结果: "
                              + String.join("; ", stepResults),
                          null)))
              .temperature(properties.getLlm().getTemperature())
              .maxTokens(properties.getLlm().getMaxTokens())
              .build();

      long stepStart = System.currentTimeMillis();
      ChatResponse stepResponse;
      try {
        stepResponse = llmClient.chat(stepRequest);
      } catch (Exception e) {
        long stepDuration = System.currentTimeMillis() - stepStart;
        traceRecorder.recordStep(
            traceId,
            "STEP_FAILED",
            "Step " + (stepIdx + 1) + " failed: " + step.getDescription(),
            stepRequest,
            e.getMessage(),
            stepDuration);
        step.markFailed();

        if (replanCount < maxReplans) {
          replanCount++;
          List<ExecutionPlan.PlanStep> newSteps =
              regeneratePlan(
                  plan.getGoal(), stepResults, step.getDescription(), e.getMessage(), convId);
          if (!newSteps.isEmpty()) {
            plan.replaceRemainingSteps(stepIdx, newSteps);
            continue;
          }
        }
        chunkConsumer.accept(
            ChatChunk.content(
                responseId, model, "\n[错误] 步骤执行失败: " + e.getMessage()));
        chunkConsumer.accept(ChatChunk.finish(responseId, model, "error", totalUsage));
        traceRecorder.endTrace(traceId, "FAILED");
        throw e;
      }
      long stepDuration = System.currentTimeMillis() - stepStart;

      agentMetrics.recordLlmCall(llmClient.getProvider(), model, stepDuration, stepResponse, null);
      if (stepResponse.getUsage() != null && costAnalysisService != null) {
        costAnalysisService.recordUsage(convId, model, stepResponse.getUsage());
      }
      traceRecorder.recordStep(
          traceId,
          "STEP_EXECUTE",
          "Step " + (stepIdx + 1) + ": " + step.getDescription(),
          stepRequest,
          stepResponse,
          stepDuration);

      if (stepResponse.getUsage() != null) {
        totalUsage = totalUsage.add(stepResponse.getUsage());
      }
      stepResults.add(stepResponse.getContent());
      step.markCompleted();

      // 推送本步骤结果
      String stepMsg =
          STEP_PROGRESS_TEMPLATE.formatted(
              stepIdx + 1,
              plan.getSteps().size(),
              step.getDescription(),
              truncate(stepResponse.getContent(), STEP_RESULT_TRUNCATE_LENGTH));
      chunkConsumer.accept(ChatChunk.content(responseId, model, stepMsg));

      stepIdx++;
    }

    // 推送汇总提示
    chunkConsumer.accept(ChatChunk.content(responseId, model, SYNTHESIZING_MESSAGE));

    long synthStart = System.currentTimeMillis();
    ChatResponse finalResponse = synthesize(plan, stepResults, convId);
    long synthDuration = System.currentTimeMillis() - synthStart;
    agentMetrics.recordLlmCall(
        llmClient.getProvider(), model, synthDuration, finalResponse, null);
    if (finalResponse.getUsage() != null && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, model, finalResponse.getUsage());
    }
    traceRecorder.recordStep(
        traceId, "SYNTHESIZE", "Final synthesis", plan, finalResponse, synthDuration);
    if (finalResponse.getUsage() != null) {
      totalUsage = totalUsage.add(finalResponse.getUsage());
    }
    plan.markCompleted();
    traceRecorder.endTrace(traceId, "SUCCESS");

    memory.save(convId, ChatMessage.user(request.getUserInput(), convId));
    memory.save(convId, ChatMessage.assistant(finalResponse.getContent(), convId, totalUsage));

    // 推送最终汇总结果
    chunkConsumer.accept(ChatChunk.content(responseId, model, "\n" + finalResponse.getContent()));
    chunkConsumer.accept(ChatChunk.finish(responseId, model, "stop", totalUsage));

    log.info(
        "[Plan-Execute-Stream] 完成: convId={}, steps={}, tokens={}, traceId={}",
        convId,
        plan.getSteps().size(),
        totalUsage.getTotalTokens(),
        traceId);
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
    String planPrompt =
        promptTemplateProvider.loadOrDefault(
            properties.getPromptTemplate().getPlanExecutePlanCode(),
            """
                    你是 YDSZ 项目管理系统的任务规划器。
                    请将以下用户需求分解为 2-5 个可执行的步骤。

                    用户需求: %s

                    请按以下格式输出（每行一个步骤）：
                    1. 第一步描述
                    2. 第二步描述
                    3. ...
                    """)
            .formatted(userInput);

    String planSystemPrompt =
        promptTemplateProvider.loadOrDefault(
            properties.getPromptTemplate().getPlanExecutePlanSystemCode(),
            "你是任务规划器，只输出编号步骤列表，不加额外解释。");

    ChatRequest planRequest =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(
                List.of(
                    ChatMessage.system(planSystemPrompt),
                    ChatMessage.user(planPrompt, null)))
            // 规划阶段温度 0.3：偏低以保证步骤分解稳定、可复现
            .temperature(PLAN_TEMPERATURE)
            // 规划输出较短，maxTokens 512 足够；超出将被截断导致解析失败
            .maxTokens(PLAN_MAX_TOKENS)
            .build();

    ChatResponse planResponse = llmClient.chat(planRequest);
    return parsePlan(userInput, planResponse.getContent());
  }

  /** 动态重规划：根据已完成步骤和失败信息重新生成剩余步骤 */
  private List<ExecutionPlan.PlanStep> regeneratePlan(
      String goal,
      List<String> completedResults,
      String failedStep,
      String errorMessage,
      String convId) {
    String replanPrompt =
        promptTemplateProvider.loadOrDefault(
            properties.getPromptTemplate().getPlanExecuteReplanCode(),
            """
                    你是 YDSZ 项目管理系统的任务规划器。
                    原计划在执行过程中某步骤失败，请根据已完成的结果和失败信息重新规划剩余步骤。

                    原始目标: %s

                    已完成的步骤结果:
                     %s

                    失败的步骤: %s
                    失败原因: %s

                    请按以下格式输出新的剩余步骤（每行一个步骤）：
                    1. 第一步描述
                    2. 第二步描述
                    3. ...
                    """)
            .formatted(goal, String.join("\n", completedResults), failedStep, errorMessage);

    String replanSystemPrompt =
        promptTemplateProvider.loadOrDefault(
            properties.getPromptTemplate().getPlanExecutePlanSystemCode(),
            "你是任务规划器，只输出编号步骤列表，不加额外解释。");

    ChatRequest replanRequest =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(
                List.of(
                    ChatMessage.system(replanSystemPrompt),
                    ChatMessage.user(replanPrompt, null)))
            // 规划阶段温度 0.3：偏低以保证步骤分解稳定、可复现
            .temperature(PLAN_TEMPERATURE)
            // 规划输出较短，maxTokens 512 足够；超出将被截断导致解析失败
            .maxTokens(PLAN_MAX_TOKENS)
            .build();

    try {
      ChatResponse replanResponse = llmClient.chat(replanRequest);
      ExecutionPlan newPlan = parsePlan(goal, replanResponse.getContent());
      log.info("[Plan-Execute] 重规划成功: newSteps={}", newPlan.getSteps().size());
      return newPlan.getSteps();
    } catch (Exception e) {
      log.warn("[Plan-Execute] 重规划失败: {}", e.getMessage());
      return List.of();
    }
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
    return new ExecutionPlan(IdGenerator.nextIdStr(), goal, steps);
  }

  private ChatResponse synthesize(ExecutionPlan plan, List<String> stepResults, String convId) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("以下是任务执行的结果汇总，请整理为对用户友好的回复。\n\n");
    prompt.append("任务目标: ").append(plan.getGoal()).append("\n\n");
    for (int i = 0; i < plan.getSteps().size(); i++) {
      prompt
          .append("步骤 ")
          .append(i + 1)
          .append(": ")
          .append(plan.getSteps().get(i).getDescription())
          .append("\n");
      prompt.append("结果: ").append(stepResults.get(i)).append("\n\n");
    }
    prompt.append("请汇总以上结果，给出简洁清晰的最终回复。");

    ChatRequest synReq =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(
                List.of(
                    ChatMessage.system("你是 YDSZ 智能助手，请汇总任务执行结果。"),
                    ChatMessage.user(prompt.toString(), null)))
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
            .build();

    return llmClient.chat(synReq);
  }

  /** 截断过长的文本，避免 SSE 消息体过大 */
  private String truncate(String text, int maxLen) {
    if (text == null) {
      return "";
    }
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }
}
