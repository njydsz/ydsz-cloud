package com.njydsz.agent.server.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;

/**
 * Agent 平台运行态可观测指标（P2 增强）。
 *
 * <p>覆盖 Agent 执行生命周期、工具调用、RAG 检索、流式 TTFT、会话活跃度与 DAG 编排六类观测场景。 所有指标名拼接 {@code agent_} 前缀，与 {@link
 * AgentMetrics} 已有的 LLM 基础指标互补。
 *
 * <p><b>暴露的 Prometheus 指标：</b>
 *
 * <ul>
 *   <li>{@code agent_execution_total{type, status}} — Agent 执行次数 （type:
 *       simple/plan_execute/react/router/rag/dag, status: success/failure/timeout）
 *   <li>{@code agent_execution_duration_seconds{type}} — Agent 端到端执行耗时（含重试/迭代）
 *   <li>{@code agent_tool_calls_total{tool_name, status}} — 工具调用次数 （status:
 *       success/failure/timeout）
 *   <li>{@code agent_tool_call_duration_seconds{tool_name}} — 单次工具调用耗时分布
 *   <li>{@code agent_rag_retrieval_total{provider, status}} — RAG 检索次数 （provider:
 *       pgvector/memory/hybrid, status: success/failure/empty）
 *   <li>{@code agent_rag_retrieval_duration_seconds{provider}} — RAG 检索端到端耗时
 *   <li>{@code agent_llm_ttft_seconds{provider, model}} — 流式首 Token 响应耗时 （Time-To-First-Token，秒）
 *   <li>{@code agent_active_conversations} — Gauge：当前活跃对话数（最近 N 分钟）
 *   <li>{@code agent_conversation_messages_total} — 累积对话消息条数
 *   <li>{@code agent_dag_nodes_executed_total{status}} — DAG 节点执行次数（status: success/skipped/failed）
 *   <li>{@code agent_dag_execution_duration_seconds} — DAG 整图编排出时长
 *   <li>{@code agent_human_approval_waiting_total} — Human-in-the-Loop 审批请求次数
 *   <li>{@code agent_human_approval_wait_duration_seconds} — 等待人工审批耗时
 * </ul>
 *
 * <p><b>线程安全：</b>所有计数/计时通过 {@link SentryMetricsAdapter} 统一管理， {@link #activeConversationsRef} 使用
 * {@link AtomicReference} 保证原子更新。
 *
 * <p><b>符合《云顶编码规范》第 27.2.1 节</b>：禁止直接操作 MeterRegistry， 通过 {@link SentryMetricsAdapter} 桥接到 {@code
 * MetricsCollector} 统一入口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConditionalOnClass(MeterRegistry.class)
public class AgentRuntimeMetrics extends SentryMetricsAdapter {

  // -----------------------------------------------------------------------
  // 指标名常量
  // -----------------------------------------------------------------------
  private static final String METRIC_EXECUTION = "execution_total";
  private static final String METRIC_EXECUTION_DURATION = "execution_duration_seconds";
  private static final String METRIC_TOOL_CALLS = "tool_calls_total";
  private static final String METRIC_TOOL_CALL_DURATION = "tool_call_duration_seconds";
  private static final String METRIC_RAG_RETRIEVAL = "rag_retrieval_total";
  private static final String METRIC_RAG_RETRIEVAL_DURATION = "rag_retrieval_duration_seconds";
  private static final String METRIC_LLM_TTFT = "llm_ttft_seconds";
  private static final String METRIC_ACTIVE_CONVERSATIONS = "active_conversations";
  private static final String METRIC_CONVERSATION_MESSAGES = "conversation_messages_total";
  private static final String METRIC_DAG_NODES = "dag_nodes_executed_total";
  private static final String METRIC_DAG_EXECUTION_DURATION = "dag_execution_duration_seconds";
  private static final String METRIC_APPROVAL_WAITING = "human_approval_waiting_total";
  private static final String METRIC_APPROVAL_WAIT_DURATION =
      "human_approval_wait_duration_seconds";

  // -----------------------------------------------------------------------
  // 内部状态
  // -----------------------------------------------------------------------

  /** 当前活跃对话数（最近 N 分钟内有交互的会话）。通过 Gauge 上报。 */
  private final AtomicLong activeConversations = new AtomicLong(0);

  /** Gauge 引用，用于动态更新活跃对话数。 */
  private final AtomicReference<Double> activeConversationsRef = new AtomicReference<>(0.0);

  /** 累积消息计数（进程内原子计数，供可观测面板直接读取；P1 修复：面板 totalMessages 此前硬编码为 0） */
  private final AtomicLong totalMessages = new AtomicLong(0);

  /**
   * 构造 Agent 运行态指标采集器。
   *
   * <p>注册 {@link #METRIC_ACTIVE_CONVERSATIONS} Gauge 到 Micrometer， 后续指标通过调用方显式的 {@code recordXxx}
   * 方法写入。
   */
  public AgentRuntimeMetrics() {
    super("agent_");
    // 注册活跃对话 Gauge（使用 AtomicReference 模式）
    gaugeRef(METRIC_ACTIVE_CONVERSATIONS, activeConversationsRef, AtomicReference::get);
  }

  // -----------------------------------------------------------------------
  // Agent 执行
  // -----------------------------------------------------------------------

  /**
   * 记录一次 Agent 执行结果。
   *
   * @param type Agent 类型标识（如 "simple" / "react" / "plan_execute" / "router" / "rag" / "dag"）
   * @param status 执行状态：success / failure / timeout
   * @param durationMs 端到端耗时（毫秒）
   */
  public void recordExecution(String type, String status, long durationMs) {
    incrementCounter(METRIC_EXECUTION, "type", safe(type), "status", safe(status));
    recordTimer(METRIC_EXECUTION_DURATION, durationMs, "type", safe(type));
  }

  /**
   * 根据布尔结果便捷记录 Agent 执行。
   *
   * @param type Agent 类型
   * @param success 是否成功
   * @param durationMs 耗时（毫秒）
   */
  public void recordExecution(String type, boolean success, long durationMs) {
    recordExecution(type, success ? "success" : "failure", durationMs);
  }

  // -----------------------------------------------------------------------
  // 工具调用
  // -----------------------------------------------------------------------

  /**
   * 记录一次工具调用。
   *
   * @param toolName 工具注册名（如 "wiki_search" / "sql_query"）
   * @param status 调用状态：success / failure / timeout
   * @param durationMs 调用耗时（毫秒）
   */
  public void recordToolCall(String toolName, String status, long durationMs) {
    incrementCounter(METRIC_TOOL_CALLS, "tool_name", safe(toolName), "status", safe(status));
    recordTimer(METRIC_TOOL_CALL_DURATION, durationMs, "tool_name", safe(toolName));
  }

  /**
   * 便捷方法：根据异常判断状态记录工具调用。
   *
   * @param toolName 工具名
   * @param error 异常（null 表示成功）
   * @param durationMs 耗时
   */
  public void recordToolCall(String toolName, Throwable error, long durationMs) {
    recordToolCall(toolName, error == null ? "success" : "failure", durationMs);
  }

  // -----------------------------------------------------------------------
  // RAG 检索
  // -----------------------------------------------------------------------

  /**
   * 记录一次 RAG 检索。
   *
   * @param provider 检索通路：pgvector / memory / hybrid
   * @param status 检索状态：success / failure / empty（success 但召回 0 条）
   * @param durationMs 检索端到端耗时（毫秒）
   */
  public void recordRagRetrieval(String provider, String status, long durationMs) {
    incrementCounter(METRIC_RAG_RETRIEVAL, "provider", safe(provider), "status", safe(status));
    recordTimer(METRIC_RAG_RETRIEVAL_DURATION, durationMs, "provider", safe(provider));
  }

  // -----------------------------------------------------------------------
  // 流式 LLM TTFT
  // -----------------------------------------------------------------------

  /**
   * 记录流式 LLM 首 Token 响应耗时（Time-To-First-Token）。
   *
   * <p>衡量流式场景下用户感知延迟的关键指标，应显著低于完整响应耗时。
   *
     * @param provider Provider 名称
   * @param model 模型名称
   * @param ttftMs 首 Token 耗时（毫秒）
   */
  public void recordTtft(String provider, String model, long ttftMs) {
    recordTimer(METRIC_LLM_TTFT, ttftMs, "provider", safe(provider), "model", safe(model));
  }

  // -----------------------------------------------------------------------
  // 活跃度 Gauge
  // -----------------------------------------------------------------------

  /**
   * 标记一个对话被使用（消息发送），将其设为活跃。
   *
   * <p>实际 Gauge 值受内部保留窗口限制；调用方需在定时器中调用 {@link #reconcileActiveConversations(long)} 做对账。
   */
  public void markConversationActive() {
    activeConversations.incrementAndGet();
    activeConversationsRef.set((double) activeConversations.get());
  }

  /** 递减活跃对话数（会话关闭 / 超期）。 */
  public void markConversationInactive() {
    activeConversations.decrementAndGet();
    activeConversationsRef.set((double) activeConversations.get());
  }

  /**
   * 对账活跃对话 Gauge，避免内部计数漂移。
   *
   * @param realCount 真实的当前活跃对话数（一次查询得到）
   */
  public void reconcileActiveConversations(long realCount) {
    activeConversations.set(Math.max(0, realCount));
    activeConversationsRef.set((double) Math.max(0, realCount));
  }

  /**
   * 直接设置活跃对话数（用于初始化或周期性对账）。
   *
   * @param count 活跃会话数
   */
  public void setActiveConversations(long count) {
    activeConversations.set(Math.max(0, count));
    activeConversationsRef.set((double) Math.max(0, count));
  }

  /**
   * 获取当前活跃对话数（Gauge 实时值）。
   *
   * @return 当前活跃会话数
   */
  public long getActiveConversations() {
    return activeConversations.get();
  }

  // -----------------------------------------------------------------------
  // 会话消息
  // -----------------------------------------------------------------------

  /**
   * 记录一条消息（用户、助手或系统消息）到累积计数器。
   *
   * @param role 消息角色：user / assistant / system / tool
   */
  public void recordMessage(String role) {
    incrementCounter(METRIC_CONVERSATION_MESSAGES, "role", safe(role));
    totalMessages.incrementAndGet();
  }

  /**
   * 获取进程内累积消息总数。
   *
   * <p>供可观测面板展示"总消息数"卡片；进程重启后归零，与 Micrometer 计数器语义一致。
   *
   * @return 累积消息条数
   */
  public long getTotalMessages() {
    return totalMessages.get();
  }

  // -----------------------------------------------------------------------
  // DAG 编排
  // -----------------------------------------------------------------------

  /**
   * 记录一次 DAG 节点的执行结果。
   *
   * @param status 节点执行状态：success / skipped / failed
   */
  public void recordDagNode(String status) {
    incrementCounter(METRIC_DAG_NODES, "status", safe(status));
  }

  /**
   * 记录 DAG 整图编排总耗时。
   *
   * @param durationMs DAG 编排耗时（毫秒）
   */
  public void recordDagExecutionDuration(long durationMs) {
    recordTimer(METRIC_DAG_EXECUTION_DURATION, durationMs);
  }

  // -----------------------------------------------------------------------
  // Human Approval
  // -----------------------------------------------------------------------

  /** 记录一次 Human-in-the-Loop 审批请求被发出（正在等待人工处理）。 */
  public void recordApprovalWaiting() {
    incrementCounter(METRIC_APPROVAL_WAITING);
  }

  /**
   * 记录人工审批的单次等待耗时。
   *
   * <p>在审批结果返回时调用；长时间等待往往意味着审批流程瓶颈。
   *
   * @param durationMs 等待耗时（毫秒）
   */
  public void recordApprovalWaitDuration(long durationMs) {
    recordTimer(METRIC_APPROVAL_WAIT_DURATION, durationMs);
  }
}
