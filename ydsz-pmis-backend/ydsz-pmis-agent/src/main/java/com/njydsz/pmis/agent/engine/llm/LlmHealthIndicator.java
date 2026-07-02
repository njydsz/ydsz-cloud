package com.njydsz.pmis.agent.engine.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * LLM Provider 健康检查指标（P1-13 新增）
 *
 * <p>通过 Spring Boot Actuator 暴露 LLM Provider 状态，
 * 供运维大盘 / K8s 探针 / 监控告警使用。
 *
 * <p>访问 {@code GET /actuator/health} 时返回：
 * <pre>
 * "llm": {
 *   "status": "UP",
 *   "details": {
 *     "provider": "mock",
 *     "fallback-available": true
 *   }
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-13)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmHealthIndicator implements HealthIndicator {

    /** LLM Provider 路由器 */
    private final LlmProviderRouter llmProviderRouter;
    /** Mock LLM Provider（降级兜底） */
    private final MockLlmProvider mockLlmProvider;

    @Override
    public Health health() {
        try {
            String providerName = llmProviderRouter.getActiveProviderName();
            boolean hasFallback = mockLlmProvider != null;
            return Health.up()
                    .withDetail("provider", providerName)
                    .withDetail("fallback-available", hasFallback)
                    .build();
        } catch (Exception e) {
            log.warn("[LlmHealth] 健康检查异常: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
