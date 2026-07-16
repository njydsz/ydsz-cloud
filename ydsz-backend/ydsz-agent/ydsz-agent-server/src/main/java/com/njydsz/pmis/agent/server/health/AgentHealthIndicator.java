package com.njydsz.agent.server.health;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.infra.llm.LlmClientRouter;
import com.njydsz.agent.infra.memory.RedisConversationMemory;

/**
 * Agent 模块健康检查
 *
 * <p>对关键依赖进行真实探活：
 * <ul>
 *   <li><b>LLM Provider</b> — 检查路由器是否注册了至少一个 Provider</li>
 *   <li><b>Redis 记忆</b> — 检查 Redis 连接是否可用（仅对 {@link RedisConversationMemory}）</li>
 * </ul>
 * <p>任一关键依赖不可用则报告 DOWN，K8s readinessProbe 据此决定是否导入流量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class AgentHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AgentHealthIndicator.class);

    private final LlmClient llmClient;
    private final ConversationMemory memory;

    public AgentHealthIndicator(LlmClient llmClient, ConversationMemory memory) {
        this.llmClient = llmClient;
        this.memory = memory;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean allHealthy = true;

        // 检查 LLM Provider
        if (llmClient instanceof LlmClientRouter router) {
            List<String> providers = router.getAvailableProviders();
            builder.withDetail("llmProviders", providers);
            builder.withDetail("llmProviderCount", providers.size());
            if (providers.isEmpty()) {
                builder.withDetail("llmStatus", "NO_PROVIDER");
                allHealthy = false;
            } else {
                builder.withDetail("llmStatus", "UP");
            }
        } else {
            builder.withDetail("llmProvider", llmClient.getProvider());
            builder.withDetail("llmStatus", "UP");
        }

        // 检查 Redis 记忆连接
        if (memory instanceof RedisConversationMemory redisMemory) {
            boolean redisAvailable = redisMemory.isAvailable();
            builder.withDetail("memoryType", "redis");
            builder.withDetail("redisAvailable", redisAvailable);
            if (!redisAvailable) {
                builder.withDetail("memoryStatus", "REDIS_UNREACHABLE");
                allHealthy = false;
            } else {
                builder.withDetail("memoryStatus", "UP");
            }
        } else {
            builder.withDetail("memoryType", memory.getClass().getSimpleName());
            builder.withDetail("memoryStatus", "UP");
        }

        if (!allHealthy) {
            log.warn("[Health] Agent 健康检查未通过，详见 health details");
            return builder.down().build();
        }
        return builder.build();
    }
}
