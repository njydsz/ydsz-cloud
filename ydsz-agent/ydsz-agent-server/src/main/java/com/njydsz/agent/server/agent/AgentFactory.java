package com.njydsz.agent.server.agent;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.rag.RagService;

/**
 * Agent 工厂
 *
 * <p>根据 {@link AgentDefinition} 创建对应的 {@link AgentExecutor} 实现。支持按类型路由到不同的执行器实现。
 *
 * <p>所有执行器统一注入 {@link TraceRecorder}、{@link AgentMetrics}、{@link CostAnalysisService}，
 * 确保执行链路可追踪、指标可采集、成本可核算；输入/输出护栏统一由 {@link GuardrailService} 驱动，执行器不再持有独立护栏列表。
 *
 * <h3>路由规则</h3>
 *
 * <ul>
 *   <li>{@code CHAT} → {@link SimpleAgentExecutor}（单轮对话，无工具）</li>
 *   <li>{@code REACT}/{@code REACT_AGENT}/{@code RAG} → {@link ReActAgentExecutor}（推理-行动循环）</li>
 *   <li>{@code PLAN_EXECUTE}/{@code WORKFLOW} → {@link PlanExecuteAgentExecutor}（先规划后执行）</li>
 *   <li>{@code SUPERVISOR} → {@link SupervisorAgentExecutor}（主管-子 Agent 协同）</li>
 *   <li>{@code DAG} → {@link DagOrchestrationExecutor}（DAG 图编排）</li>
 *   <li>未知类型 → {@link ReActAgentExecutor}（兜底）</li>
 * </ul>
 *
 * <h3>循环依赖处理</h3>
 *
 * <p>{@link DagOrchestrationExecutor} 和 {@link SupervisorAgentExecutor} 内部需要通过本工厂创建子 Agent 执行器，
 * 形成构造器循环依赖。使用 {@link Lazy} 延迟注入打破循环，Spring 会代理目标 Bean 直到首次实际调用时才初始化。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class AgentFactory {

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 对话记忆 */
  private final ConversationMemory memory;

  /** 工具注册中心 */
  private final ToolRegistry toolRegistry;

  /** Agent 配置属性 */
  private final AgentProperties properties;

  /** RAG 服务 */
  private final RagService ragService;

  /** 链路记录器 */
  private final TraceRecorder traceRecorder;

  /** Agent 指标采集 */
  private final AgentMetrics agentMetrics;

  /** 成本分析服务 */
  private final CostAnalysisService costAnalysisService;

  /** 护栏编排服务（统一驱动输入/输出护栏，消除各执行器重复逻辑） */
  private final GuardrailService guardrailService;

  /** Prompt 模板提供者（加载外部化模板） */
  private final PromptTemplateProvider promptTemplateProvider;

  /**
   * DAG 编排执行器（延迟注入打破循环依赖）。
   *
   * <p>DAG 执行器内部需要回调本工厂创建节点子 Agent，形成构造器循环。使用 {@link Lazy} 代理直到首次路由到 DAG 类型时才解析。
   */
  private final DagOrchestrationExecutor dagExecutor;

  /**
   * Supervisor 执行器（延迟注入打破循环依赖）。
   *
   * <p>Supervisor 执行器内部需要回调本工厂创建 Worker Agent，处理方式同 DAG。
   */
  private final SupervisorAgentExecutor supervisorExecutor;

  public AgentFactory(
      LlmClient llmClient,
      ConversationMemory memory,
      ToolRegistry toolRegistry,
      AgentProperties properties,
      RagService ragService,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService,
      PromptTemplateProvider promptTemplateProvider,
      @Lazy DagOrchestrationExecutor dagExecutor,
      @Lazy SupervisorAgentExecutor supervisorExecutor) {
    this.llmClient = llmClient;
    this.memory = memory;
    this.toolRegistry = toolRegistry;
    this.properties = properties;
    this.ragService = ragService;
    this.traceRecorder = traceRecorder;
    this.agentMetrics = agentMetrics;
    this.costAnalysisService = costAnalysisService;
    this.guardrailService = guardrailService;
    this.promptTemplateProvider = promptTemplateProvider;
    this.dagExecutor = dagExecutor;
    this.supervisorExecutor = supervisorExecutor;
  }

  /**
   * 获取 Agent 执行器
   *
   * <p>每次创建新实例（执行器为无状态不可变对象，创建开销极小），避免缓存长期持有依赖引用。
   *
   * @param definition Agent 定义
   * @return 执行器
   */
  public AgentExecutor getExecutor(AgentDefinition definition) {
    return createExecutor(definition.getType().name());
  }

  /**
   * 获取默认 Agent 执行器（ReAct 模式）。
   *
   * @return 默认执行器实例
   */
  public AgentExecutor getDefaultExecutor() {
    return createExecutor("REACT");
  }

  /**
   * 创建指定类型的执行器实例。
   *
   * <p>所有执行器统一传递完整依赖集，确保能力一致。未知类型回退到 ReAct 执行器。
   *
   * @param type Agent 类型字符串
   * @return 对应类型的执行器实例
   */
  private AgentExecutor createExecutor(String type) {
    log.info("[Agent-Factory] 创建执行器: type={}", type);

    return switch (type.toUpperCase()) {
      case "REACT", "REACT_AGENT" ->
          // ReAct 模式：推理-行动循环，支持 Tool Calling
          new ReActAgentExecutor(
              llmClient,
              memory,
              toolRegistry,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService,
              promptTemplateProvider,
              ragService);
      case "RAG" ->
          // RAG 模式：检索增强生成，复用 ReAct 执行器（ragService 不为 null 时自动启用知识增强）
          new ReActAgentExecutor(
              llmClient,
              memory,
              toolRegistry,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService,
              promptTemplateProvider,
              ragService);
      case "CHAT" ->
          // Simple 模式：单轮对话，无工具调用
          new SimpleAgentExecutor(
              llmClient,
              memory,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService,
              promptTemplateProvider);
      case "PLAN_EXECUTE", "WORKFLOW" ->
          // Plan-Execute 模式：先规划后执行，复杂任务分解
          new PlanExecuteAgentExecutor(
              llmClient,
              memory,
              toolRegistry,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService,
              promptTemplateProvider);
      case "SUPERVISOR" ->
          // Supervisor 模式：主管-子 Agent 协同（仅首次创建时初始化，后续走缓存）
          supervisorExecutor;
      case "DAG" ->
          // DAG 模式：图编排执行（仅首次创建时初始化，后续走缓存）
          dagExecutor;
      default -> {
        log.warn("[Agent-Factory] 未知 Agent 类型: {}，回退到 ReAct", type);
        yield new ReActAgentExecutor(
            llmClient,
            memory,
            toolRegistry,
            properties,
            traceRecorder,
            agentMetrics,
            costAnalysisService,
            guardrailService,
            promptTemplateProvider,
            ragService);
      }
    };
  }
}
