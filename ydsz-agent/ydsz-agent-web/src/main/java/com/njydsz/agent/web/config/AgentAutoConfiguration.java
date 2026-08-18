package com.njydsz.agent.web.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.guardrail.InputGuardrail;
import com.njydsz.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.agent.domain.json.AgentJsonModule;
import com.njydsz.agent.domain.rag.EmbeddingClient;
import com.njydsz.agent.domain.rag.Reranker;
import com.njydsz.agent.domain.rag.TextChunker;
import com.njydsz.agent.domain.rag.VectorStore;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.infra.guardrail.PiiMaskingGuardrail;
import com.njydsz.agent.infra.guardrail.PromptInjectionGuardrail;
import com.njydsz.agent.infra.llm.CachedLlmClient;
import com.njydsz.agent.infra.llm.LlmClientRouter;
import com.njydsz.agent.infra.llm.OpenAiCompatibleClient;
import com.njydsz.agent.infra.llm.SemanticLlmCache;
import com.njydsz.agent.domain.repository.AgentTraceRepository;
import com.njydsz.agent.domain.repository.AgentTraceStepRepository;
import com.njydsz.agent.domain.repository.TokenUsageRecordRepository;
import com.njydsz.agent.infra.memory.RedisConversationMemory;
import com.njydsz.agent.infra.memory.SummaryConversationMemory;
import com.njydsz.agent.infra.rag.HybridRetriever;
import com.njydsz.agent.infra.rag.IdentityReranker;
import com.njydsz.agent.infra.rag.InMemoryVectorStore;
import com.njydsz.agent.infra.rag.OpenAiEmbeddingClient;
import com.njydsz.agent.infra.rag.PgVectorStore;
import com.njydsz.agent.infra.rag.SimpleTextChunker;
import com.njydsz.agent.infra.tool.DefaultToolRegistry;
import com.njydsz.agent.infra.tool.McpToolAdapter;
import com.njydsz.agent.infra.tool.SseMcpClientProvider;
import com.njydsz.agent.infra.tool.ToolAnnotationScanner;
import com.njydsz.agent.infra.trace.InMemoryTraceRecorder;
import com.njydsz.agent.infra.trace.PgTraceRecorder;
import com.njydsz.agent.server.agent.AgentFactory;
import com.njydsz.agent.server.agent.DagOrchestrationExecutor;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.health.AgentHealthIndicator;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.metrics.AgentRuntimeMetrics;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * Agent 模块自动配置（DDD 分层：由 web 层负责依赖注入编排）。
 *
 * <p>承担 ydsz-agent 微服务的核心 Bean 注册职责，包括 LLM 客户端、对话记忆、工具注册、
 * RAG（向量存储+Embedding）、护栏（输入/输出）、指标采集、Agent 工厂、Token 成本核算、
 * DAG 编排执行器、健康检查等。
 *
 * <p><b>DDD 合规说明：</b>原位于 server 层的配置类依赖大量 infra 实现类，违反「server 层不依赖 infra 层」原则。
 * 现移至 web 层（依赖注入编排层），server 层仅依赖 domain 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.agent",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Slf4j
public class AgentAutoConfiguration {

  /**
   * 注册 Agent 领域模型的 JSON 序列化模块。
   *
   * @return JSON 模块实例，无条件注册
   */
  @Bean
  public AgentJsonModule agentJsonModule() {
    return new AgentJsonModule();
  }

  /**
   * 装配 LLM 客户端路由器，作为所有模型调用的统一入口。
   *
   * @param properties Agent 配置
   * @return 路由器实例
   */
  @Bean
  @ConditionalOnMissingBean(LlmClient.class)
  public LlmClient llmClient(
      AgentProperties properties,
      ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate>
          redisTemplateProvider) {
    LlmClientRouter router = new LlmClientRouter();
    AgentProperties.Llm llmConfig = properties.getLlm();

    // 注册默认 Provider
    OpenAiCompatibleClient defaultClient =
        new OpenAiCompatibleClient(
            llmConfig.getDefaultProvider(),
            llmConfig.getBaseUrl(),
            llmConfig.getApiKey(),
            llmConfig.getTimeoutSeconds());
    router.register(defaultClient);

    // 注册额外 Provider（多模型 + Fallback 链）
    if (llmConfig.getProviders() != null) {
      for (var entry : llmConfig.getProviders().entrySet()) {
        AgentProperties.ProviderConfig pc = entry.getValue();
        if (!pc.isEnabled()) {
          continue;
        }
        String providerName = pc.getName() != null ? pc.getName() : entry.getKey();
        OpenAiCompatibleClient client =
            new OpenAiCompatibleClient(
                providerName, pc.getBaseUrl(), pc.getApiKey(), llmConfig.getTimeoutSeconds());
        router.register(client);
      }
    }

    // 启用语义缓存时包装路由器
    if (properties.getCache().isEnabled()) {
      org.springframework.data.redis.core.StringRedisTemplate redisTemplate =
          redisTemplateProvider.getIfAvailable();
      if (redisTemplate != null) {
        SemanticLlmCache cache =
            new SemanticLlmCache(
                redisTemplate,
                java.time.Duration.ofMinutes(properties.getCache().getTtlMinutes()),
                properties.getCache().getMaxSize());
        log.info(
            "[Agent] LLM 语义缓存已启用, ttl={}min, maxSize={}",
            properties.getCache().getTtlMinutes(),
            properties.getCache().getMaxSize());
        return new CachedLlmClient(router, cache);
      }
      log.warn("[Agent] 语义缓存配置为开启但 RedisTemplate 不可用，跳过缓存");
    }
    return router;
  }

