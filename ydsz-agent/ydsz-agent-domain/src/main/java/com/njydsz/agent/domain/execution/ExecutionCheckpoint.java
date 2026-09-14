package com.njydsz.agent.domain.execution;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.model.ToolCall;

/**
 * Agent 执行检查点 — 供会话级暂停/恢复使用。
 *
 * <p>当工具审批门决定暂停执行时，执行器保存当前会话的快照：
 * 已构建的消息列表、待执行的工具调用、已消耗的 Token、当前迭代轮次等。
 * 审批完成后，{@link com.njydsz.agent.server.execution.ExecutionPauseService}
 * 按 {@code approvalId} 取出检查点并恢复执行。
 *
 * <p>检查点要求内部集合不可变，可安全跨线程传递。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
public final class ExecutionCheckpoint implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 触发暂停的审批请求 ID */
  private final String approvalId;

  /** 原执行请求 */
  private final AgentExecutionRequest request;

  /** 对话 ID */
  private final String conversationId;

  /** 链路追踪 ID */
  private final String traceId;

  /** 当前消息列表（正序，包含 system prompt / history / user / assistant tool-call 消息） */
  private final List<ChatMessage> messages;

  /** 待审批完成后执行的工具调用 */
  private final List<ToolCall> pendingToolCalls;

  /** 已消耗 Token */
  private final TokenUsage totalUsage;

  /** 当前迭代轮次（从 0 开始） */
  private final int currentIteration;

  /** 用户输入原文 */
  private final String userInput;

  /** 系统提示词 */
  private final String systemPrompt;

  /** 扩展上下文（中间件可附加用于恢复） */
  private final Map<String, Object> context;

  /**
   * 全参构造。
   *
   * @param approvalId 审批请求 ID
   * @param request 原执行请求
   * @param conversationId 对话 ID
   * @param traceId 链路追踪 ID
   * @param messages 当前消息列表
   * @param pendingToolCalls 待执行工具调用
   * @param totalUsage 已消耗 Token
   * @param currentIteration 当前迭代轮次
   * @param userInput 用户输入原文
   * @param systemPrompt 系统提示词
   * @param context 扩展上下文
   */
  public ExecutionCheckpoint(
      String approvalId,
      AgentExecutionRequest request,
      String conversationId,
      String traceId,
      List<ChatMessage> messages,
      List<ToolCall> pendingToolCalls,
      TokenUsage totalUsage,
      int currentIteration,
      String userInput,
      String systemPrompt,
      Map<String, Object> context) {
    this.approvalId = Objects.requireNonNull(approvalId, "approvalId 不能为 null");
    this.request = Objects.requireNonNull(request, "request 不能为 null");
    this.conversationId = Objects.requireNonNull(conversationId, "conversationId 不能为 null");
    this.traceId = Objects.requireNonNull(traceId, "traceId 不能为 null");
    this.messages = messages != null ? List.copyOf(messages) : List.of();
    this.pendingToolCalls = pendingToolCalls != null ? List.copyOf(pendingToolCalls) : List.of();
    this.totalUsage = totalUsage != null ? totalUsage : TokenUsage.zero();
    this.currentIteration = Math.max(0, currentIteration);
    this.userInput = Objects.requireNonNull(userInput, "userInput 不能为 null");
    this.systemPrompt = systemPrompt;
    this.context = context != null ? Map.copyOf(context) : Map.of();
  }

  public String getApprovalId() {
    return approvalId;
  }

  public AgentExecutionRequest getRequest() {
    return request;
  }

  public String getConversationId() {
    return conversationId;
  }

  public String getTraceId() {
    return traceId;
  }

  public List<ChatMessage> getMessages() {
    return messages;
  }

  public List<ToolCall> getPendingToolCalls() {
    return pendingToolCalls;
  }

  public TokenUsage getTotalUsage() {
    return totalUsage;
  }

  public int getCurrentIteration() {
    return currentIteration;
  }

  public String getUserInput() {
    return userInput;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public Map<String, Object> getContext() {
    return context;
  }
}
