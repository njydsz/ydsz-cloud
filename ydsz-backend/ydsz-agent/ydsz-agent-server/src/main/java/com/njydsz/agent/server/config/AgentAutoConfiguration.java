package com.njydsz.agent.server.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.guardrail.InputGuardrail;
import com.njydsz.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.agent.domain.rag.EmbeddingClient;
import com.njydsz.agent.domain.rag.TextChunker;
import com.njydsz.agent.domain.rag.VectorStore;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.agent.infra.guardrail.PiiMaskingGuardrail;
import com.njydsz.agent.infra.guardrail.PromptInjectionGuardrail;
import com.njydsz.agent.infra.llm.LlmClientRouter;
import com.njydsz.agent.infra.llm.OpenAiCompatibleClient;
import com.njydsz.agent.infra.memory.RedisConversationMemory;
import com.njydsz.agent.infra.rag.InMemoryVectorStore;
import com.njydsz.agent.infra.rag.HybridRetriever;
import com.njydsz.agent.infra.rag.OpenAiEmbeddingClient;
import com.njydsz.agent.infra.rag.PgVectorStore;
import com.njydsz.agent.infra.rag.SimpleTextChunker;
import com.njydsz.agent.infra.tool.DefaultToolRegistry;
import com.njydsz.agent.infra.tool.ToolAnnotationScanner;
import com.njydsz.agent.infra.trace.InMemoryTraceRecorder;
import com.njydsz.agent.server.agent.AgentFactory;
import com.njydsz.agent.server.agent.DagOrchestrationExecutor;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.common.redis.service.RedisService;

import lombok.extern.slf4j.Slf4j;

import io.micrometer.core.instrument.MeterRegistry;

