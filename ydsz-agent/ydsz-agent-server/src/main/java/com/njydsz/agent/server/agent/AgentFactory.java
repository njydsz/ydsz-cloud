package com.njydsz.agent.server.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
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
 * <p>根据 {@link AgentDefinition} 创建对应的 {@link AgentExecutor} 实现。 支持按类型路由到不同的执行器实现。
 *
 * <p>所有执行器统一注入 {@link TraceRecorder}、{@link AgentMetrics}、{@link CostAnalysisService}，
 * 确保执行链路可追踪、指标可采集、成本可核算；输入/输出护栏统一由 {@link GuardrailService} 驱动，执行器不再持有独立护栏列表（O4 死字段清理）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AgentFactory {

  private static final Logger LOG = LoggerFactory.getLogger(AgentFactory.class);

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

  /** 执行器缓存（key=Agent 类型） */
  private final Map<String, AgentExecutor> executorCache = new ConcurrentHashMap<>();

  public AgentFactory(
      LlmClient llmClient,
      ConversationMemory memory,
      ToolRegistry toolRegistry,
      AgentProperties properties,
      RagService ragService,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService) {
    this.llmClient = llmClient;
    this.memory = memory;
    this.toolRegistry = toolRegistry;
    this.properties = properties;
    this.ragService = ragService;
    this.traceRecorder = traceRecorder;
    this.agentMetrics = agentMetrics;
    this.costAnalysisService = costAnalysisService;
    this.guardrailService = guardrailService;
  }

  /**
   * 获取 Agent 执行器
   *
   * @param definition Agent 定义
   * @return 执行器
   */
  public AgentExecutor getExecutor(AgentDefinition definition) {
    String type = definition.getType().name();
    return executorCache.computeIfAbsent(type, this::createExecutor);
  }

  /** 获取默认 Agent 执行器（ReAct 模式） */
  public AgentExecutor getDefaultExecutor() {
    return executorCache.computeIfAbsent("REACT", this::createExecutor);
  }

  /**
   * 创建指定类型的执行器实例。
   *
   * <p>所有执行器统一传递完整依赖集，确保能力一致。 未知类型回退到 ReAct 执行器。
   *
   * @param type Agent 类型字符串
   * @return 对应类型的执行器实例
   */
  private AgentExecutor createExecutor(String type) {
    LOG.info("[Agent-Factory] 创建执行器: type={}", type);

    return switch (type.toUpperCase()) {
      case "REACT", "REACT_AGENT" ->
          new ReActAgentExecutor(
              llmClient,
              memory,
              toolRegistry,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService);
      case "CHAT" ->
          new SimpleAgentExecutor(
              llmClient,
              memory,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService);
      case "RAG" ->
          new RagAgentExecutor(
              llmClient,
              memory,
              properties,
              ragService,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService);
      case "PLAN_EXECUTE" ->
          new PlanExecuteAgentExecutor(
              llmClient,
              memory,
              toolRegistry,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService);
      case "ROUTER" ->
          new RouterAgentExecutor(
              llmClient,
              memory,
              toolRegistry,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService,
              this);
      case "SUPERVISOR" ->
          new SupervisorAgentExecutor(
              llmClient,
              memory,
              properties,
              traceRecorder,
              agentMetrics,
              costAnalysisService,
              guardrailService,
              this);
      default -> {
        LOG.warn("[Agent-Factory] 未知 Agent 类型: {}，回退到 ReAct", type);
        yield new ReActAgentExecutor(
            llmClient,
            memory,
            toolRegistry,
            properties,
            traceRecorder,
            agentMetrics,
            costAnalysisService,
            guardrailService);
      }
    };
  }
}
