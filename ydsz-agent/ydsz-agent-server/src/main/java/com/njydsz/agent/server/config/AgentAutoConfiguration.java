package com.njydsz.agent.server.config;

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
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
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
import com.njydsz.agent.infra.mapper.AgentTraceMapper;
import com.njydsz.agent.infra.mapper.AgentTraceStepMapper;
import com.njydsz.agent.infra.mapper.TokenUsageRecordMapper;
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
import com.njydsz.agent.infra.tool.Text2SqlTool;
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
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * Agent 模块自动配置。
 *
 * <p>承担 ydsz-agent 微服务的核心 Bean 注册职责，包括 LLM 客户端、对话记忆、工具注册、
 *
 * <p>RAG（向量存储+Embedding）、护栏（输入/输出）、指标采集、Agent 工厂、Token 成本核算、
 *
 * <p>DAG 编排执行器、健康检查等。
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
   * <p>由 {@code JsonAutoConfiguration} 自动发现并纳入 {@code JsonModuleRegistrar}， 使 {@code ChatRequest} /
   * {@code ChatMessage} / {@code ToolCall} / {@code ToolDefinition} / {@code TokenUsage} 在全局 {@code
   * toJson} / {@code toObject} 路径中统一产出 OpenAI 契约形状， 替代此前在 {@link OpenAiCompatibleClient} 中手工拼装 JSON
   * 的做法，避免契约多处维护而漂移。
   *
   * @return JSON 模块实例，无条件注册
   */
  @Bean
  public AgentJsonModule agentJsonModule() {
    // P1-1：注册 Agent 领域模型的 YdszJson 自定义序列化器/反序列化器。
    // 由 JsonAutoConfiguration.JsonConfigBean 自动发现并加入 JsonModuleRegistrar，
    // 使 ChatRequest/ChatMessage/ToolCall/ToolDefinition/TokenUsage 在全局 toJson/toObject
    // 路径中统一产出 OpenAI 契约形状（替代 OpenAiCompatibleClient 手工拼装）。
    return new AgentJsonModule();
  }

  /**
   * 装配 LLM 客户端路由器，作为所有模型调用的统一入口。
   *
   * <p>先以 {@code ydsz.agent.llm} 下的默认配置注册主 Provider（它同时成为 {@link LlmClientRouter} 的兜底
   * defaultClient），再遍历 {@code providers} 注册多模型； 配置中 {@code enabled=false} 的条目会被跳过，便于灰度开关某个 Provider
   * 而无需删配置。 所有 Provider 共用同一份 {@code timeoutSeconds} 超时设置。
   *
   * <p><b>降级</b>：路由器对网络超时、限流、Provider 5xx 自动切换备用 Provider； 认证失败与模型不存在不降级，直接抛出，避免无效重试放大故障。
   *
   * @param properties Agent 配置，需提供 baseUrl / apiKey 等 LLM 接入参数
   * @return 路由器实例；仅在容器中不存在其他 {@link LlmClient} 时生效
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
   * <p>Redis List 的容量取「记忆滑动窗口 2 倍」与「至少 50」的较大值： 多留一倍余量是为了防止窗口边界处的消息被 trim 掉，导致下一轮取不满上下文。 记忆按 {@code
   * ttlHours} 自动过期，超期对话不再计入上下文，也无需额外清理任务。
   *
   * <p><b>降级</b>：Redis 不可用时记忆读写失败，Agent 会退化为无历史上下文的单轮问答。
   *
   * @param stringOps Redis String 操作组件，由 common-redis 提供
   * @param collectionOps Redis 集合操作组件，由 common-redis 提供
   * @param properties Agent 配置，提供 {@code memory.maxMessages} 与 {@code memory.ttlHours}
   * @return 记忆实现；仅在容器中不存在其他 {@link ConversationMemory} 时生效
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
    // Redis 列表容量取「滑动窗口 2 倍」与「至少 50」的较大值，预留余量避免边界被覆盖
    int maxListSize = Math.max(maxMessages * 2, 50);
    RedisConversationMemory redisMemory =
        new RedisConversationMemory(
            stringOps, collectionOps, memoryConfig.getTtlHours(), maxListSize);
    // P1 优化：启用摘要压缩时包装 Redis 记忆，长对话自动压缩为摘要（对标 LangChain SummaryBufferMemory）
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
          memoryConfig.getSummaryKeepRecent());
    }
    return redisMemory;
  }

  /**
   * 装配工具注册中心，集中管理可供 LLM 进行 Function Calling 的工具。
   *
   * <p>默认实现为内存 {@link DefaultToolRegistry}，注册表随进程存活； 多实例部署时各节点各自持有一份，因此工具注册必须在<b>每个节点启动时</b>幂等完成，
   * 不能依赖运行期单点注册。
   *
   * @return 工具注册中心；仅在容器中不存在其他 {@link ToolRegistry} 时生效
   */
  @Bean
  @ConditionalOnMissingBean(ToolRegistry.class)
  public ToolRegistry toolRegistry() {
    return new DefaultToolRegistry();
  }

  /**
   * 装配注解式工具扫描器，把带工具注解的 Bean 方法自动登记到注册中心。
   *
   * <p>免去业务侧手写注册代码；扫描在容器刷新阶段完成，运行期新增的动态代理对象不会被感知。
   *
   * @param toolRegistry 扫描结果写入的目标注册中心
   * @return 扫描器；仅在容器中不存在其他 {@link ToolAnnotationScanner} 时生效
   */
  @Bean
  @ConditionalOnMissingBean(ToolAnnotationScanner.class)
  public ToolAnnotationScanner toolAnnotationScanner(ToolRegistry toolRegistry) {
    return new ToolAnnotationScanner(toolRegistry);
  }

  /**
   * 装配执行链路记录器，用于回放 Agent 的思考-行动步骤。
   *
   * <p>优先使用数据库实现（{@link PgTraceRecorder}），将链路数据持久化到 {@code ydsz_agent_trace} 与 {@code
   * ydsz_agent_trace_step} 表中， 支持跨重启保留、多实例共享与长期审计。
   *
   * <p>仅在 JDBC 数据源不可用或 Mapper 未装配时降级为内存实现。
   *
   * @param traceMapper 链路主表 Mapper
   * @param traceStepMapper 链路步骤表 Mapper
   * @return 链路记录器
   */
  @Bean
  @ConditionalOnMissingBean(TraceRecorder.class)
  public TraceRecorder traceRecorder(
      AgentTraceMapper traceMapper, AgentTraceStepMapper traceStepMapper) {
    if (traceMapper != null && traceStepMapper != null) {
      log.info("[Agent] 使用数据库链路记录器 PgTraceRecorder");
      return new PgTraceRecorder(traceMapper, traceStepMapper);
    }
    log.info("[Agent] 降级使用内存链路记录器 InMemoryTraceRecorder");
    return new InMemoryTraceRecorder();
  }

  /**
   * 装配提示词注入防护（输入侧护栏）。
   *
   * <p>在用户输入进入 Prompt 之前拦截"忽略以上指令"一类的越权诱导，防止系统提示被覆盖。 注意 {@link ConditionalOnMissingBean} 作用于 {@link
   * InputGuardrail} 类型： 一旦业务侧自定义了任意输入护栏，本默认护栏<b>不会再注册</b>，需自行保留注入防护能力。
   *
   * @return 输入护栏实例
   */
  @Bean
  @ConditionalOnMissingBean(InputGuardrail.class)
  public InputGuardrail promptInjectionGuardrail() {
    return new PromptInjectionGuardrail();
  }

  /**
   * 装配 PII 脱敏护栏（输出侧护栏）。
   *
   * <p>在模型回复返回前对手机号、身份证、邮箱等个人敏感信息做掩码，满足合规要求。 同样受 {@link ConditionalOnMissingBean} 类型级约束：业务侧自定义任意
   * {@link OutputGuardrail} 后本护栏不再注册，脱敏责任随之转移给自定义实现。
   *
   * @return 输出护栏实例
   */
  @Bean
  @ConditionalOnMissingBean(OutputGuardrail.class)
  public OutputGuardrail piiMaskingGuardrail() {
    return new PiiMaskingGuardrail();
  }

  /**
   * 装配 Embedding 客户端，为 RAG 提供文本向量化能力。
   *
   * <p>{@code rag.embeddingApiKey} / {@code rag.embeddingBaseUrl} 留空时自动回落到 LLM
   * 的同名配置——多数场景下向量与对话走同一家 Provider，可少配一套凭据； 只有向量服务独立部署时才需要单独指定。
   *
   * <p><b>注意</b>：{@code rag.dimension} 必须与向量库中已有数据的维度一致， 中途改动会导致既有向量无法检索，需要重新灌库。
   *
   * @param properties Agent 配置，提供 RAG 与 LLM 的接入参数
   * @return Embedding 客户端；仅在容器中不存在其他 {@link EmbeddingClient} 时生效
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
   * 装配文本分块器，把长文档切分为可向量化的片段。
   *
   * <p>{@code chunkSize} 决定单块 token 规模，{@code chunkOverlap} 为相邻块的重叠长度——
   * 重叠是为了避免语义在切割点被割裂导致召回缺失；重叠越大召回越稳，但索引体积与 Embedding 调用成本同步上升，需权衡配置。
   *
   * @param properties Agent 配置，提供 {@code rag.chunkSize} 与 {@code rag.chunkOverlap}
   * @return 分块器；仅在容器中不存在其他 {@link TextChunker} 时生效
   */
  @Bean
  @ConditionalOnMissingBean(TextChunker.class)
  public TextChunker textChunker(AgentProperties properties) {
    AgentProperties.Rag ragConfig = properties.getRag();
    return new SimpleTextChunker(ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
  }

  /**
   * 装配向量存储，并在 pgvector 不可用时自动降级为内存实现。
   *
   * <p>仅当 {@code rag.vectorStore=pgvector} <b>且</b> {@link PgVectorStore#isAvailable()}
   * 探测通过（扩展已安装、表结构就绪）时才使用数据库存储；否则一律回落到 {@link InMemoryVectorStore}，保证 RAG 能力在环境未就绪时仍可用于开发联调。
   *
   * <p><b>降级代价</b>：内存实现的向量随进程消失且各实例数据不共享， 生产环境务必确认日志中未出现降级，否则检索结果会因节点而异。
   *
   * @param properties Agent 配置，提供 {@code rag.vectorStore} 选型
   * @param embeddingClient 向量化客户端，两种实现均依赖它生成查询向量
   * @param jdbcTemplate pgvector 模式下使用的数据源模板
   * @return 向量存储实现；仅在容器中不存在其他 {@link VectorStore} 时生效
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
   * <p>通过 {@link SentryMetricsAdapter} 统一管理指标，符合《云顶编码规范》 第 27.2.1 节「禁止直接操作 MeterRegistry」的强制要求。
   *
   * @return 指标组件；仅在容器中不存在其他 {@link AgentMetrics} 时生效
   */
  @Bean
  @ConditionalOnMissingBean(AgentMetrics.class)
  public AgentMetrics agentMetrics() {
    return new AgentMetrics();
  }

  /**
   * 装配 Agent 运行态指标采集组件（P2 增强）。
   *
   * <p>覆盖 Agent 执行、工具调用、RAG 检索、流式 TTFT、会话活跃度、DAG 编排与人工审批 等运行态场景，与基础 {@link AgentMetrics} 互补。指标名统一拼接
   * {@code agent_} 前缀。
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
   * <p>配置了 {@code llm.modelPrices} 时使用自定义单价表（保序，越具体的模型名应排越前， 因为匹配采用子串包含），否则使用内置默认单价。未命中任何配置的模型按兜底单价
   * {@code 0.001 USD/千 Token} 计费，以免未知模型成本被静默算作 0。
   *
   * <p>用量数据持久化到数据库（{@code ydsz_agent_token_usage} 表）， 支持任意时间范围的用量查询，重启不丢失。
   *
   * @param usageRecordMapper Token 用量记录 Mapper
   * @param properties Agent 配置，提供 {@code llm.modelPrices} 单价表
   * @return 成本分析服务；仅在容器中不存在其他 {@link CostAnalysisService} 时生效
   */
  @Bean
  @ConditionalOnMissingBean(CostAnalysisService.class)
  public CostAnalysisService costAnalysisService(
      TokenUsageRecordMapper usageRecordMapper, AgentProperties properties) {
    Map<String, Double> prices = properties.getLlm().getModelPrices();
    return prices != null && !prices.isEmpty()
        ? new CostAnalysisService(usageRecordMapper, prices)
        : new CostAnalysisService(usageRecordMapper);
  }

  /**
   * 装配 Agent 请求准入卫士，负责限流与重复请求拦截。
   *
   * <p>基于 Redis 做跨实例的计数与幂等标记，因此限流阈值对集群整体生效而非单节点。
   *
   * <p><b>降级</b>：Redis 不可用时无法判定配额，为保证可用性会放行请求， 该窗口内限流与幂等保护同时失效，需依赖上游网关兜底。
   *
   * @param stringOps Redis String 操作组件
   * @return 请求卫士；仅在容器中不存在其他 {@link AgentRequestGuard} 时生效
   */
  @Bean
  @ConditionalOnMissingBean(AgentRequestGuard.class)
  public AgentRequestGuard agentRequestGuard(RedisStringOps stringOps) {
    return new AgentRequestGuard(stringOps);
  }

  /**
   * 装配护栏编排服务，统一驱动输入侧与输出侧全部护栏。
   *
   * <p>Spring 按类型注入<b>全部</b> {@link InputGuardrail} / {@link OutputGuardrail} 实现， 执行顺序取决于 Bean
   * 的排序（可用 {@code @Order} 干预）；业务自定义护栏会与默认护栏 一并生效，无需修改本配置。拦截结果通过 {@link AgentMetrics} 上报，便于观测误杀率。
   *
   * @param inputGuardrails 输入护栏集合，容器内无实现时注入空列表
   * @param outputGuardrails 输出护栏集合，容器内无实现时注入空列表
   * @param agentMetrics 指标组件，用于记录护栏命中情况
   * @return 护栏服务；仅在容器中不存在其他 {@link GuardrailService} 时生效
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
   * 装配 MCP 工具适配器，在应用启动时自动发现 MCP Server 的工具并注册到 ToolRegistry。
   *
   * <p>仅当 {@code ydsz.agent.mcp.enabled=true} 且配置了至少一个 Server 时生效。 工具名添加 Server 名称前缀（格式：{@code
   * serverName__toolName}），避免多 Server 命名冲突。
   *
   * @param properties Agent 配置，提供 MCP 开关与 Server 列表
   * @param toolRegistry 目标工具注册中心
   * @return MCP 工具适配器（作为 Spring Bean 存活，可供运行时重新发现）
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.agent.mcp", name = "enabled", havingValue = "true")
  public McpToolAdapter mcpToolAdapter(AgentProperties properties, ToolRegistry toolRegistry) {
    SseMcpClientProvider clientProvider = new SseMcpClientProvider();
    McpToolAdapter adapter = new McpToolAdapter(clientProvider, properties.getMcp());
    // 启动时自动发现并注册 MCP 工具
    adapter
        .discoverAllTools()
        .forEach(
            toolDef ->
                toolRegistry.register(
                    toolDef.getName(),
                    call -> adapter.executeTool(toolDef.getName(), call.getArguments())));
    log.info("[Agent] MCP 工具注册完成, serverCount={}", properties.getMcp().getServers().size());
    return adapter;
  }

  /**
   * 装配 Text2SQL 工具，将自然语言转换为 SQL 查询并执行。
   *
   * <p>仅当 {@code ydsz.agent.text2sql.enabled=true} 时生效。 注册为名为 {@code text2sql_query} 的工具，LLM 可通过
   * Function Calling 调用。
   *
   * @param properties Agent 配置，提供 Text2SQL 开关
   * @param toolRegistry 目标工具注册中心
   * @param jdbcTemplate JDBC 模板，用于执行查询
   * @param llmClient LLM 客户端，用于生成 SQL
   * @return Text2SQL 工具 Bean
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.agent.text2sql", name = "enabled", havingValue = "true")
  public Text2SqlTool text2sqlTool(
      AgentProperties properties,
      ToolRegistry toolRegistry,
      JdbcTemplate jdbcTemplate,
      LlmClient llmClient) {
    Text2SqlTool tool =
        new Text2SqlTool(jdbcTemplate, llmClient, properties.getLlm().getDefaultModel());
    toolRegistry.register("text2sql_query", call -> tool.execute(call.getArguments()));
    log.info("[Agent] Text2SQL 工具已注册: text2sql_query");
    return tool;
  }

  /**
   * 装配 Agent 工厂，聚合 Agent 运行所需的全部依赖并按需创建 Agent 实例。
   *
   * <p>这是本配置类的<b>汇聚点</b>：模型调用、记忆、工具、RAG、护栏、链路追踪、 指标与成本核算在此拼装成完整执行链路。任一上游 Bean 被业务覆盖， 都会透明地反映到工厂创建出的
   * Agent 上。
   *
   * @param llmClient 模型调用入口（默认为带 Fallback 的路由器）
   * @param memory 对话记忆，决定多轮上下文的召回范围
   * @param toolRegistry 工具注册中心，提供 Function Calling 能力
   * @param properties Agent 配置，含默认模型、温度、迭代上限等
   * @param ragService 知识检索服务
   * @param traceRecorder 执行链路记录器，用于调试回放
   * @param agentMetrics 指标采集组件
   * @param costAnalysisService Token 成本核算服务
   * @return Agent 工厂；仅在容器中不存在其他 {@link AgentFactory} 时生效
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
      GuardrailService guardrailService) {
    return new AgentFactory(
        llmClient,
        memory,
        toolRegistry,
        properties,
        ragService,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService);
  }

  /**
   * 装配 DAG 编排执行器，用于多节点 Agent 工作流的并行调度。
   *
   * <p>强制从容器取名为 {@code agentDagExecutor} 的虚拟线程池（由 common-thread 统一托管）， 而非自行 new
   * 线程池——集中管理才能统一监控与优雅停机，也避免各模块各建一套导致线程膨胀。 该 Bean 缺失时容器启动会直接失败，属于<b>强依赖</b>，不做静默降级。
   *
   * <p>编排整图总超时 5 分钟，任一节点失败其下游自动跳过。
   *
   * @param llmClient 节点执行时调用的模型客户端
   * @param properties Agent 配置，提供节点默认模型与采样参数
   * @param agentFactory Agent 工厂，供节点按需创建子 Agent
   * @param applicationContext 用于按名称获取统一线程池
   * @return DAG 执行器；仅在容器中不存在其他 {@link DagOrchestrationExecutor} 时生效
   */
  @Bean
  @ConditionalOnMissingBean(DagOrchestrationExecutor.class)
  public DagOrchestrationExecutor dagOrchestrationExecutor(
      LlmClient llmClient,
      AgentProperties properties,
      AgentFactory agentFactory,
      ApplicationContext applicationContext) {
    // P0-3: 强制使用 common-thread 统一线程池（agentDagExecutor, type=VIRTUAL）
    ExecutorService dagExecutor =
        applicationContext.getBean("agentDagExecutor", ExecutorService.class);
    log.info("[Agent] DagOrchestrationExecutor 使用统一线程池 agentDagExecutor");
    return new DagOrchestrationExecutor(llmClient, properties, agentFactory, dagExecutor);
  }

  /**
   * 装配混合检索器（向量 + 全文，RRF 融合排序 + 可选 Reranker 精排）。
   *
   * <p>构造期会探测全文检索所需的数据表是否存在，探测结果在实例生命周期内缓存： 若启动时表尚未建好，本实例将<b>始终</b>退化为纯向量检索，后续补建表也不会自动恢复，
   * 需重启应用重新探测。
   *
   * @param vectorStore 向量检索通路
   * @param jdbcTemplate 全文检索通路使用的数据源模板
   * @param reranker 重排序器（可选，未配置时使用 IdentityReranker）
   * @return 混合检索器；仅在容器中不存在其他 {@link HybridRetriever} 时生效
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
   * 装配恒等 Reranker 作为默认实现（关闭精排时的兜底）。
   *
   * <p>业务侧提供自定义 {@link Reranker} 实现时，本 Bean 因 {@link ConditionalOnMissingBean} 不再生效，自定义实现会注入到
   * {@link HybridRetriever} 中。
   *
   * @return 恒等 Reranker
   */
  @Bean
  @ConditionalOnMissingBean(Reranker.class)
  public Reranker reranker() {
    return new IdentityReranker();
  }

  /**
   * 装配 Agent 健康检查指示器，接入 Actuator {@code /health} 端点。
   *
   * <p>向量存储、链路记录、成本核算、指标四项以 {@link ObjectProvider} 惰性注入： 它们属于<b>可选能力</b>，缺失时健康检查跳过对应项而非报错，
   * 保证裁剪部署（如关闭 RAG）时应用仍能通过健康探针。 LLM 客户端与对话记忆则为强依赖，直接注入。
   *
   * @param llmClient 模型客户端，用于探测 Provider 连通性
   * @param memory 对话记忆，用于探测 Redis 连通性
   * @param vectorStoreProvider 向量存储（可选）
   * @param traceRecorderProvider 链路记录器（可选）
   * @param costAnalysisServiceProvider 成本核算服务（可选）
   * @param agentMetricsProvider 指标组件（可选）
   * @return 健康指示器；仅在容器中不存在同类型 Bean 时生效
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
