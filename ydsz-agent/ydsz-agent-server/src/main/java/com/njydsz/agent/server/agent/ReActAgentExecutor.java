package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.DagProgressEvent;
import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.execution.ExecutionCheckpoint;
import com.njydsz.agent.domain.execution.SessionPausedException;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.middleware.MiddlewareChain;
import com.njydsz.agent.domain.middleware.MiddlewareContext;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.SseEvent;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.execution.ExecutionPauseService;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.common.util.id.IdGenerator;

/**
 * ReAct Agent 执行器
 *
 * <p>实现 ReAct（Reasoning + Acting）模式：
 *
 * <pre>
 * Thought → Action (Tool Call) → Observation (Tool Result) → Thought → ... → Final Answer
 * </pre>
 *
 * <p>可观测性：
 *
 * <ul>
 *   <li>{@link TraceRecorder} — 记录每次 LLM 调用和工具执行步骤
 *   <li>{@link AgentMetrics} — 采集 LLM 调用耗时/Token/状态指标
 *   <li>{@link CostAnalysisService} — 核算 Token 用量成本
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ReActAgentExecutor extends AbstractAgentExecutor {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  /**
   * 工具并发执行线程池（JDK 21 虚拟线程，规范豁免场景）。
   *
   * <p>多个 tool call 并行执行以缩短单轮迭代耗时；虚拟线程在 IO 密集型工具场景下近乎零成本。
   */
  private static final ExecutorService TOOL_EXECUTOR =
      ExecutorUtils.newVirtualThreadExecutor("agent-react-executor-");

  /** 工具结果推送时的截断长度 */
  private static final int TOOL_RESULT_TRUNCATE_LENGTH = 200;

  /** 工具审批门中间件已审批调用 ID 上下文属性键（恢复执行时避免重复拦截） */
  private static final String TOOL_APPROVAL_BYPASS_IDS = "tool-approval.approved-call-ids";

  /** 工具注册中心 */
  private final ToolRegistry toolRegistry;

  /** RAG 检索服务（可选，为 null 时不启用知识增强） */
  private final RagService ragService;

  /** 执行暂停服务（可选，为 null 时不支持会话级暂停/恢复） */
  private final ExecutionPauseService pauseService;

  public ReActAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      ToolRegistry toolRegistry,
      AgentProperties properties,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService,
      PromptTemplateProvider promptTemplateProvider,
      RagService ragService,
      MiddlewareChain middlewareChain,
      ExecutionPauseService pauseService) {
    super(
        llmClient,
        memory,
        properties,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService,
        promptTemplateProvider,
        middlewareChain);
    this.toolRegistry = toolRegistry;
    this.ragService = ragService;
    this.pauseService = pauseService;
  }

  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId = extractConvId(request);
    MiddlewareContext mwContext = createMiddlewareContext(request);
    // 启动中间件链（onAgentStart 钩子 — 含链路追踪启动）
    notifyAgentStart(mwContext);
    String traceId = mwContext.getTraceId() != null ? mwContext.getTraceId() : startTrace(convId, "REACT");

    log.info(
        "[ReAct] 开始执行: convId={}, traceId={}, maxIterations={}",
        convId,
        traceId,
        request.getMaxIterations());

    // 优先使用中间件输入护栏，回退到 GuardrailService（向后兼容）
    String userInput = executeInputGuardrails(mwContext, request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      mwContext.setFinished(true);
      notifyAgentEnd(mwContext);
      return buildRejectedResponse("输入被护栏拒绝");
    }

    List<ChatMessage> messages = new ArrayList<>(COLLECTION_CAPACITY);
    String systemPrompt = buildSystemPrompt(request, userInput);
    messages.add(ChatMessage.system(systemPrompt));
    messages.addAll(loadHistory(request, convId));
    messages.add(ChatMessage.user(userInput, convId));

    // 通知系统 Prompt 构建（onSystemPrompt 钩子）
    mwContext.setSystemPrompt(systemPrompt);
    notifySystemPrompt(mwContext);

    TokenUsage totalUsage = TokenUsage.zero();
    List<ToolDefinition> toolDefinitions = new ArrayList<>(toolRegistry.getToolDefinitions());

    for (int i = 0; i < request.getMaxIterations(); i++) {
      try {
        IterationOutcome outcome = runIteration(
            request, mwContext, messages, totalUsage, toolDefinitions, convId, traceId, i);
        totalUsage = outcome.totalUsage();
        if (outcome.response() != null) {
          return outcome.response();
        }
      } catch (SessionPausedException e) {
        return handlePaused(
            e, request, convId, traceId, messages, userInput, systemPrompt, totalUsage, i);
      }
    }

    log.warn("[ReAct] 超过最大迭代次数: convId={}", convId);
    traceRecorder.endTrace(traceId, "MAX_ITERATIONS");
    mwContext.setFinished(true);
    notifyAgentEnd(mwContext);
    return buildMaxIterationsResponse(convId, totalUsage);
  }

  /**
   * 执行单轮 ReAct 迭代。
   *
   * <p>如果本轮产生工具调用，执行工具批次并把结果加入消息列表；
   * 如果产生最终答案，返回 ChatResponse；否则返回 null 继续下一轮。
   *
   * @param request 执行请求
   * @param mwContext 中间件上下文
   * @param messages 当前消息列表（会被修改）
   * @param totalUsage 当前总 Token 用量（累加后通过返回值透出，原引用保持不变）
   * @param toolDefinitions 工具定义列表
   * @param convId 对话 ID
   * @param traceId 链路追踪 ID
   * @param iteration 当前迭代轮次
   * @return 迭代结果（最终响应或 null + 更新后的累计用量）
   */
  private IterationOutcome runIteration(
      AgentExecutionRequest request,
      MiddlewareContext mwContext,
      List<ChatMessage> messages,
      TokenUsage totalUsage,
      List<ToolDefinition> toolDefinitions,
      String convId,
      String traceId,
      int iteration) {
    ChatRequest llmRequest =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(messages)
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
            .tools(toolDefinitions)
            .build();

    // 设置 LLM 请求到中间件上下文（onReasoning 钩子可审查/修改）
    mwContext.setLlmRequest(llmRequest);
    notifyReasoning(mwContext);
    // 使用中间件链执行 LLM 调用（onModelCall 洋葱模型 — 支持缓存/限流/指标）
    ChatResponse response = executeLlmCall(mwContext, () -> llmClient.chat(llmRequest));
    // 同步路径中间件链不会自动设置 llmResponse，需手动补设
    if (mwContext.getLlmResponse() == null) {
      mwContext.setLlmResponse(response);
    }

    if (response.getUsage() != null) {
      totalUsage = totalUsage.add(response.getUsage());
    }

    if (!response.hasToolCalls()) {
      String output = applyOutputGuardrails(response.getContent());
      saveConversation(convId, request.getUserInput(), output, response.getUsage());
      traceRecorder.endTrace(traceId, "SUCCESS");
      mwContext.setFinished(true);
      notifyAgentEnd(mwContext);
      log.info(
          "[ReAct] 完成: convId={}, iterations={}, tokens={}",
          convId,
          iteration + 1,
          totalUsage.getTotalTokens());
      ChatResponse finalResponse = new ChatResponse(
          response.getId(),
          response.getModel(),
          ChatMessage.assistant(output, convId, totalUsage),
          totalUsage,
          "stop",
          List.of());
      return new IterationOutcome(finalResponse, totalUsage);
    }

    messages.add(response.getMessage());
    // 并发执行工具并获取结果（经中间件 onActing 洋葱模型包装）
    List<ToolCall> allowedCalls = filterAllowedTools(request, response.getToolCalls());
    ToolBatchOutcome outcome = executeToolBatch(mwContext, traceId, allowedCalls);
    for (ToolCall toolCall : allowedCalls) {
      String result = outcome.results().getOrDefault(toolCall.getId(), "{}");
      // 通知工具观察（onObservation 钩子）
      mwContext.setToolCall(toolCall);
      mwContext.setToolResult(result);
      notifyObservation(mwContext);
      ChatMessage toolMsg = ChatMessage.tool(toolCall.getId(), result, convId);
      messages.add(toolMsg);
    }
    return new IterationOutcome(null, totalUsage);
  }

  /**
   * 处理会话暂停：保存检查点并返回暂停响应。
   *
   * @param e 暂停异常
   * @param request 原执行请求
   * @param convId 对话 ID
   * @param traceId 链路追踪 ID
   * @param messages 当前消息列表
   * @param userInput 用户输入原文
   * @param systemPrompt 系统提示词
   * @param totalUsage 已消耗 Token
   * @param iteration 当前迭代轮次
   * @return 暂停状态响应
   */
  private ChatResponse handlePaused(
      SessionPausedException e,
      AgentExecutionRequest request,
      String convId,
      String traceId,
      List<ChatMessage> messages,
      String userInput,
      String systemPrompt,
      TokenUsage totalUsage,
      int iteration) {
    if (pauseService == null) {
      throw new IllegalStateException("会话暂停服务未装配，无法保存检查点");
    }
    ExecutionCheckpoint checkpoint =
        new ExecutionCheckpoint(
            e.getApprovalId(),
            request,
            convId,
            traceId,
            List.copyOf(messages),
            e.getPendingToolCalls(),
            totalUsage,
            iteration,
            userInput,
            systemPrompt,
            Map.of());
    pauseService.save(e.getApprovalId(), checkpoint);
    log.info(
        "[ReAct] 会话已暂停等待审批: convId={}, traceId={}, approvalId={}, iteration={}",
        convId,
        traceId,
        e.getApprovalId(),
        iteration);
    return new ChatResponse(
            IdGenerator.nextIdStr(),
            properties.getLlm().getDefaultModel(),
            ChatMessage.assistant(
                "当前操作需人工审批，审批通过后将自动恢复执行。approvalId=" + e.getApprovalId(),
                convId,
                totalUsage),
            totalUsage,
            "paused",
            List.of())
        .withMetadata("approvalId", e.getApprovalId())
        .withMetadata("status", "PAUSED");
  }

  /**
   * 从检查点恢复 ReAct 执行。
   *
   * <p>审批通过后，执行检查点中保存的待执行工具调用，并将结果回填到消息列表，
   * 然后从当前迭代轮次继续 ReAct 循环；审批拒绝时返回拒绝响应并清理检查点。
   *
   * @param checkpoint 执行检查点
   * @param approved true=审批通过；false=审批拒绝
   * @return 恢复后的最终响应
   */
  @Override
  public ChatResponse resume(ExecutionCheckpoint checkpoint, boolean approved) {
    if (pauseService == null) {
      throw new IllegalStateException("会话暂停服务未装配，无法恢复执行");
    }
    String approvalId = checkpoint.getApprovalId();
    String convId = checkpoint.getConversationId();
    String traceId = checkpoint.getTraceId();
    AgentExecutionRequest request = checkpoint.getRequest();

    if (!approved) {
      pauseService.discard(approvalId);
      traceRecorder.endTrace(traceId, "APPROVAL_REJECTED");
      log.info("[ReAct] 审批拒绝，中止执行: approvalId={}", approvalId);
      return buildRejectedResponse("人工审批未通过，当前操作已中止");
    }

    List<ChatMessage> messages = new ArrayList<>(checkpoint.getMessages());
    TokenUsage totalUsage = checkpoint.getTotalUsage();
    MiddlewareContext mwContext = createMiddlewareContext(request);
    mwContext.setTraceId(traceId);
    mwContext.setSystemPrompt(checkpoint.getSystemPrompt());

    // 执行已审批的待执行工具调用（标记为已审批，避免工具审批门再次拦截）
    List<ToolCall> pending = checkpoint.getPendingToolCalls();
    if (!pending.isEmpty()) {
      Set<String> approvedIds = new HashSet<>(pending.size());
      for (ToolCall toolCall : pending) {
        approvedIds.add(toolCall.getId());
      }
      mwContext.setAttribute(TOOL_APPROVAL_BYPASS_IDS, approvedIds);
      ToolBatchOutcome outcome = executeToolBatch(mwContext, traceId, pending);
      mwContext.setAttribute(TOOL_APPROVAL_BYPASS_IDS, null);
      for (ToolCall toolCall : pending) {
        String result = outcome.results().getOrDefault(toolCall.getId(), "{}");
        mwContext.setToolCall(toolCall);
        mwContext.setToolResult(result);
        notifyObservation(mwContext);
        messages.add(ChatMessage.tool(toolCall.getId(), result, convId));
      }
    }

    log.info(
        "[ReAct] 恢复执行: approvalId={}, convId={}, traceId={}, iteration={}",
        approvalId, convId, traceId, checkpoint.getCurrentIteration());

    List<ToolDefinition> toolDefinitions = new ArrayList<>(toolRegistry.getToolDefinitions());
    for (int i = checkpoint.getCurrentIteration() + 1; i < request.getMaxIterations(); i++) {
      try {
        IterationOutcome outcome = runIteration(
            request, mwContext, messages, totalUsage, toolDefinitions, convId, traceId, i);
        totalUsage = outcome.totalUsage();
        if (outcome.response() != null) {
          pauseService.discard(approvalId);
          return outcome.response();
        }
      } catch (SessionPausedException e) {
        return handlePaused(
            e, request, convId, traceId, messages,
            checkpoint.getUserInput(), checkpoint.getSystemPrompt(), totalUsage, i);
      }
    }

    pauseService.discard(approvalId);
    log.warn("[ReAct] 恢复后超过最大迭代次数: convId={}", convId);
    traceRecorder.endTrace(traceId, "MAX_ITERATIONS");
    mwContext.setFinished(true);
    notifyAgentEnd(mwContext);
    return buildMaxIterationsResponse(convId, totalUsage);
  }

  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    doExecuteStream(request, chunkConsumer, null);
  }

  @Override
  public void executeStream(
      AgentExecutionRequest request,
      Consumer<ChatChunk> chunkConsumer,
      Consumer<DagProgressEvent> progressConsumer,
      Consumer<SseEvent> eventConsumer) {
    doExecuteStream(request, chunkConsumer, eventConsumer);
  }

  /**
   * 流式执行核心实现（可选类型化事件推送）。
   *
   * <p>在文本片段之外，向 {@code eventConsumer} 推送结构化事件：
   * 工具调用开始（{@code tool_call_started}）与完成（{@code tool_call_completed}），
   * 事件携带来源标识 {@link ChatChunk#SOURCE_MAIN} 以便前端区分多 Agent 协作下的归属。
   *
   * @param request 执行请求
   * @param chunkConsumer 流式片段消费者
   * @param eventConsumer 类型化事件消费者（可为 null）
   */
  private void doExecuteStream(
      AgentExecutionRequest request,
      Consumer<ChatChunk> chunkConsumer,
      Consumer<SseEvent> eventConsumer) {
    String convId = extractConvId(request);
    MiddlewareContext mwContext = createMiddlewareContext(request, eventConsumer);
    notifyAgentStart(mwContext);
    String traceId = mwContext.getTraceId() != null ? mwContext.getTraceId() : startTrace(convId, "REACT_STREAM");
    log.info("[ReAct-Stream] 开始流式执行: convId={}, traceId={}", convId, traceId);

    String responseId = IdGenerator.nextIdStr();
    String model = properties.getLlm().getDefaultModel();
    TokenUsage totalUsage = TokenUsage.zero();

    String userInput = executeInputGuardrails(mwContext, request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      mwContext.setFinished(true);
      notifyAgentEnd(mwContext);
      emitRejectionStream(responseId, chunkConsumer);
      return;
    }

    List<ChatMessage> messages = new ArrayList<>(COLLECTION_CAPACITY);
    String systemPrompt = buildSystemPrompt(request, userInput);
    messages.add(ChatMessage.system(systemPrompt));
    messages.addAll(loadHistory(request, convId));
    messages.add(ChatMessage.user(userInput, convId));

    mwContext.setSystemPrompt(systemPrompt);
    notifySystemPrompt(mwContext);

    List<ToolDefinition> toolDefinitions = new ArrayList<>(toolRegistry.getToolDefinitions());

    for (int i = 0; i < request.getMaxIterations(); i++) {
      ChatRequest llmRequest =
          ChatRequest.builder()
              .model(model)
              .messages(messages)
              .temperature(properties.getLlm().getTemperature())
              .maxTokens(properties.getLlm().getMaxTokens())
              .tools(toolDefinitions)
              .build();

      mwContext.setLlmRequest(llmRequest);
      notifyReasoning(mwContext);
      ChatResponse response = executeLlmCall(mwContext, () -> llmClient.chat(llmRequest));
      if (mwContext.getLlmResponse() == null) {
        mwContext.setLlmResponse(response);
      }

      if (response.getUsage() != null) {
        totalUsage = totalUsage.add(response.getUsage());
      }

      if (response.getContent() != null && !response.getContent().isBlank()) {
        String prefix = i > 0 ? "\n\n[思考" + (i + 1) + "] " : "";
        chunkConsumer.accept(
            ChatChunk.content(responseId, model, prefix + response.getContent())
                .withSource(ChatChunk.SOURCE_MAIN));
        // 携带工具调用时，本轮文本属于推理思考过程，额外推送 reasoning 事件供前端分区展示
        if (response.hasToolCalls()) {
          emitEvent(
              eventConsumer, SseEvent.reasoning(response.getContent()), ChatChunk.SOURCE_MAIN);
        }
      }

      if (!response.hasToolCalls()) {
        String output = applyOutputGuardrails(response.getContent());
        saveConversation(convId, userInput, output, totalUsage);
        traceRecorder.endTrace(traceId, "SUCCESS");
        mwContext.setFinished(true);
        notifyAgentEnd(mwContext);
        emitEvent(eventConsumer, SseEvent.result(output, model), ChatChunk.SOURCE_MAIN);
        chunkConsumer.accept(ChatChunk.finish(responseId, model, "stop", totalUsage));
        return;
      }

      messages.add(response.getMessage());
      List<ToolCall> allowedCalls = filterAllowedTools(request, response.getToolCalls());
      emitToolCallStarted(allowedCalls, eventConsumer);
      for (ToolCall toolCall : allowedCalls) {
        chunkConsumer.accept(
            ChatChunk.content(responseId, model, "\n\n[工具调用] " + toolCall.getName() + "...")
                .withSource(ChatChunk.SOURCE_MAIN));
      }
      ToolBatchOutcome outcome = executeToolBatch(mwContext, traceId, allowedCalls);
      for (ToolCall toolCall : allowedCalls) {
        String result = outcome.results().getOrDefault(toolCall.getId(), "{}");
        mwContext.setToolCall(toolCall);
        mwContext.setToolResult(result);
        notifyObservation(mwContext);
        emitToolCallCompleted(toolCall, result, outcome.durations(), eventConsumer);
        chunkConsumer.accept(
            ChatChunk.content(responseId, model, "\n[工具结果] " + truncateResult(result))
                .withSource(ChatChunk.SOURCE_MAIN));
        messages.add(ChatMessage.tool(toolCall.getId(), result, convId));
      }
    }

    log.warn("[ReAct-Stream] 超过最大迭代次数: convId={}", convId);
    traceRecorder.endTrace(traceId, "MAX_ITERATIONS");
    mwContext.setFinished(true);
    notifyAgentEnd(mwContext);
    chunkConsumer.accept(
        ChatChunk.content(responseId, model, "\n\n抱歉，我已达到最大推理次数限制，无法完成此任务。")
            .withSource(ChatChunk.SOURCE_MAIN));
    chunkConsumer.accept(ChatChunk.finish(responseId, model, "max_iterations", totalUsage));
  }

  /**
   * 推送单个类型化事件（消费者为空时静默忽略）。
   *
   * @param eventConsumer 事件消费者（可为 null）
   * @param event 待推送事件
   * @param source 事件来源标识
   */
  private void emitEvent(Consumer<SseEvent> eventConsumer, SseEvent event, String source) {
    if (eventConsumer == null || event == null) {
      return;
    }
    eventConsumer.accept(event.withSource(source));
  }

  /**
   * 推送工具调用开始事件。
   *
   * @param toolCalls 本批次工具调用
   * @param eventConsumer 事件消费者（可为 null）
   */
  private void emitToolCallStarted(List<ToolCall> toolCalls, Consumer<SseEvent> eventConsumer) {
    if (eventConsumer == null || toolCalls == null) {
      return;
    }
    for (ToolCall toolCall : toolCalls) {
      emitEvent(
          eventConsumer,
          SseEvent.toolCallStarted(toolCall.getName(), toolCall.getArguments()),
          ChatChunk.SOURCE_MAIN);
    }
  }

  /**
   * 推送工具调用完成事件。
   *
   * @param toolCall 已执行的工具调用
   * @param result 执行结果
   * @param durations 执行耗时表（callId → 毫秒）
   * @param eventConsumer 事件消费者（可为 null）
   */
  private void emitToolCallCompleted(
      ToolCall toolCall,
      String result,
      Map<String, Long> durations,
      Consumer<SseEvent> eventConsumer) {
    if (eventConsumer == null) {
      return;
    }
    Long duration = durations != null ? durations.get(toolCall.getId()) : null;
    emitEvent(
        eventConsumer,
        SseEvent.toolCallCompleted(
            toolCall.getName(), truncateResult(result), duration != null ? duration : 0L),
        ChatChunk.SOURCE_MAIN);
  }

  /**
   * 执行工具批次（经中间件 onActing 洋葱模型包装）。
   *
   * @param mwContext 中间件上下文
   * @param traceId 链路 ID
   * @param toolCalls 本批次工具调用
   * @return 执行结果与耗时
   */
  private ToolBatchOutcome executeToolBatch(
      MiddlewareContext mwContext, String traceId, List<ToolCall> toolCalls) {
    Map<String, Long> durations = new ConcurrentHashMap<>(toolCalls.size());
    Map<String, String> results =
        executeActing(
            mwContext, toolCalls, () -> executeToolsConcurrently(traceId, toolCalls, durations));
    return new ToolBatchOutcome(results, durations);
  }

  /**
   * 工具批次执行结果。
   *
   * @param results callId → 结果文本
   * @param durations callId → 执行耗时（毫秒）
   */
  private record ToolBatchOutcome(Map<String, String> results, Map<String, Long> durations) {
  }

  /**
   * 单轮迭代结果。
   *
   * @param response 最终响应（非 null 时本轮结束）
   * @param totalUsage 更新后的累计 Token 用量
   */
  private record IterationOutcome(ChatResponse response, TokenUsage totalUsage) {
  }

  private String truncateResult(String result) {
    if (result == null) {
      return "";
    }
    return result.length() > TOOL_RESULT_TRUNCATE_LENGTH
        ? result.substring(0, TOOL_RESULT_TRUNCATE_LENGTH) + "..."
        : result;
  }

  /**
   * 白名单过滤工具调用。
   *
   * <p>P1 修复：仅执行 {@link AgentExecutionRequest#getEnabledTools()} 允许的工具， 防御 LLM 幻觉调用未授权工具。
   *
   * @param request 执行请求
   * @param toolCalls LLM 返回的工具调用列表
   * @return 通过白名单校验的工具调用列表
   */
  private List<ToolCall> filterAllowedTools(
      AgentExecutionRequest request, List<ToolCall> toolCalls) {
    List<String> enabledTools = request.getEnabledTools();
    if (toolCalls == null
        || toolCalls.isEmpty()
        || enabledTools == null
        || enabledTools.isEmpty()) {
      return toolCalls != null ? toolCalls : List.of();
    }
    List<ToolCall> allowed = new ArrayList<>(toolCalls.size());
    for (ToolCall toolCall : toolCalls) {
      if (enabledTools.contains(toolCall.getName())) {
        allowed.add(toolCall);
      } else {
        log.warn("[ReAct] 工具不在白名单内，拒绝调用: {}", toolCall.getName());
      }
    }
    return allowed;
  }

  /**
   * 并发执行工具调用并记录链路。
   *
   * <p>P1 优化：LLM 一次返回多个 tool call 时并行执行， 结果按 callId
   * 收集后由调用方按原始顺序回填，保证 tool/tool_result 配对顺序。
   *
   * @param traceId 链路 ID
   * @param toolCalls 待执行的工具调用列表
   * @param durations 执行耗时收集容器（callId → 毫秒），由调用方传入以便事件推送复用
   * @return callId → 工具执行结果
   */
  private Map<String, String> executeToolsConcurrently(
      String traceId, List<ToolCall> toolCalls, Map<String, Long> durations) {
    Map<String, String> results = new ConcurrentHashMap<>(toolCalls.size());
    if (toolCalls.isEmpty()) {
      return results;
    }
    List<CompletableFuture<Void>> futures = new ArrayList<>(toolCalls.size());
    for (ToolCall toolCall : toolCalls) {
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                long toolStart = System.currentTimeMillis();
                String result = toolRegistry.execute(toolCall);
                long toolDuration = System.currentTimeMillis() - toolStart;
                durations.put(toolCall.getId(), toolDuration);
                // TraceRecorder 记录工具调用步骤（先持久化，后由中间件按需驱逐结果）
                traceRecorder.recordStep(
                    traceId,
                    "TOOL_CALL",
                    toolCall.getName(),
                    toolCall.getArguments(),
                    result,
                    toolDuration);
                results.put(toolCall.getId(), result);
              },
              TOOL_EXECUTOR));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return results;
  }

  @Override
  public String getType() {
    return "react";
  }

  @Override
  public boolean supports(String type) {
    return "react".equalsIgnoreCase(type) || "react_agent".equalsIgnoreCase(type)
        || "rag".equalsIgnoreCase(type);
  }

  /**
   * 执行输入护栏（优先中间件，回退 GuardrailService）。
   *
   * @param mwContext 中间件上下文
   * @param userInput 用户原始输入
   * @return 脱敏后的输入（或 null 表示被拒绝）
   */
  private String executeInputGuardrails(MiddlewareContext mwContext, String userInput) {
    // 当中间件链包含输入护栏中间件时，护栏逻辑在 onReasoning 钩子中执行（通过抛异常中断）。
    // 此处为向后兼容保留 GuardrailService 调用路径。
    try {
      return applyInputGuardrails(userInput);
    } catch (Exception e) {
      mwContext.setError(e);
      return null;
    }
  }

  /**
   * 构建系统 Prompt（含可选 RAG 知识增强）。
   *
   * <p>当 {@link #ragService} 不为 null 时，会根据用户输入检索知识库，将检索到的上下文注入 System Prompt。
   *
   * @param request 执行请求
   * @param userInput 用户输入（用于 RAG 检索）
   * @return 构建后的系统 Prompt
   */
  private String buildSystemPrompt(AgentExecutionRequest request, String userInput) {
    StringBuilder sb = new StringBuilder();
    if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
      sb.append(request.getSystemPrompt());
    } else {
      String templateContent =
          promptTemplateProvider.load(properties.getPromptTemplate().getReactSystemCode());
      sb.append(
          templateContent != null
              ? templateContent
              : "你是 YDSZ 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。");
    }
    // P1-1: RAG 知识增强（可选）
    if (ragService != null && userInput != null && !userInput.isBlank()) {
      String ragContext = retrieveRagContext(userInput, request);
      if (ragContext != null && !ragContext.isBlank()) {
        sb.append("\n\n").append(ragContext);
      }
    }
    if (toolRegistry.size() > 0) {
      sb.append("\n\n你可以使用以下工具：\n");
      for (var tool : toolRegistry.getToolDefinitions()) {
        sb.append("- ").append(tool.getName());
        if (tool.getDescription() != null) {
          sb.append(": ").append(tool.getDescription());
        }
        sb.append("\n");
      }
      sb.append("\n请根据用户需求决定是否使用工具。如果不需要工具，直接回答即可。");
    }
    return sb.toString();
  }

  /**
   * 检索 RAG 上下文（内部方法，失败时返回 null 而非抛出异常）。
   *
   * @param userInput 用户输入
   * @param request 执行请求（用于 trace 记录）
   * @return RAG 上下文字符串，检索失败时返回 null
   */
  private String retrieveRagContext(String userInput, AgentExecutionRequest request) {
    try {
      List<TextChunk> chunks = ragService.retrieve(userInput);
      if (chunks.isEmpty()) {
        return null;
      }
      return ragService.buildContext(chunks);
    } catch (Exception e) {
      log.warn("[ReAct] RAG 检索失败，跳过知识增强: {}", e.getMessage());
      return null;
    }
  }

  private ChatResponse buildMaxIterationsResponse(String convId, TokenUsage usage) {
    ChatMessage msg = ChatMessage.assistant("抱歉，我已达到最大推理次数限制，无法完成此任务。请尝试简化您的问题。", convId, usage);
    return new ChatResponse(
        IdGenerator.nextIdStr(),
        properties.getLlm().getDefaultModel(),
        msg,
        usage,
        "max_iterations",
        List.of());
  }
}