import com.njydsz.agent.server.health.AgentHealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
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
@ConditionalOnProperty(prefix = "ydsz.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient llmClient(AgentProperties properties) {
        LlmClientRouter router = new LlmClientRouter();
        AgentProperties.Llm llmConfig = properties.getLlm();

        // 注册默认 Provider
        OpenAiCompatibleClient defaultClient = new OpenAiCompatibleClient(
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
                OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                        providerName,
                        pc.getBaseUrl(),
                        pc.getApiKey(),
                        llmConfig.getTimeoutSeconds());
                router.register(client);
            }
        }
        return router;
    }

    @Bean
    @ConditionalOnMissingBean(ConversationMemory.class)
    public ConversationMemory conversationMemory(RedisService redisService,
                                                   AgentProperties properties) {
        int maxMessages = properties.getMemory().getMaxMessages();
        int maxListSize = Math.max(maxMessages * 2, 50);
        return new RedisConversationMemory(redisService,
                properties.getMemory().getTtlHours(), maxListSize);
    }

    @Bean
    @ConditionalOnMissingBean(ToolRegistry.class)
    public ToolRegistry toolRegistry() {
        return new DefaultToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(ToolAnnotationScanner.class)
    public ToolAnnotationScanner toolAnnotationScanner(ToolRegistry toolRegistry) {
        return new ToolAnnotationScanner(toolRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(TraceRecorder.class)
    public TraceRecorder traceRecorder() {
        return new InMemoryTraceRecorder();
    }

    @Bean
    @ConditionalOnMissingBean(InputGuardrail.class)
    public InputGuardrail promptInjectionGuardrail() {
        return new PromptInjectionGuardrail();
    }

    @Bean
    @ConditionalOnMissingBean(OutputGuardrail.class)
    public OutputGuardrail piiMaskingGuardrail() {
        return new PiiMaskingGuardrail();
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    public EmbeddingClient embeddingClient(AgentProperties properties) {
        AgentProperties.Rag ragConfig = properties.getRag();
        String apiKey = ragConfig.getEmbeddingApiKey().isEmpty()
                ? properties.getLlm().getApiKey() : ragConfig.getEmbeddingApiKey();
        String baseUrl = ragConfig.getEmbeddingBaseUrl().isEmpty()
                ? properties.getLlm().getBaseUrl() : ragConfig.getEmbeddingBaseUrl();
        return new OpenAiEmbeddingClient(baseUrl, apiKey,
                ragConfig.getEmbeddingModel(), ragConfig.getDimension());
    }

    @Bean
    @ConditionalOnMissingBean(TextChunker.class)
    public TextChunker textChunker(AgentProperties properties) {
        AgentProperties.Rag ragConfig = properties.getRag();
        return new SimpleTextChunker(ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(AgentProperties properties, EmbeddingClient embeddingClient,
                                   JdbcTemplate jdbcTemplate) {
        AgentProperties.Rag ragConfig = properties.getRag();
        if ("pgvector".equalsIgnoreCase(ragConfig.getVectorStore())) {
            PgVectorStore pgStore = new PgVectorStore(jdbcTemplate, embeddingClient);
            if (pgStore.isAvailable()) {
                return pgStore;
            }
        }
        return new InMemoryVectorStore(embeddingClient);
    }

    @Bean
    @ConditionalOnMissingBean(AgentMetrics.class)
    public AgentMetrics agentMetrics(MeterRegistry meterRegistry) {
        return new AgentMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(CostAnalysisService.class)
    public CostAnalysisService costAnalysisService(AgentProperties properties) {
        Map<String, Double> prices = properties.getLlm().getModelPrices();
        return prices != null && !prices.isEmpty()
                ? new CostAnalysisService(prices)
                : new CostAnalysisService();
    }

    @Bean
    @ConditionalOnMissingBean(AgentRequestGuard.class)
    public AgentRequestGuard agentRequestGuard(RedisService redisService) {
        return new AgentRequestGuard(redisService);
    }

    @Bean
    @ConditionalOnMissingBean(GuardrailService.class)
    public GuardrailService guardrailService(List<InputGuardrail> inputGuardrails,
                                             List<OutputGuardrail> outputGuardrails,
                                             AgentMetrics agentMetrics) {
        return new GuardrailService(inputGuardrails, outputGuardrails, agentMetrics);
    }

    @Bean
    @ConditionalOnMissingBean(AgentFactory.class)
    public AgentFactory agentFactory(LlmClient llmClient, ConversationMemory memory,
                                     ToolRegistry toolRegistry, AgentProperties properties,
                                     List<InputGuardrail> inputGuardrails,
                                     List<OutputGuardrail> outputGuardrails,
                                     RagService ragService,
                                     TraceRecorder traceRecorder,
                                     AgentMetrics agentMetrics,
                                     CostAnalysisService costAnalysisService) {
        return new AgentFactory(llmClient, memory, toolRegistry, properties,
                inputGuardrails, outputGuardrails, ragService,
                traceRecorder, agentMetrics, costAnalysisService);
    }

    @Bean
    @ConditionalOnMissingBean(DagOrchestrationExecutor.class)
    public DagOrchestrationExecutor dagOrchestrationExecutor(
            LlmClient llmClient, AgentProperties properties, AgentFactory agentFactory,
            ApplicationContext applicationContext) {
        // P0-3: 强制使用 common-thread 统一线程池（agentDagExecutor, type=VIRTUAL）
        ExecutorService dagExecutor =
                applicationContext.getBean("agentDagExecutor", ExecutorService.class);
        log.info("[Agent] DagOrchestrationExecutor 使用统一线程池 agentDagExecutor");
        return new DagOrchestrationExecutor(llmClient, properties, agentFactory, dagExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(HybridRetriever.class)
    public HybridRetriever hybridRetriever(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        return new HybridRetriever(vectorStore, jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentHealthIndicator agentHealthIndicator(
            LlmClient llmClient, ConversationMemory memory,
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<TraceRecorder> traceRecorderProvider,
            ObjectProvider<CostAnalysisService> costAnalysisServiceProvider,
            ObjectProvider<AgentMetrics> agentMetricsProvider) {
        return new AgentHealthIndicator(
                llmClient, memory, vectorStoreProvider, traceRecorderProvider,
                costAnalysisServiceProvider, agentMetricsProvider);
    }
}
