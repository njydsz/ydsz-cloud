package com.njydsz.agent.server.agent;

import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareChain;
import com.njydsz.agent.domain.middleware.MiddlewareContext;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.common.util.id.IdGenerator;

/**
 * Agent 执行器抽象基类
 *
 * <p>提取各执行器的公共依赖和通用逻辑，消除重复代码：
 *
 * <ul>
 *   <li>公共字段（llmClient / memory / properties / traceRecorder / agentMetrics / costAnalysisService /
 *       guardrailService / promptTemplateProvider）
 *   <li>对话 ID 提取（{@link #extractConvId}）
 *   <li>链路追踪启动（{@link #startTrace}）
 *   <li>输入护栏统一处理（{@link #applyInputGuardrails}）
 *   <li>LLM 调用异常记录（{@link #recordLlmError}）
 * </ul>
 *
 * <p>子类只需实现具体的执行逻辑，无需重复声明公共字段和基础方法。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public abstract class AbstractAgentExecutor implements AgentExecutor {

  /** LLM 客户端 */
  protected final LlmClient llmClient;

  /** 对话记忆（历史消息加载/保存） */
  protected final ConversationMemory memory;

  /** Agent 配置属性 */
  protected final AgentProperties properties;

  /** 链路追踪记录器 */
  protected final TraceRecorder traceRecorder;

  /** Agent 监控指标采集器 */
  protected final AgentMetrics agentMetrics;

  /** 成本分析服务（Token 用量核算） */
  protected final CostAnalysisService costAnalysisService;

  /** 护栏编排服务（统一驱动输入/输出护栏） */
  protected final GuardrailService guardrailService;

  /** Prompt 模板提供者（加载外部化模板） */
  protected final PromptTemplateProvider promptTemplateProvider;

  /** 中间件链（可选，为 null 时跳过中间件调用） */
  protected final MiddlewareChain middlewareChain;

  /**
   * 构造函数（注入公共依赖）。
   *
   * @param llmClient LLM 客户端
   * @param memory 对话记忆
   * @param properties Agent 配置属性
   * @param traceRecorder 链路追踪记录器
   * @param agentMetrics Agent 监控指标采集器
   * @param costAnalysisService 成本分析服务
   * @param guardrailService 护栏编排服务
   * @param promptTemplateProvider Prompt 模板提供者
   * @param middlewareChain 中间件链（可为 null）
   */
  protected AbstractAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      AgentProperties properties,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService,
      PromptTemplateProvider promptTemplateProvider,
      MiddlewareChain middlewareChain) {
    this.llmClient = llmClient;
    this.memory = memory;
    this.properties = properties;
    this.traceRecorder = traceRecorder;
    this.agentMetrics = agentMetrics;
    this.costAnalysisService = costAnalysisService;
    this.guardrailService = guardrailService;
    this.promptTemplateProvider = promptTemplateProvider;
    this.middlewareChain = middlewareChain;
  }

  /**
   * 从请求中提取对话 ID，若未提供则生成新 ID。
   *
   * @param request 执行请求
   * @return 对话 ID
   */
  protected String extractConvId(AgentExecutionRequest request) {
    return request.getConversationId() != null
        ? request.getConversationId()
        : IdGenerator.nextIdStr();
  }

  /**
   * 启动链路追踪。
   *
   * @param convId 对话 ID
   * @param traceType 追踪类型标识（如 "CHAT"、"REACT"）
   * @return 追踪 ID
   */
  protected String startTrace(String convId, String traceType) {
    return traceRecorder.startTrace(convId, traceType);
  }

  /**
   * 应用输入护栏。
   *
   * @param userInput 用户原始输入
   * @return 护栏通过后的输入；拒绝时返回 null
   */
  protected String applyInputGuardrails(String userInput) {
    return guardrailService.applyInputGuardrails(userInput);
  }

  /**
   * 应用输出护栏。
   *
   * @param output 模型输出内容
   * @return 护栏通过后的输出
   */
  protected String applyOutputGuardrails(String output) {
    return guardrailService.applyOutputGuardrails(output);
  }

  /**
   * 记录 LLM 调用异常并结束追踪（同步场景）。
   *
   * @param traceId 追踪 ID
   * @param traceType 追踪类型
   * @param request 执行请求
   * @param e 异常
   * @param startTime 调用开始时间
   */
  protected void recordLlmError(
      String traceId, String traceType, AgentExecutionRequest request, Exception e, long startTime) {
    long duration = System.currentTimeMillis() - startTime;
    agentMetrics.recordLlmCall(
        llmClient.getProvider(), properties.getLlm().getDefaultModel(), duration, null, e);
    traceRecorder.recordStep(
        traceId,
        "LLM_CALL_ERROR",
        traceType + " LLM 调用失败",
        request.getUserInput(),
        e.getMessage(),
        duration);
    traceRecorder.endTrace(traceId, "FAILED");
  }

  /**
   * 记录 LLM 调用异常（流式场景）。
   *
   * @param traceId 追踪 ID
   * @param llmRequest LLM 请求
   * @param e 异常
   * @param startTime 调用开始时间
   */
  protected void recordLlmStreamError(
      String traceId, Object llmRequest, Exception e, long startTime) {
    long duration = System.currentTimeMillis() - startTime;
    agentMetrics.recordLlmCall(
        llmClient.getProvider(), properties.getLlm().getDefaultModel(), duration, null, e);
    traceRecorder.recordStep(
        traceId, "LLM_CALL_ERROR", "Stream failed", llmRequest, e.getMessage(), duration);
    traceRecorder.endTrace(traceId, "FAILED");
  }

  /**
   * 记录 LLM 调用成功（同步场景）。
   *
   * @param convId 对话 ID
   * @param traceId 追踪 ID
   * @param traceType 追踪类型描述
   * @param request LLM 请求
   * @param response LLM 响应
   * @param startTime 调用开始时间
   */
  protected void recordLlmSuccess(
      String convId,
      String traceId,
      String traceType,
      Object request,
      ChatResponse response,
      long startTime) {
    long duration = System.currentTimeMillis() - startTime;
    agentMetrics.recordLlmCall(
        llmClient.getProvider(), properties.getLlm().getDefaultModel(), duration, response, null);
    if (response.getUsage() != null && costAnalysisService != null) {
      costAnalysisService.recordUsage(
          convId, properties.getLlm().getDefaultModel(), response.getUsage());
    }
    traceRecorder.recordStep(traceId, "LLM_CALL", traceType, request, response, duration);
  }

  /**
   * 保存对话记忆（用户输入 + 助手回复）。
   *
   * @param convId 对话 ID
   * @param userInput 用户输入
   * @param output 助手回复
   * @param usage Token 用量
   */
  protected void saveConversation(String convId, String userInput, String output, TokenUsage usage) {
    memory.save(convId, ChatMessage.user(userInput, convId));
    memory.save(convId, ChatMessage.assistant(output, convId, usage));
  }

  /**
   * 构建护栏拒绝响应（同步场景）。
   *
   * @param reason 拒绝原因
   * @return 拒绝响应
   */
  protected ChatResponse buildRejectedResponse(String reason) {
    ChatMessage msg = ChatMessage.assistant("抱歉，" + reason + "。", null, TokenUsage.zero());
    return new ChatResponse(
        IdGenerator.nextIdStr(),
        "guardrail",
        msg,
        TokenUsage.zero(),
        "guardrail_rejected",
        List.of());
  }

  /**
   * 推送护栏拒绝消息（流式场景）。
   *
   * @param responseId 响应 ID
   * @param chunkConsumer 流式消费者
   */
  protected void emitRejectionStream(String responseId, Consumer<ChatChunk> chunkConsumer) {
    String model = properties.getLlm().getDefaultModel();
    chunkConsumer.accept(ChatChunk.content(responseId, model, "抱歉，您的输入被安全护栏拒绝。"));
    chunkConsumer.accept(ChatChunk.finish(responseId, model, "guardrail_rejected", null));
  }

  /**
   * 创建中间件上下文。
   *
   * @param request 执行请求
   * @return 新的中间件上下文实例
   */
  protected MiddlewareContext createMiddlewareContext(AgentExecutionRequest request) {
    return new MiddlewareContext(request, extractConvId(request));
  }

  /**
   * 通知 Agent 执行开始（触发中间件 onAgentStart 钩子）。
   *
   * @param context 中间件上下文
   */
  protected void notifyAgentStart(MiddlewareContext context) {
    if (middlewareChain != null) {
      middlewareChain.executeAgentStart(context);
    }
  }

  /**
   * 通知系统 Prompt 构建（触发中间件 onSystemPrompt 钩子）。
   *
   * @param context 中间件上下文
   */
  protected void notifySystemPrompt(MiddlewareContext context) {
    if (middlewareChain != null) {
      middlewareChain.executeSystemPrompt(context);
    }
  }

  /**
   * 通知 LLM 推理前（触发中间件 onReasoning 钩子）。
   *
   * @param context 中间件上下文
   */
  protected void notifyReasoning(MiddlewareContext context) {
    if (middlewareChain != null) {
      middlewareChain.executeReasoning(context);
    }
  }

  /**
   * 执行 LLM 调用（触发中间件 onModelCall 洋葱模型）。
   *
   * @param context 中间件上下文
   * @param finalCall 实际 LLM 调用函数
   * @return LLM 响应
   */
  protected ChatResponse executeLlmCall(
      MiddlewareContext context, AgentMiddleware.ModelCallProceed finalCall) {
    if (middlewareChain != null) {
      return middlewareChain.executeModelCall(context, finalCall);
    }
    ChatResponse response = finalCall.execute();
    context.setLlmResponse(response);
    return response;
  }

  /**
   * 通知工具观察（触发中间件 onObservation 钩子）。
   *
   * @param context 中间件上下文
   */
  protected void notifyObservation(MiddlewareContext context) {
    if (middlewareChain != null) {
      middlewareChain.executeObservation(context);
    }
  }

  /**
   * 通知 Agent 执行结束（触发中间件 onAgentEnd 钩子）。
   *
   * @param context 中间件上下文
   */
  protected void notifyAgentEnd(MiddlewareContext context) {
    if (middlewareChain != null) {
      middlewareChain.executeAgentEnd(context);
    }
  }

  /**
   * 获取默认模型名称。
   *
   * @return 模型名称
   */
  protected String getDefaultModel() {
    return properties.getLlm().getDefaultModel();
  }

  /**
   * 解析系统 Prompt 优先级：请求级 > 模板编码 > 配置默认值。
   *
   * @param request 执行请求
   * @param templateCode 模板编码
   * @param fallback 最终回退值
   * @return 实际使用的系统 Prompt
   */
  protected String resolveSystemPrompt(
      AgentExecutionRequest request, String templateCode, String fallback) {
    if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
      return request.getSystemPrompt();
    }
    if (promptTemplateProvider != null) {
      String templateContent = promptTemplateProvider.load(templateCode);
      if (templateContent != null) {
        return templateContent;
      }
    }
    return fallback;
  }
}
