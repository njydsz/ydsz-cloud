package com.njydsz.pmis.agent.server.engine.llm;

import com.njydsz.pmis.agent.api.llm.LlmClient;
import com.njydsz.pmis.agent.api.llm.LlmClientConfig;
import com.njydsz.pmis.agent.server.engine.llm.impl.MockLlmClient;
import com.njydsz.pmis.agent.server.engine.llm.impl.OpenAICompatibleLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 客户端自动配置（P0-2 架构优化，2026-07-12 迁移到 agent 模块）。
 *
 * <p>根据配置 {@code pmis.agent.ai.client-type} 自动创建对应的 {@link LlmClient} Bean：
 * <ul>
 *   <li>OPENAI_COMPATIBLE：{@link OpenAICompatibleLlmClient}（生产环境）</li>
 *   <li>MOCK（默认）：{@link MockLlmClient}（开发/测试环境）</li>
 * </ul>
 *
 * <p>各模块（literule、agent、message 等）统一注入 {@link LlmClient} 即可使用，
 * 无需各自维护 LLM 配置和实现。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-2)
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LlmClientConfig.class)
@ConditionalOnProperty(name = "pmis.agent.ai.enabled", havingValue = "true", matchIfMissing = false)
public class LlmClientAutoConfiguration {

    /**
     * OpenAI 兼容协议 LLM 客户端
     */
    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(name = "pmis.agent.ai.client-type", havingValue = "OPENAI_COMPATIBLE")
    public LlmClient openAICompatibleLlmClient(LlmClientConfig config) {
        log.info("[LlmClient] 初始化 OpenAI 兼容客户端: model={} url={}",
                config.getModel(), config.getApiUrl());
        return new OpenAICompatibleLlmClient(config);
    }

    /**
     * Mock LLM 客户端（默认）
     */
    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient mockLlmClient() {
        log.info("[LlmClient] 初始化 Mock 客户端（离线模式）");
        return new MockLlmClient();
    }
}
