package com.njydsz.literule.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.njydsz.literule.server.cep.CEPEngine;
import com.njydsz.literule.server.core.AsyncTraceRecorder;
import com.njydsz.literule.server.core.DefaultRuleEngine;
import com.njydsz.literule.server.core.RuleCircuitBreaker;
import com.njydsz.literule.server.core.RuleMetrics;
import com.njydsz.literule.server.core.RuleIndexer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则引擎健康检查指标
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
 * <p>对标项目其他模块的 HealthIndicator 规范（common-cache/common-search/common-notify 等）。
 *
 * @author ydsz-team
 * @since 2.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.literule", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LiteRuleHealthIndicator implements HealthIndicator {

    private final DefaultRuleEngine ruleEngine;
    private final CEPEngine cepEngine;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean allUp = true;

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
            if (breaker != null) {
                engineDetails.put("circuitBreaker", "enabled");
            } else {
                engineDetails.put("circuitBreaker", "disabled");
            }

            details.put("engine", engineDetails);
        } catch (Exception e) {
            details.put("engine", "DOWN - " + e.getMessage());
            allUp = false;
        }

        // 熔断器状态
        try {
            RuleCircuitBreaker breaker = ruleEngine.getCircuitBreaker();
            if (breaker != null) {
                Map<String, Object> breakerDetails = new LinkedHashMap<>();
                int openCount = 0;
                int halfOpenCount = 0;
                for (com.njydsz.literule.api.Rule rule : ruleEngine.getRules()) {
                    RuleCircuitBreaker.State state = breaker.getState(rule.getCode());
                    if (state == RuleCircuitBreaker.State.OPEN) {
                        openCount++;
                    } else if (state == RuleCircuitBreaker.State.HALF_OPEN) {
                        halfOpenCount++;
                    }
                }
                breakerDetails.put("openRules", openCount);
                breakerDetails.put("halfOpenRules", halfOpenCount);
                details.put("circuitBreaker", breakerDetails);
            }
        } catch (Exception e) {
            details.put("circuitBreaker", "ERROR - " + e.getMessage());
        }

        // 异步 Trace 队列
        try {
            if (ruleEngine.getTraceRecorder() instanceof AsyncTraceRecorder asyncRecorder) {
                Map<String, Object> traceDetails = new LinkedHashMap<>();
                traceDetails.put("queueSize", asyncRecorder.getQueueSize());
                traceDetails.put("queueCapacity", asyncRecorder.getQueueCapacity());
                traceDetails.put("running", asyncRecorder.isRunning());
                details.put("traceRecorder", traceDetails);
            } else if (ruleEngine.getTraceRecorder() != null) {
                details.put("traceRecorder", "custom");
            } else {
                details.put("traceRecorder", "disabled");
            }
        } catch (Exception e) {
            details.put("traceRecorder", "ERROR - " + e.getMessage());
        }

        // CEP 引擎状态
        try {
            Map<String, Object> cepDetails = new LinkedHashMap<>();
            cepDetails.put("patterns", cepEngine.patternCount());
            cepDetails.put("totalHits", cepEngine.totalHits());
            details.put("cep", cepDetails);
        } catch (Exception e) {
            details.put("cep", "DOWN - " + e.getMessage());
            allUp = false;
        }

        // 索引状态
        try {
            RuleIndexer indexer = ruleEngine.getRuleIndexer();
            if (indexer != null) {
                Map<String, Object> indexDetails = new LinkedHashMap<>();
                indexDetails.put("indexEnabled", indexer.isIndexEnabled());
                indexDetails.put("hasFieldIndex", indexer.hasFieldIndex());
                details.put("indexer", indexDetails);
            }
        } catch (Exception e) {
            details.put("indexer", "ERROR - " + e.getMessage());
        }

        return allUp ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}
