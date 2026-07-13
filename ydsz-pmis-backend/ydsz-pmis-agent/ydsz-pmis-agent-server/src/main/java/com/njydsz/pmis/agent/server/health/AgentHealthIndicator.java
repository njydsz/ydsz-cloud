package com.njydsz.pmis.agent.server.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.agent.domain.conversation.ConversationMemory;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;

/**
 * Agent 模块健康检查
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class AgentHealthIndicator implements HealthIndicator {

    private final LlmClient llmClient;
    private final ConversationMemory memory;

    public AgentHealthIndicator(LlmClient llmClient, ConversationMemory memory) {
        this.llmClient = llmClient;
        this.memory = memory;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        builder.withDetail("llmProvider", llmClient.getProvider());
        builder.withDetail("llmAvailable", llmClient.supports("gpt-4o-mini"));
        builder.withDetail("memoryType", memory.getClass().getSimpleName());
        return builder.build();
    }
}
