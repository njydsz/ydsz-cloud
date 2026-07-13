package com.njydsz.pmis.agent.server.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.agent.domain.conversation.ConversationMemory;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.domain.guardrail.InputGuardrail;
import com.njydsz.pmis.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.pmis.agent.domain.tool.ToolRegistry;
import com.njydsz.pmis.agent.domain.trace.TraceRecorder;
import com.njydsz.pmis.agent.infra.guardrail.PiiMaskingGuardrail;
import com.njydsz.pmis.agent.infra.guardrail.PromptInjectionGuardrail;
import com.njydsz.pmis.agent.infra.llm.LlmClientRouter;
import com.njydsz.pmis.agent.infra.llm.OpenAiCompatibleClient;
import com.njydsz.pmis.agent.infra.memory.RedisConversationMemory;
import com.njydsz.pmis.agent.infra.tool.DefaultToolRegistry;
import com.njydsz.pmis.agent.infra.trace.InMemoryTraceRecorder;
import com.njydsz.pmis.agent.server.agent.AgentFactory;

/**
 * Agent 自动配置
 *
 * <p>当 {@code pmis.agent.enabled=true}（默认）时自动装配以下 Bean：
 * <ul>
 *   <li>{@link OpenAiCompatibleClient} — LLM 客户端实现</li>
 *   <li>{@link LlmClientRouter} — LLM 路由器（暴露为 {@link LlmClient} 接口）</li>
 *   <li>{@link RedisConversationMemory} — Redis 对话记忆</li>
 *   <li>{@link DefaultToolRegistry} — 工具注册中心</li>
 *   <li>{@link InMemoryTraceRecorder} — 执行链路记录器</li>
 *   <li>{@link PromptInjectionGuardrail} — Prompt 注入检测护栏</li>
 *   <li>{@link PiiMaskingGuardrail} — PII 脱敏护栏</li>
 *   <li>{@link AgentFactory} — Agent 工厂</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "pmis.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient llmClient(AgentProperties properties) {
        LlmClientRouter router = new LlmClientRouter();
        AgentProperties.Llm llmConfig = properties.getLlm();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                llmConfig.getDefaultProvider(),
                llmConfig.getBaseUrl(),
                llmConfig.getApiKey(),
                llmConfig.getTimeoutSeconds());
        router.register(client);
        return router;
    }

    @Bean
    @ConditionalOnMissingBean(ConversationMemory.class)
    public ConversationMemory conversationMemory(StringRedisTemplate redisTemplate,
                                                   AgentProperties properties) {
        return new RedisConversationMemory(redisTemplate, properties.getMemory().getTtlHours());
    }

    @Bean
    @ConditionalOnMissingBean(ToolRegistry.class)
    public ToolRegistry toolRegistry() {
        return new DefaultToolRegistry();
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
    @ConditionalOnMissingBean(AgentFactory.class)
    public AgentFactory agentFactory(LlmClient llmClient, ConversationMemory memory,
                                     ToolRegistry toolRegistry, AgentProperties properties,
                                     List<InputGuardrail> inputGuardrails,
                                     List<OutputGuardrail> outputGuardrails) {
        return new AgentFactory(llmClient, memory, toolRegistry, properties,
                inputGuardrails, outputGuardrails);
    }
}
