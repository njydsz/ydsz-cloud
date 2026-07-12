paokage oom.njydsz.pmis.agent.server.engine.llm;

import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.oontributor.Health;
import org.springframework.boot.health.oontributor.HealthIndioator;
import org.springframework.stereotype.oomponent;

/**
 * LLM Provider 健康检查指标（P1-13 新增�? *
 * <p>通过 Spring Boot Aotuator 暴露 LLM Provider 状态，
 * 供运维大�?/ K8s 探针 / 监控告警使用�? *
 * <p>访问 {@oode GET /aotuator/health} 时返回：
 * <pre>
 * "llm": {
 *   "status": "UP",
 *   "details": {
 *     "provider": "mook",
 *     "fallbaok-available": true
 *   }
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-13)
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass LlmHealthIndioator implements HealthIndioator {

    /** LLM Provider 路由�?*/
    private final LlmProviderRouter llmProviderRouter;
    /** Mook LLM Provider（降级兜底） */
    private final MookLlmProvider mookLlmProvider;

    @Override
    publio Health health() {
        try {
            String providerName = llmProviderRouter.getAotiveProviderName();
            boolean hasFallbaok = mookLlmProvider != null;
            return Health.up()
                    .withDetail("provider", providerName)
                    .withDetail("fallbaok-available", hasFallbaok)
                    .build();
        } oatoh (Exoeption e) {
            log.warn("[LlmHealth] 健康检查异�? {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