  /**
   * 装配基于 Redis 的对话记忆存储。
   *
   * @param stringOps Redis String 操作组件
   * @param collectionOps Redis 集合操作组件
   * @param properties Agent 配置
   * @param llmClient LLM 客户端
   * @return 记忆实现
   */
  @Bean
  @ConditionalOnMissingBean(ConversationMemory.class)
  public ConversationMemory conversationMemory(
      RedisStringOps stringOps,
      RedisCollectionOps collectionOps,
      AgentProperties properties,
      LlmClient llmClient) {
    AgentProperties.Memory memoryConfig = properties.getMemory();
    int maxMessages = memoryConfig.getMaxMessages();
    int maxListSize = Math.max(maxMessages * 2, 50);
    RedisConversationMemory redisMemory =
        new RedisConversationMemory(
            stringOps, collectionOps, memoryConfig.getTtlHours(), maxListSize);
    if (memoryConfig.isSummaryEnabled()) {
      log.info(
          "[Agent] 启用摘要压缩记忆: threshold={}, keepRecent={}",
          memoryConfig.getSummaryThreshold(),
          memoryConfig.getSummaryKeepRecent());
      return new SummaryConversationMemory(
          redisMemory,
          llmClient,
          properties.getLlm().getDefaultModel(),
          memoryConfig.getSummaryThreshold(),
          memoryConfig.getSummaryKeepRecent(),
          stringOps);
    }
    return redisMemory;
  }

  /**
   * 装配工具注册中心。
   *
   * <p>从配置中读取工具执行超时，注入到注册中心用于超时控制。
   *
   * @param properties Agent 配置，提供工具超时参数
   * @return 工具注册中心
   */
  @Bean
  @ConditionalOnMissingBean(ToolRegistry.class)
  public ToolRegistry toolRegistry(AgentProperties properties) {
    int timeout = properties.getTool().getTimeoutSeconds();
    return new DefaultToolRegistry(timeout);
  }

  /**
   * 装配注解式工具扫描器。
   *
   * @param toolRegistry 扫描结果写入的目标注册中心
   * @return 扫描器
   */
  @Bean
  @ConditionalOnMissingBean(ToolAnnotationScanner.class)
  public ToolAnnotationScanner toolAnnotationScanner(ToolRegistry toolRegistry) {
    return new ToolAnnotationScanner(toolRegistry);
  }

  /**
   * 装配执行链路记录器。
   *
   * @param traceRepository 链路主表 Repository
   * @param traceStepRepository 链路步骤表 Repository
   * @return 链路记录器
   */
  @Bean
  @ConditionalOnMissingBean(TraceRecorder.class)
  public TraceRecorder traceRecorder(
      AgentTraceRepository traceRepository, AgentTraceStepRepository traceStepRepository) {
    if (traceRepository != null && traceStepRepository != null) {
      log.info("[Agent] 使用数据库链路记录器 PgTraceRecorder");
      return traceRepository.createTraceRecorder(traceStepRepository);
    }
    log.info("[Agent] 降级使用内存链路记录器 InMemoryTraceRecorder");
    return new InMemoryTraceRecorder();
  }

  /**
   * 装配提示词注入防护（输入侧护栏）。
   *
   * @return 输入护栏实例
   */
  @Bean
  @ConditionalOnMissingBean(InputGuardrail.class)
  @ConditionalOnProperty(prefix = "ydsz.agent.guardrail", name = "promptInjectionEnabled", havingValue = "true")
  public InputGuardrail promptInjectionGuardrail() {
    return new PromptInjectionGuardrail();
  }

  /**
   * 装配 PII 脱敏护栏（输出侧护栏）。
   *
   * @return 输出护栏实例
   */
  @Bean
  @ConditionalOnMissingBean(OutputGuardrail.class)
  public OutputGuardrail piiMaskingGuardrail() {
    return new PiiMaskingGuardrail();
  }

