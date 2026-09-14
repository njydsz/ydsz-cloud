package com.njydsz.agent.domain.middleware;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.SseEvent;
import com.njydsz.agent.domain.model.ToolCall;

/**
 * 中间件上下文 — 贯穿 Agent 执行全链路的状态载体。
 *
 * <p>封装 Agent 一次执行的完整中间状态：请求、系统 Prompt、消息历史、工具调用、响应、异常。
 * 中间件通过读写此对象实现拦截、增强、拒绝等行为。
 *
 * <p><b>线程安全</b>：每个 Agent 执行实例独占一个 MiddlewareContext，不存在跨线程共享，
 * 因此内部字段不做同步保护。禁止将同一 Context 实例跨执行复用。
 *
 * <p><b>对标 AgentScope</b>：AgentScope 的 MiddlewareBase 暴露 5 个钩子位置
 * (onAgent/onReasoning/onActing/onModelCall/onSystemPrompt)，本上下文为这些钩子提供统一的参数载体。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public class MiddlewareContext {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 8;

  /** 原始执行请求（不可变） */
  private final AgentExecutionRequest executionRequest;

  /** 对话 ID */
  private final String conversationId;

  /** 链路追踪 ID */
  private String traceId;

  /** 系统 Prompt（中间件可修改） */
  private String systemPrompt;

  /** 构建完成的 LLM 请求（中间件可修改消息列表） */
  private ChatRequest llmRequest;

  /** LLM 响应（中间件可修改） */
  private ChatResponse llmResponse;

  /** 本次迭代检测到的工具调用（单个） */
  private ToolCall toolCall;

  /** 工具执行结果 */
  private String toolResult;

  /** 本次 Acting 阶段待执行/已执行的工具调用批次（onActing 钩子使用） */
  private List<ToolCall> toolCalls;

  /** Acting 阶段工具执行结果（callId → 结果文本，onActing 钩子可读写） */
  private Map<String, String> toolResults;

  /** 类型化事件消费者（流式路径下由执行器注入，中间件可据此把事件推入当前 SSE 流） */
  private Consumer<SseEvent> eventConsumer;

  /** 执行结束标记 */
  private boolean finished;

  /** 执行异常 */
  private Throwable error;

  /** 自定义属性（中间件间传递扩展数据） */
  private final Map<String, Object> attributes = new HashMap<>(COLLECTION_CAPACITY);

  /**
   * 构造中间件上下文。
   *
   * @param executionRequest 原始执行请求
   * @param conversationId 对话 ID
   */
  public MiddlewareContext(AgentExecutionRequest executionRequest, String conversationId) {
    this.executionRequest = executionRequest;
    this.conversationId = conversationId;
  }

  public AgentExecutionRequest getExecutionRequest() {
    return executionRequest;
  }

  public String getConversationId() {
    return conversationId;
  }

  public String getTraceId() {
    return traceId;
  }

  /**
   * 设置链路追踪 ID。
   *
   * @param traceId 追踪 ID
   */
  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  /**
   * 设置系统 Prompt。
   *
   * @param systemPrompt 系统提示词文本
   */
  public void setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public ChatRequest getLlmRequest() {
    return llmRequest;
  }

  /**
   * 设置 LLM 请求。
   *
   * @param llmRequest 构建完成的 LLM 请求
   */
  public void setLlmRequest(ChatRequest llmRequest) {
    this.llmRequest = llmRequest;
  }

  public ChatResponse getLlmResponse() {
    return llmResponse;
  }

  /**
   * 设置 LLM 响应。
   *
   * @param llmResponse LLM 返回的响应
   */
  public void setLlmResponse(ChatResponse llmResponse) {
    this.llmResponse = llmResponse;
  }

  public ToolCall getToolCall() {
    return toolCall;
  }

  /**
   * 设置工具调用。
   *
   * @param toolCall 检测到的工具调用
   */
  public void setToolCall(ToolCall toolCall) {
    this.toolCall = toolCall;
  }

  public String getToolResult() {
    return toolResult;
  }

  /**
   * 设置工具执行结果。
   *
   * @param toolResult 工具返回的观察文本
   */
  public void setToolResult(String toolResult) {
    this.toolResult = toolResult;
  }

  /**
   * 获取本次 Acting 阶段的工具调用批次。
   *
   * @return 工具调用列表（onActing 钩子执行前设置）
   */
  public List<ToolCall> getToolCalls() {
    return toolCalls;
  }

  /**
   * 设置本次 Acting 阶段的工具调用批次。
   *
   * @param toolCalls 工具调用列表
   */
  public void setToolCalls(List<ToolCall> toolCalls) {
    this.toolCalls = toolCalls;
  }

  /**
   * 获取 Acting 阶段工具执行结果。
   *
   * @return callId → 结果文本（结果返回后由框架回填）
   */
  public Map<String, String> getToolResults() {
    return toolResults;
  }

  /**
   * 设置 Acting 阶段工具执行结果。
   *
   * @param toolResults callId → 结果文本
   */
  public void setToolResults(Map<String, String> toolResults) {
    this.toolResults = toolResults;
  }

  public boolean isFinished() {
    return finished;
  }

  /**
   * 标记执行结束。
   *
   * @param finished true=结束
   */
  public void setFinished(boolean finished) {
    this.finished = finished;
  }

  public Throwable getError() {
    return error;
  }

  /**
   * 设置执行异常。
   *
   * @param error 异常对象
   */
  public void setError(Throwable error) {
    this.error = error;
  }

  /**
   * 存储扩展属性。
   *
   * <p>中间件间传递数据的推荐方式。key 命名规范：{@code middleware-name.field}，
   * 如 {@code audit.userId}、{@code rateLimit.remaining}。
   *
   * @param key 属性键
   * @param value 属性值
   */
  public void setAttribute(String key, Object value) {
    this.attributes.put(key, value);
  }

  /**
   * 读取扩展属性。
   *
   * @param key 属性键
   * @return 属性值，不存在返回 null
   */
  public Object getAttribute(String key) {
    return this.attributes.get(key);
  }

  /**
   * 获取类型安全的扩展属性。
   *
   * @param key 属性键
   * @param type 期望类型
   * @param <T> 类型参数
   * @return 属性值，不存在或类型不匹配返回 null
   */
  @SuppressWarnings("unchecked")
  public <T> T getAttribute(String key, Class<T> type) {
    Object value = this.attributes.get(key);
    if (value != null && type.isInstance(value)) {
      return (T) value;
    }
    return null;
  }
}
