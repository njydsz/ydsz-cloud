package com.njydsz.pmis.agent.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.agent.domain.conversation.ConversationMemory;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.infra.llm.LlmClientRouter;
import com.njydsz.pmis.agent.infra.llm.OpenAiCompatibleClient;
import com.njydsz.pmis.agent.infra.memory.RedisConversationMemory;

/**
 * Agent 自动配置
 *
 * <p>当 {@code pmis.agent.enabled=true}（默认）时自动装配以下 Bean：
 * <ul>
 *   <li>{@link OpenAiCompatibleClient} — LLM 客户端实现</li>
 *   <li>{@link LlmClientRouter} — LLM 路由器（暴露为 {@link LlmClient} 接口）</li>
 *   <li>{@link RedisConversationMemory} — Redis 对话记忆</li>
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
}