  /**
   * 装配 Embedding 客户端。
   *
   * @param properties Agent 配置
   * @return Embedding 客户端
   */
  @Bean
  @ConditionalOnMissingBean(EmbeddingClient.class)
  public EmbeddingClient embeddingClient(AgentProperties properties) {
    AgentProperties.Rag ragConfig = properties.getRag();
    String apiKey =
        ragConfig.getEmbeddingApiKey().isEmpty()
            ? properties.getLlm().getApiKey()
            : ragConfig.getEmbeddingApiKey();
    String baseUrl =
        ragConfig.getEmbeddingBaseUrl().isEmpty()
            ? properties.getLlm().getBaseUrl()
            : ragConfig.getEmbeddingBaseUrl();
    return new OpenAiEmbeddingClient(
        baseUrl, apiKey, ragConfig.getEmbeddingModel(), ragConfig.getDimension());
  }

  /**
   * 装配文本分块器。
   *
   * @param properties Agent 配置
   * @return 分块器
   */
  @Bean
  @ConditionalOnMissingBean(TextChunker.class)
  public TextChunker textChunker(AgentProperties properties) {
    AgentProperties.Rag ragConfig = properties.getRag();
    return new SimpleTextChunker(ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
  }

  /**
   * 装配向量存储。
   *
   * @param properties Agent 配置
   * @param embeddingClient 向量化客户端
   * @param jdbcTemplate 数据源模板
   * @return 向量存储实现
   */
  @Bean
  @ConditionalOnMissingBean(VectorStore.class)
  public VectorStore vectorStore(
      AgentProperties properties, EmbeddingClient embeddingClient, JdbcTemplate jdbcTemplate) {
    AgentProperties.Rag ragConfig = properties.getRag();
    if ("pgvector".equalsIgnoreCase(ragConfig.getVectorStore())) {
      PgVectorStore pgStore =
          new PgVectorStore(jdbcTemplate, embeddingClient, ragConfig.isTenantIsolation());
      if (pgStore.isAvailable()) {
        return pgStore;
      }
    }
    return new InMemoryVectorStore(embeddingClient, ragConfig.isTenantIsolation());
  }

  /**
   * 装配 Agent 指标采集组件。
   *
   * @return 指标组件
   */
  @Bean
  @ConditionalOnMissingBean(AgentMetrics.class)
  public AgentMetrics agentMetrics() {
    return new AgentMetrics();
  }

  /**
   * 装配 Agent 运行态指标采集组件。
   *
   * @return 运行态指标组件
   */
  @Bean
  @ConditionalOnMissingBean(AgentRuntimeMetrics.class)
  public AgentRuntimeMetrics agentRuntimeMetrics() {
    return new AgentRuntimeMetrics();
  }

  /**
   * 装配 Token 成本核算服务。
   *
   * @param tokenUsageRecordRepository Token 用量记录 Repository
   * @param properties Agent 配置
   * @return 成本分析服务
   */
  @Bean
  @ConditionalOnMissingBean(CostAnalysisService.class)
  public CostAnalysisService costAnalysisService(
      TokenUsageRecordRepository tokenUsageRecordRepository, AgentProperties properties) {
    Map<String, Double> prices = properties.getLlm().getModelPrices();
    return prices != null && !prices.isEmpty()
        ? new CostAnalysisService(tokenUsageRecordRepository, prices)
        : new CostAnalysisService(tokenUsageRecordRepository);
  }

  /**
   * 装配 Agent 请求准入卫士。
   *
   * @param stringOps Redis String 操作组件
   * @return 请求卫士
   */
  @Bean
  @ConditionalOnMissingBean(AgentRequestGuard.class)
  public AgentRequestGuard agentRequestGuard(RedisStringOps stringOps) {
    return new AgentRequestGuard(stringOps);
  }

  /**
   * 装配护栏编排服务。
   *
   * @param inputGuardrails 输入护栏集合
   * @param outputGuardrails 输出护栏集合
   * @param agentMetrics 指标组件
   * @return 护栏服务
   */
  @Bean
  @ConditionalOnMissingBean(GuardrailService.class)
  public GuardrailService guardrailService(
      List<InputGuardrail> inputGuardrails,
      List<OutputGuardrail> outputGuardrails,
      AgentMetrics agentMetrics) {
    return new GuardrailService(inputGuardrails, outputGuardrails, agentMetrics);
  }

  /**
   * 装配 MCP 工具适配器。
   *
   * @param properties Agent 配置
   * @param toolRegistry 目标工具注册中心
   * @return MCP 工具适配器
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.agent.mcp", name = "enabled", havingValue = "true")
  public McpToolAdapter mcpToolAdapter(AgentProperties properties, ToolRegistry toolRegistry) {
    SseMcpClientProvider clientProvider = new SseMcpClientProvider();
    McpToolAdapter adapter = new McpToolAdapter(clientProvider, properties.getMcp());
    adapter
        .discoverAllTools()
        .forEach(
            toolDef ->
                toolRegistry.register(
                    toolDef.getName(),
                    call -> adapter.executeTool(toolDef.getName(), YdszJson.toJson(call))));
    log.info("[Agent] MCP 工具注册完成, serverCount={}", properties.getMcp().getServers().size());
    return adapter;
  }

  /**
   * 装配 Agent 工厂。
   *
   * @param llmClient 模型调用入口
   * @param memory 对话记忆
   * @param toolRegistry 工具注册中心
   * @param properties Agent 配置
   * @param ragService 知识检索服务
   * @param traceRecorder 执行链路记录器
   * @param agentMetrics 指标采集组件
   * @param costAnalysisService Token 成本核算服务
   * @param guardrailService 护栏编排服务
   * @param promptTemplateProvider Prompt 模板提供者
   * @param dagExecutor DAG 编排执行器
   * @param supervisorExecutor Supervisor 执行器
   * @return Agent 工厂
   */
  @Bean
  @ConditionalOnMissingBean(AgentFactory.class)
  public AgentFactory agentFactory(
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
      @Lazy com.njydsz.agent.server.agent.SupervisorAgentExecutor supervisorExecutor) {
    return new AgentFactory(
        llmClient,
        memory,
        toolRegistry,
        properties,
        ragService,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService,
        promptTemplateProvider,
        dagExecutor,
        supervisorExecutor);
  }

  /**
   * 装配 DAG 编排执行器。
   *
   * @param llmClient 节点执行时调用的模型客户端
   * @param properties Agent 配置
   * @param agentFactory Agent 工厂
   * @param applicationContext 用于按名称获取统一线程池
   * @return DAG 执行器
   */
  @Bean
  @ConditionalOnMissingBean(DagOrchestrationExecutor.class)
  public DagOrchestrationExecutor dagOrchestrationExecutor(
      LlmClient llmClient,
      AgentProperties properties,
      AgentFactory agentFactory,
      ApplicationContext applicationContext) {
    ExecutorService dagExecutor =
        applicationContext.getBean("agentDagExecutor", ExecutorService.class);
    log.info("[Agent] DagOrchestrationExecutor 使用统一线程池 agentDagExecutor");
    return new DagOrchestrationExecutor(llmClient, properties, agentFactory, dagExecutor);
  }

  /**
   * 装配混合检索器（向量 + 全文，RRF 融合排序 + 可选 Reranker 精排）。
   *
   * @param properties Agent 配置
   * @param vectorStore 向量检索通路
   * @param jdbcTemplate 全文检索通路使用的数据源模板
   * @param rerankerProvider 重排序器
   * @return 混合检索器
   */
  @Bean
  @ConditionalOnMissingBean(HybridRetriever.class)
  public HybridRetriever hybridRetriever(
      AgentProperties properties,
      VectorStore vectorStore,
      JdbcTemplate jdbcTemplate,
      ObjectProvider<Reranker> rerankerProvider) {
    Reranker reranker = rerankerProvider.getIfAvailable();
    return new HybridRetriever(
        vectorStore, jdbcTemplate, reranker, properties.getRag().isTenantIsolation());
  }

  /**
   * 装配恒等 Reranker 作为默认实现。
   *
   * @return 恒等 Reranker
   */
  @Bean
  @ConditionalOnMissingBean(Reranker.class)
  public Reranker reranker() {
    return new IdentityReranker();
  }

  /**
   * 装配 Agent 健康检查指示器。
   *
   * @param llmClient 模型客户端
   * @param memory 对话记忆
   * @param vectorStoreProvider 向量存储
   * @param traceRecorderProvider 链路记录器
   * @param costAnalysisServiceProvider 成本核算服务
   * @param agentMetricsProvider 指标组件
   * @return 健康指示器
   */
  @Bean
  @ConditionalOnMissingBean
  public AgentHealthIndicator agentHealthIndicator(
      LlmClient llmClient,
      ConversationMemory memory,
      ObjectProvider<VectorStore> vectorStoreProvider,
      ObjectProvider<TraceRecorder> traceRecorderProvider,
      ObjectProvider<CostAnalysisService> costAnalysisServiceProvider,
      ObjectProvider<AgentMetrics> agentMetricsProvider) {
    return new AgentHealthIndicator(
        llmClient,
        memory,
        vectorStoreProvider,
        traceRecorderProvider,
        costAnalysisServiceProvider,
        agentMetricsProvider);
  }
}
