package com.njydsz.agent.server.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import com.njydsz.agent.infra.guardrail.PiiMaskingGuardrail;
import com.njydsz.agent.infra.guardrail.PromptInjectionGuardrail;
import com.njydsz.agent.infra.llm.LlmClientRouter;
import com.njydsz.agent.infra.llm.OpenAiCompatibleClient;
import com.njydsz.agent.infra.memory.RedisConversationMemory;
import com.njydsz.agent.infra.rag.InMemoryVectorStore;
import com.njydsz.agent.infra.rag.OpenAiEmbeddingClient;
import com.njydsz.agent.infra.rag.PgVectorStore;
import com.njydsz.agent.infra.rag.SimpleTextChunker;
import com.njydsz.agent.infra.tool.DefaultToolRegistry;
import com.njydsz.agent.infra.tool.ToolAnnotationScanner;
import com.njydsz.agent.infra.trace.InMemoryTraceRecorder;
import com.njydsz.agent.server.agent.AgentFactory;
import com.njydsz.agent.server.agent.DagOrchestrationExecutor;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.rag.RagService;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Agent 自动配置
 *
 * <p>当 {@code ydsz.agent.enabled=true}（默认）时自动装配以下 Bean：
 * <ul>
 *   <li>{@link OpenAiCompatibleClient} — LLM 客户端实现</li>
 *   <li>{@link LlmClientRouter} — LLM 路由器（暴露为 {@link LlmClient} 接口）</li>
 *   <li>{@link RedisConversationMemory} — Redis 对话记忆</li>
 *   <li>{@link DefaultToolRegistry} — 工具注册中心</li>
 *   <li>{@link InMemoryTraceRecorder} — 执行链路记录器</li>
 *   <li>{@link PromptInjectionGuardrail} — Prompt 注入检测护栏</li>
 *   <li>{@link PiiMaskingGuardrail} — PII 脱敏护栏</li>
 *   <li>{@link OpenAiEmbeddingClient} — Embedding 客户端</li>
 *   <li>{@link SimpleTextChunker} — 文本分块器</li>
 *   <li>{@link PgVectorStore} / {@link InMemoryVectorStore} — 向量存储</li>
 *   <li>{@link AgentMetrics} — Micrometer 指标采集</li>
 *   <li>{@link CostAnalysisService} — Token 用量成本核算</li>
 *   <li>{@link AgentFactory} — Agent 工厂</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "ydsz.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    public ConversationMemory conversationMemory(StringRedisTemplate redisTemplate,
                                                   AgentProperties properties) {
        int maxMessages = properties.getMemory().getMaxMessages();
        int maxListSize = Math.max(maxMessages * 2, 50);
        return new RedisConversationMemory(redisTemplate,
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
    public CostAnalysisService costAnalysisService() {
        return new CostAnalysisService();
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
            LlmClient llmClient, AgentProperties properties, AgentFactory agentFactory) {
        return new DagOrchestrationExecutor(llmClient, properties, agentFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public com.njydsz.agent.server.health.AgentHealthIndicator agentHealthIndicator(
            LlmClient llmClient, ConversationMemory memory,
            org.springframework.beans.factory.ObjectProvider<VectorStore> vectorStoreProvider,
            org.springframework.beans.factory.ObjectProvider<TraceRecorder> traceRecorderProvider,
            org.springframework.beans.factory.ObjectProvider<CostAnalysisService> costAnalysisServiceProvider,
            org.springframework.beans.factory.ObjectProvider<AgentMetrics> agentMetricsProvider) {
        return new com.njydsz.agent.server.health.AgentHealthIndicator(
                llmClient, memory, vectorStoreProvider, traceRecorderProvider,
                costAnalysisServiceProvider, agentMetricsProvider);
    }
}
