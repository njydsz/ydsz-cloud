package com.njydsz.gateway.config;

import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;

import lombok.extern.slf4j.Slf4j;

/**
 * 网关 Sentinel API 级限流规则配置（P2-8 落地）。
 *
 * <p>对标大厂网关限流策略（如阿里云 API 网关、Spring Cloud Gateway + Sentinel）：
 * <ul>
 *   <li>按 API 分组定义不同的限流策略</li>
 *   <li>核心写接口（POST/PUT/DELETE）使用更严格的 QPS 限制</li>
 *   <li>查询接口（GET）允许更高 QPS</li>
 *   <li>AI Agent 接口单独限流（LLM 调用成本高，需控制并发）</li>
 * </ul>
 *
 * <p>限流规则可通过 Nacos 动态推送覆盖（Sentinel Dashboard 或 Nacos DataSource）。
 * 此处定义默认兜底规则，确保即使 Dashboard 不可用也有基本保护。
 *
 * <p>规则概览：
 * <ul>
 *   <li>agent-api: /api/agent/** — QPS=10, 并发=5（AI Agent 接口，LLM 成本控制）</li>
 *   <li>workflow-api: /api/workflow/** — QPS=20, 并发=10（工作流接口）</li>
 *   <li>project-write: /api/project/** — QPS=30（项目写操作）</li>
 *   <li>query-api: /api/** — QPS=100（通用查询）</li>
 *   <li>global-api: /** — QPS=200（全局兜底）</li>
 * </ul>
 *
 * @since 1.0.0 (P2-8)
 */
@Slf4j
@Configuration
public class SentinelApiLimitConfig {

    @Value("${ydsz.gateway.sentinel.api-limits.enabled:true}")
    private boolean apiLimitsEnabled;

    /**
     * 初始化 API 分组和限流规则。
     *
     * <p>在 Bean 初始化后执行，将 API 分组和规则注册到 Sentinel GatewayRuleManager。
     * 后续可通过 Sentinel Dashboard 或 Nacos 动态推送覆盖。
     */
    @PostConstruct
    public void initApiRules() {
        if (!apiLimitsEnabled) {
            log.info("[Sentinel] API 级限流规则已禁用 (ydsz.gateway.sentinel.api-limits.enabled=false)");
            return;
        }

        initApiDefinitions();
        initFlowRules();

        log.info("[Sentinel] API 级限流规则初始化完成: agent-api(10qps/5thread), workflow-api(20qps/10thread), " +
                "project-write(30qps), query-api(100qps), global-api(200qps)");
    }

    /**
     * 定义 API 分组（按路径模式匹配）。
     */
    private void initApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // 1. AI Agent 接口（LLM 调用成本高，需严格限流）
        definitions.add(new ApiDefinition("agent-api")
                .setPredicateItems(new HashSet<>(Set.of(
                        new ApiPathPredicateItem()
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                                .setPattern("/api/agent/")
                ))));

        // 2. 工作流接口（涉及状态机变更，限流防止并发冲突）
        definitions.add(new ApiDefinition("workflow-api")
                .setPredicateItems(new HashSet<>(Set.of(
                        new ApiPathPredicateItem()
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                                .setPattern("/api/workflow/")
                ))));

        // 3. 项目写操作（POST/PUT/DELETE）
        definitions.add(new ApiDefinition("project-write")
                .setPredicateItems(new HashSet<>(Set.of(
                        new ApiPathPredicateItem()
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                                .setPattern("/api/project/")
                ))));

        // 4. 通用查询接口（GET /api/**/list, /api/**/page）
        definitions.add(new ApiDefinition("query-api")
                .setPredicateItems(new HashSet<>(Set.of(
                        new ApiPathPredicateItem()
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                                .setPattern("/api/")
                ))));

        // 5. 全局兜底
        definitions.add(new ApiDefinition("global-api")
                .setPredicateItems(new HashSet<>(Set.of(
                        new ApiPathPredicateItem()
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                                .setPattern("/")
                ))));

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    /**
     * 定义限流规则（QPS + 并发数）。
     */
    private void initFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 1. Agent API: QPS=10 + 并发数=5
        rules.add(new GatewayFlowRule("agent-api")
                .setCount(10)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));
        rules.add(new GatewayFlowRule("agent-api")
                .setCount(5)
                .setGrade(RuleConstant.FLOW_GRADE_THREAD));

        // 2. Workflow API: QPS=20 + 并发数=10
        rules.add(new GatewayFlowRule("workflow-api")
                .setCount(20)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));
        rules.add(new GatewayFlowRule("workflow-api")
                .setCount(10)
                .setGrade(RuleConstant.FLOW_GRADE_THREAD));

        // 3. Project Write: QPS=30
        rules.add(new GatewayFlowRule("project-write")
                .setCount(30)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));

        // 4. Query API: QPS=100
        rules.add(new GatewayFlowRule("query-api")
                .setCount(100)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));

        // 5. Global API: QPS=200（兜底保护）
        rules.add(new GatewayFlowRule("global-api")
                .setCount(200)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));

        GatewayRuleManager.loadRules(rules);
    }
}
