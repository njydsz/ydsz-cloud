package com.njydsz.literule.server.health;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.literule.api.Rule;
import com.njydsz.literule.server.cep.CEPEngine;
import com.njydsz.literule.server.core.AsyncTraceRecorder;
import com.njydsz.literule.server.core.DefaultRuleEngine;
import com.njydsz.literule.server.core.RuleCircuitBreaker;
import com.njydsz.literule.server.core.RuleIndexer;
import com.njydsz.literule.server.core.RuleMetrics;

/**
 * 规则引擎健康检查指标。
 *
 * <p>报告规则引擎核心组件的运行状态，包括：
 * <ul>
 *   <li>规则引擎状态（已注册规则数/索引模式/统计状态）</li>
 *   <li>熔断器状态（OPEN 规则数/HALF_OPEN 规则数）</li>
 *   <li>异步 Trace 队列大小</li>
 *   <li>CEP 引擎状态（模式数/命中数）</li>
 *   <li>监控指标摘要（总评估次数/触发次数/异常次数）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LiteRuleHealthIndicator extends AbstractModuleHealthIndicator {

    private final DefaultRuleEngine ruleEngine;
    private final CEPEngine cepEngine;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        // 规则引擎核心状态
        try {
            Map<String, Object> engineDetails = new LinkedHashMap<>();
            engineDetails.put("registeredRules", ruleEngine.getRules().size());
            engineDetails.put("statsEnabled", ruleEngine.isStatsEnabled());
            engineDetails.put("canaryEnabled", ruleEngine.isCanaryEnabled());

            RuleMetrics metrics = ruleEngine.getMetrics();
            if (metrics != null) {
                engineDetails.put("totalEvaluations", metrics.getTotalEvaluations());
                engineDetails.put("totalTriggered", metrics.getTotalTriggered());
                engineDetails.put("totalErrors", metrics.getTotalErrors());
                engineDetails.put("registeredRulesGauge", metrics.getRegisteredRules());
            }

            RuleCircuitBreaker breaker = ruleEngine.getCircuitBreaker();
            engineDetails.put("circuitBreaker", breaker != null ? "enabled" : "disabled");

            builder.withDetail("engine", engineDetails);
        } catch (Exception e) {
            builder.withDetail("engine", "DOWN - " + extractMessage(e));
        }

        // 熔断器状态
        try {
            RuleCircuitBreaker breaker = ruleEngine.getCircuitBreaker();
            if (breaker != null) {
                Map<String, Object> breakerDetails = new LinkedHashMap<>();
                int openCount = 0;
                int halfOpenCount = 0;
                for (Rule rule : ruleEngine.getRules()) {
                    RuleCircuitBreaker.State state = breaker.getState(rule.getCode());
                    if (state == RuleCircuitBreaker.State.OPEN) {
                        openCount++;
                    } else if (state == RuleCircuitBreaker.State.HALF_OPEN) {
                        halfOpenCount++;
                    }
                }
                breakerDetails.put("openRules", openCount);
                breakerDetails.put("halfOpenRules", halfOpenCount);
                builder.withDetail("circuitBreaker", breakerDetails);
            }
        } catch (Exception e) {
            builder.withDetail("circuitBreaker", "ERROR - " + extractMessage(e));
        }

        // 异步 Trace 队列
        try {
            if (ruleEngine.getTraceRecorder() instanceof AsyncTraceRecorder asyncRecorder) {
                Map<String, Object> traceDetails = new LinkedHashMap<>();
                traceDetails.put("queueSize", asyncRecorder.getQueueSize());
                traceDetails.put("queueCapacity", asyncRecorder.getQueueCapacity());
                traceDetails.put("running", asyncRecorder.isRunning());
                builder.withDetail("traceRecorder", traceDetails);
            } else if (ruleEngine.getTraceRecorder() != null) {
                builder.withDetail("traceRecorder", "custom");
            } else {
                builder.withDetail("traceRecorder", "disabled");
            }
        } catch (Exception e) {
            builder.withDetail("traceRecorder", "ERROR - " + extractMessage(e));
        }

        // CEP 引擎状态
        if (cepEngine != null) {
            try {
                Map<String, Object> cepDetails = new LinkedHashMap<>();
                cepDetails.put("patterns", cepEngine.patternCount());
                cepDetails.put("totalHits", cepEngine.totalHits());
                builder.withDetail("cep", cepDetails);
            } catch (Exception e) {
                builder.withDetail("cep", "DOWN - " + extractMessage(e));
            }
        } else {
            builder.withDetail("cep", "disabled");
        }

        // 索引状态
        try {
            RuleIndexer indexer = ruleEngine.getRuleIndexer();
            if (indexer != null) {
                Map<String, Object> indexDetails = new LinkedHashMap<>();
                indexDetails.put("indexEnabled", indexer.isIndexEnabled());
                indexDetails.put("hasFieldIndex", indexer.hasFieldIndex());
                builder.withDetail("indexer", indexDetails);
            }
        } catch (Exception e) {
            builder.withDetail("indexer", "ERROR - " + extractMessage(e));
        }
    }
}
