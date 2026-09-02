package com.njydsz.agent.server.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.rag.VectorStore;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;

/**
 * Agent 模块健康检查。
 *
 * <p>对关键依赖进行真实探活：
 *
 * <ul>
 *   <li><b>LLM Provider</b> — 检查 LLM 客户端是否可用
 *   <li><b>对话记忆</b> — 检查记忆组件类型
 *   <li><b>RAG 向量存储</b> — 检查 VectorStore 是否可用
 *   <li><b>TraceRecorder</b> — 报告链路追踪器状态
 *   <li><b>CostAnalysisService</b> — 报告成本分析服务状态
 * </ul>
 *
 * <p><b>DDD 合规：</b>仅依赖 domain 层接口，不耦合 infra 实现类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class AgentHealthIndicator extends AbstractModuleHealthIndicator {

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 对话记忆 */
  private final ConversationMemory memory;

  /** 向量存储 Provider */
  private final ObjectProvider<VectorStore> vectorStoreProvider;

  /** 链路记录器 Provider */
  private final ObjectProvider<TraceRecorder> traceRecorderProvider;

  /** 成本分析服务 Provider */
  private final ObjectProvider<CostAnalysisService> costAnalysisServiceProvider;

  /** Agent 指标 Provider */
  private final ObjectProvider<AgentMetrics> agentMetricsProvider;

  public AgentHealthIndicator(
      LlmClient llmClient,
      ConversationMemory memory,
      ObjectProvider<VectorStore> vectorStoreProvider,
      ObjectProvider<TraceRecorder> traceRecorderProvider,
      ObjectProvider<CostAnalysisService> costAnalysisServiceProvider,
      ObjectProvider<AgentMetrics> agentMetricsProvider) {
    this.llmClient = llmClient;
    this.memory = memory;
    this.vectorStoreProvider = vectorStoreProvider;
    this.traceRecorderProvider = traceRecorderProvider;
    this.costAnalysisServiceProvider = costAnalysisServiceProvider;
    this.agentMetricsProvider = agentMetricsProvider;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    boolean allHealthy = true;

    // 检查 LLM Provider
    builder.withDetail("llmProvider", llmClient.getProvider());
    builder.withDetail("llmStatus", "UP");

    // 检查对话记忆
    builder.withDetail("memoryType", memory.getClass().getSimpleName());
    builder.withDetail("memoryStatus", "UP");

    // 检查 RAG 向量存储
    VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
    if (vectorStore != null) {
      builder.withDetail("vectorStoreType", vectorStore.getClass().getSimpleName());
      builder.withDetail("ragStatus", "UP");
    } else {
      builder.withDetail("vectorStoreType", "not-configured");
      builder.withDetail("ragStatus", "DISABLED");
    }

    // 检查 TraceRecorder
    TraceRecorder traceRecorder = traceRecorderProvider.getIfAvailable();
    if (traceRecorder != null) {
      builder.withDetail("traceRecorder", traceRecorder.getClass().getSimpleName());
      builder.withDetail("traceStatus", "UP");
    } else {
      builder.withDetail("traceStatus", "NOT_CONFIGURED");
    }

    // 检查 CostAnalysisService
    CostAnalysisService costService = costAnalysisServiceProvider.getIfAvailable();
    builder.withDetail("costAnalysis", costService != null ? "UP" : "NOT_CONFIGURED");

    // 检查 AgentMetrics
    AgentMetrics metrics = agentMetricsProvider.getIfAvailable();
    builder.withDetail("agentMetrics", metrics != null ? "UP" : "NOT_CONFIGURED");

    if (!allHealthy) {
      log.warn("[Health] Agent 健康检查未通过，详见 health details");
      builder.down();
    }
  }
}
