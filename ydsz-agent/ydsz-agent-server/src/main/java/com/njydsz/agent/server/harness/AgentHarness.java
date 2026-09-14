package com.njydsz.agent.server.harness;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.agent.DagProgressEvent;
import com.njydsz.agent.domain.context.ContextCompressor;
import com.njydsz.agent.domain.context.ContextOverflowException;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.SseEvent;
import com.njydsz.agent.domain.workspace.AgentWorkspace;
import com.njydsz.agent.domain.workspace.AgentWorkspaceStore;

/**
 * Agent Harness — 对标 AgentScope HarnessAgent 的生产就绪执行入口。
 *
 * <p>在「推理核」之外叠加工程能力，推理核（各 {@link AgentExecutor}）保持无状态与极简：
 *
 * <ol>
 *   <li><b>上下文预算守门</b>：执行前按 Token 预算从记忆加载历史，超限时经
 *       {@link ContextCompressor} 压缩后以 {@link AgentExecutionRequest#withContextMessages(List)}
 *       注入请求，避免把超长上下文直接交给模型（对应 AgentScope 的 context_length_exceeded 前置兜底）；
 *   <li><b>溢出自动重试</b>：捕获 {@link ContextOverflowException} 后按 {@code maxRetryOnOverflow}
 *       以递减预算重新准备上下文并重试，而非直接把异常抛给调用方；
 *   <li><b>工作区生命周期</b>：执行前确保工作区存在（按 Agent 编码寻址），执行后回写最近一轮
 *       对话摘要，使工作区状态随执行推进（此前 {@link AgentWorkspaceStore} 无任何调用方）。
 * </ol>
 *
 * <h3>使用方式</h3>
 *
 * <pre>{@code
 * AgentHarness harness = AgentHarness.builder()
 *     .workspaceStore(redisWorkspaceStore)
 *     .memory(conversationMemory)
 *     .contextCompressor(slidingWindowCompressor)
 *     .maxRetryOnOverflow(2)
 *     .build();
 *
 * ChatResponse response = harness.execute(request, executor);
 * }</pre>
 *
 * <p><b>职责边界</b>：中间件链由执行器通过 {@code AbstractAgentExecutor} 统一驱动
 * （onAgentStart/onSystemPrompt/onReasoning/onModelCall/onActing/onObservation/onAgentEnd），
 * Harness 不重复持有中间件链，避免 onAgent 钩子被二次触发。
 *
 * <p><b>线程安全</b>：全部字段 final 且为无状态组件引用，实例可安全并发复用。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
public class AgentHarness {

  /** 上下文 Token 预算默认值（估算值） */
  public static final int DEFAULT_CONTEXT_TOKEN_BUDGET = 12000;

  /** 工作区记忆摘要最大字符数，超出时保留尾部（近期对话更有价值） */
  private static final int WORKSPACE_MEMORY_MAX_CHARS = 4000;

  /** 记忆摘要记录分隔符 */
  private static final String DIGEST_SEPARATOR = "\n---\n";

  /** 工作区存储（可选） */
  private final AgentWorkspaceStore workspaceStore;

  /** 对话记忆（用于执行前的上下文预算裁剪，可选） */
  private final ConversationMemory memory;

  /** 上下文压缩策略（可选） */
  private final ContextCompressor contextCompressor;

  /** 上下文溢出处理器（由压缩策略构建，无策略时为 null） */
  private final ContextOverflowHandler overflowHandler;

  /** 上下文溢出最大重试次数 */
  private final int maxRetryOnOverflow;

  /** 上下文 Token 预算（估算值） */
  private final int contextTokenBudget;

  private AgentHarness(Builder builder) {
    this.workspaceStore = builder.workspaceStore;
    this.memory = builder.memory;
    this.contextCompressor = builder.contextCompressor;
    this.overflowHandler =
        builder.contextCompressor != null
            ? new ContextOverflowHandler(builder.contextCompressor)
            : null;
    this.maxRetryOnOverflow = builder.maxRetryOnOverflow;
    this.contextTokenBudget =
        builder.contextTokenBudget > 0 ? builder.contextTokenBudget : DEFAULT_CONTEXT_TOKEN_BUDGET;
    log.info(
        "[Harness] 初始化完成: workspace={}, memory={}, compressor={}, tokenBudget={}, maxRetry={}",
        workspaceStore != null ? workspaceStore.getBackendType() : "disabled",
        memory != null ? "enabled" : "disabled",
        contextCompressor != null ? contextCompressor.getName() : "disabled",
        contextTokenBudget,
        maxRetryOnOverflow);
  }

  /**
   * 获取工作区存储。
   *
   * @return 工作区存储实例（可能为 null）
   */
  public AgentWorkspaceStore getWorkspaceStore() {
    return workspaceStore;
  }

  /**
   * 获取上下文压缩策略。
   *
   * @return 压缩策略实例（可能为 null）
   */
  public ContextCompressor getContextCompressor() {
    return contextCompressor;
  }

  /**
   * 获取上下文溢出最大重试次数。
   *
   * @return 最大重试次数
   */
  public int getMaxRetryOnOverflow() {
    return maxRetryOnOverflow;
  }

  /**
   * 获取上下文 Token 预算。
   *
   * @return Token 预算（估算值）
   */
  public int getContextTokenBudget() {
    return contextTokenBudget;
  }

  // ========================= 执行入口 =========================

  /**
   * 同步执行（工作区生命周期 + 上下文预算 + 溢出重试）。
   *
   * @param request 执行请求
   * @param executor 由调用方按 Agent 类型路由后的执行器
   * @return Agent 响应
   * @throws NullPointerException executor 为 null 时抛出
   * @throws ContextOverflowException 重试耗尽后仍上下文超限时抛出
   */
  public ChatResponse execute(AgentExecutionRequest request, AgentExecutor executor) {
    Objects.requireNonNull(executor, "executor 不能为 null");
    AgentWorkspace workspace = prepareWorkspace(request);
    int budget = contextTokenBudget;
    ContextOverflowException overflow = null;
    for (int attempt = 0; attempt <= maxRetryOnOverflow; attempt++) {
      AgentExecutionRequest prepared = prepareContext(request, budget);
      try {
        ChatResponse response = executor.execute(prepared);
        persistWorkspace(workspace, prepared, response.getContent());
        return response;
      } catch (ContextOverflowException e) {
        overflow = e;
        budget = Math.max(1, budget / 2);
        log.warn(
            "[Harness] 上下文溢出，第 {} 次重试: budget={}",
            attempt + 1,
            budget);
      }
    }
    // 重试耗尽：工作区仍回写（保留本轮用户输入，便于运维定位），异常上抛由调用方决策
    persistWorkspace(workspace, prepareContext(request, budget), null);
    throw overflow;
  }

  /**
   * 流式执行（工作区生命周期 + 上下文预算 + 事件透传）。
   *
   * <p>流式路径不做溢出重试——片段已推送给前端，重试会造成重复输出；
   * 上下文预算守门仍在执行前生效，避免流式请求一开始即超限。
   *
   * @param request 执行请求
   * @param executor 由调用方按 Agent 类型路由后的执行器
   * @param chunkConsumer 流式片段消费者
   * @param progressConsumer 编排进度消费者（可为 null）
   * @param eventConsumer 类型化事件消费者（可为 null）
   */
  public void executeStream(
      AgentExecutionRequest request,
      AgentExecutor executor,
      Consumer<ChatChunk> chunkConsumer,
      Consumer<DagProgressEvent> progressConsumer,
      Consumer<SseEvent> eventConsumer) {
    Objects.requireNonNull(executor, "executor 不能为 null");
    AgentWorkspace workspace = prepareWorkspace(request);
    AgentExecutionRequest prepared = prepareContext(request, contextTokenBudget);
    executor.executeStream(prepared, chunkConsumer, progressConsumer, eventConsumer);
    persistWorkspace(workspace, prepared, null);
  }

  // ========================= 内部实现 =========================

  /**
   * 准备上下文：按 Token 预算加载历史，超限时压缩。
   *
   * @param request 执行请求
   * @param tokenBudget Token 预算
   * @return 携带预置上下文消息的请求（无记忆或无历史时原样返回）
   */
  private AgentExecutionRequest prepareContext(AgentExecutionRequest request, int tokenBudget) {
    String conversationId = request.getConversationId();
    if (memory == null || conversationId == null || conversationId.isBlank()) {
      return request;
    }
    List<ChatMessage> history;
    try {
      history =
          memory.loadWithTokenBudget(
              conversationId, tokenBudget, ContextCompressor.DEFAULT_TOKEN_CHAR_RATIO);
    } catch (Exception e) {
      log.warn(
          "[Harness] 加载历史失败，跳过上下文预算守门: convId={}, error={}",
          conversationId,
          e.getMessage());
      return request;
    }
    if (history == null || history.isEmpty()) {
      return request;
    }
    return request.withContextMessages(compressIfNeeded(history, tokenBudget));
  }

  /**
   * 超预算时压缩消息列表（无压缩策略或压缩失败时返回原始列表）。
   *
   * @param messages 历史消息
   * @param tokenBudget Token 预算
   * @return 压缩后（或原始）消息列表
   */
  private List<ChatMessage> compressIfNeeded(List<ChatMessage> messages, int tokenBudget) {
    if (overflowHandler == null) {
      return messages;
    }
    try {
      return overflowHandler.compressIfNeeded(messages, tokenBudget);
    } catch (Exception e) {
      log.warn("[Harness] 上下文压缩失败，沿用原始历史: error={}", e.getMessage());
      return messages;
    }
  }

  /**
   * 准备（加载或创建）工作区。
   *
   * @param request 执行请求
   * @return 工作区实例；无存储后端或无法确定工作区 ID 时返回 null
   */
  private AgentWorkspace prepareWorkspace(AgentExecutionRequest request) {
    String workspaceId = resolveWorkspaceId(request);
    if (workspaceStore == null || workspaceId == null) {
      return null;
    }
    try {
      AgentWorkspace existing = workspaceStore.load(workspaceId);
      if (existing != null) {
        return existing;
      }
      AgentWorkspace created = AgentWorkspace.builder().workspaceId(workspaceId).build();
      workspaceStore.save(created);
      log.debug("[Harness] 初始化工作区: id={}", workspaceId);
      return created;
    } catch (Exception e) {
      log.warn(
          "[Harness] 工作区准备失败，跳过工作区能力: id={}, error={}",
          workspaceId,
          e.getMessage());
      return null;
    }
  }

  /**
   * 回写工作区（把最近一轮对话压缩为记忆摘要，版本递增）。
   *
   * @param workspace 工作区（可能为 null）
   * @param request 已准备上下文的执行请求
   * @param assistantOutput 助手输出（可为 null，流式路径不采集）
   */
  private void persistWorkspace(
      AgentWorkspace workspace, AgentExecutionRequest request, String assistantOutput) {
    if (workspace == null || workspaceStore == null) {
      return;
    }
    try {
      String digest =
          buildMemoryDigest(workspace.getMemory(), request.getUserInput(), assistantOutput);
      workspaceStore.save(workspace.withMemory(digest));
    } catch (Exception e) {
      log.warn(
          "[Harness] 工作区回写失败（不影响主流程）: id={}, error={}",
          workspace.getWorkspaceId(),
          e.getMessage());
    }
  }

  /**
   * 解析工作区 ID（以 Agent 编码为工作区标识）。
   *
   * @param request 执行请求
   * @return 工作区 ID；未指定 Agent 编码时返回 null（按无工作区语义执行）
   */
  private String resolveWorkspaceId(AgentExecutionRequest request) {
    String agentCode = request.getAgentCode();
    return agentCode != null && !agentCode.isBlank() ? agentCode : null;
  }

  /**
   * 构建工作区记忆摘要（保留尾部，控制总体长度）。
   *
   * @param existingMemory 已有记忆内容
   * @param userInput 本轮用户输入
   * @param assistantOutput 本轮助手输出（可为 null）
   * @return 追加本轮对话后的记忆摘要
   */
  private String buildMemoryDigest(
      String existingMemory, String userInput, String assistantOutput) {
    StringBuilder sb = new StringBuilder();
    if (existingMemory != null && !existingMemory.isBlank()) {
      sb.append(existingMemory);
    }
    if (userInput != null && !userInput.isBlank()) {
      if (sb.length() > 0) {
        sb.append(DIGEST_SEPARATOR);
      }
      sb.append("Q: ").append(userInput.trim());
    }
    if (assistantOutput != null && !assistantOutput.isBlank()) {
      sb.append(DIGEST_SEPARATOR).append("A: ").append(assistantOutput.trim());
    }
    String digest = sb.toString();
    if (digest.length() <= WORKSPACE_MEMORY_MAX_CHARS) {
      return digest;
    }
    // 保留尾部：近期对话对下一次执行更有参考价值
    return digest.substring(digest.length() - WORKSPACE_MEMORY_MAX_CHARS);
  }

  /**
   * 创建 Builder 入口。
   *
   * @return 新的 Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * AgentHarness 构建器。
   */
  public static final class Builder {
    private AgentWorkspaceStore workspaceStore;
    private ConversationMemory memory;
    private ContextCompressor contextCompressor;
    private int maxRetryOnOverflow = 1;
    private int contextTokenBudget = DEFAULT_CONTEXT_TOKEN_BUDGET;

    /**
     * 设置工作区存储后端。
     *
     * @param workspaceStore 工作区存储实例
     * @return 当前 Builder
     */
    public Builder workspaceStore(AgentWorkspaceStore workspaceStore) {
      this.workspaceStore = workspaceStore;
      return this;
    }

    /**
     * 设置对话记忆（用于执行前的上下文预算守门）。
     *
     * @param memory 对话记忆实例
     * @return 当前 Builder
     */
    public Builder memory(ConversationMemory memory) {
      this.memory = memory;
      return this;
    }

    /**
     * 设置上下文压缩策略。
     *
     * @param contextCompressor 上下文压缩策略
     * @return 当前 Builder
     */
    public Builder contextCompressor(ContextCompressor contextCompressor) {
      this.contextCompressor = contextCompressor;
      return this;
    }

    /**
     * 设置上下文溢出最大重试次数。
     *
     * @param maxRetryOnOverflow 重试次数（默认 1）
     * @return 当前 Builder
     */
    public Builder maxRetryOnOverflow(int maxRetryOnOverflow) {
      this.maxRetryOnOverflow = Math.max(0, maxRetryOnOverflow);
      return this;
    }

    /**
     * 设置上下文 Token 预算。
     *
     * @param contextTokenBudget Token 预算（非正数回退默认值）
     * @return 当前 Builder
     */
    public Builder contextTokenBudget(int contextTokenBudget) {
      this.contextTokenBudget = contextTokenBudget;
      return this;
    }

    /**
     * 构建 AgentHarness 实例。
     *
     * @return 新的 AgentHarness 实例
     */
    public AgentHarness build() {
      return new AgentHarness(this);
    }
  }
}
