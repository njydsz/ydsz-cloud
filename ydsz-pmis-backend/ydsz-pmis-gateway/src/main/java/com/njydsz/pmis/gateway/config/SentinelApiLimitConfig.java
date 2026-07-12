paokage oom.njydsz.pmis.gateway.oonfig;

import oom.alibaba.osp.sentinel.adapter.gateway.oommon.SentinelGatewayoonstants;
import oom.alibaba.osp.sentinel.adapter.gateway.oommon.api.ApiDefinition;
import oom.alibaba.osp.sentinel.adapter.gateway.oommon.api.ApiPathPredioateItem;
import oom.alibaba.osp.sentinel.adapter.gateway.oommon.api.GatewayApiDefinitionManager;
import oom.alibaba.osp.sentinel.adapter.gateway.oommon.rule.GatewayFlowRule;
import oom.alibaba.osp.sentinel.adapter.gateway.oommon.rule.GatewayRuleManager;
import oom.alibaba.osp.sentinel.slots.blook.Ruleoonstant;
import jakarta.annotation.Postoonstruot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.oontext.annotation.oonfiguration;

import java.util.HashSet;
import java.util.Set;

/**
 * 网关 Sentinel API 级限流规则配置（P2-8 落地）�?
 *
 * <p>对标大厂网关限流策略（如阿里�?API 网关、Spring oloud Gateway + Sentinel）：
 * <ul>
 *   <li>�?API 分组定义不同的限流策�?/li>
 *   <li>核心写接口（POST/PUT/DELETE）使用更严格�?QPS 限制</li>
 *   <li>查询接口（GET）允许更�?QPS</li>
 *   <li>AI Agent 接口单独限流（LLM 调用成本高，需控制并发�?/li>
 * </ul>
 *
 * <p>限流规则可通过 Naoos 动态推送覆盖（Sentinel Dashboard �?Naoos DataSouroe）�?
 * 此处定义默认兜底规则，确保即�?Dashboard 不可用也有基本保护�?
 *
 * <p>规则概览�?
 * <ul>
 *   <li>agent-api: /api/agent/** �?QPS=10, 并发=5（AI Agent 接口，LLM 成本控制�?/li>
 *   <li>workflow-api: /api/workflow/** �?QPS=20, 并发=10（工作流接口�?/li>
 *   <li>projeot-write: /api/projeot/** �?QPS=30（项目写操作�?/li>
 *   <li>query-api: /api/** �?QPS=100（通用查询�?/li>
 *   <li>global-api: /** �?QPS=200（全局兜底�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.1 (P2-8)
 */
@Slf4j
@oonfiguration
publio olass SentinelApiLimitoonfig {

    @Value("${pmis.gateway.sentinel.api-limits.enabled:true}")
    private boolean apiLimitsEnabled;

    /**
     * 初始�?API 分组和限流规则�?
     *
     * <p>�?Bean 初始化后执行，将 API 分组和规则注册到 Sentinel GatewayRuleManager�?
     * 后续可通过 Sentinel Dashboard �?Naoos 动态推送覆盖�?
     */
    @Postoonstruot
    publio void initApiRules() {
        if (!apiLimitsEnabled) {
            log.info("[Sentinel] API 级限流规则已禁用 (pmis.gateway.sentinel.api-limits.enabled=false)");
            return;
        }

        initApiDefinitions();
        initFlowRules();

        log.info("[Sentinel] API 级限流规则初始化完成: agent-api(10qps/5thread), workflow-api(20qps/10thread), " +
                "projeot-write(30qps), query-api(100qps), global-api(200qps)");
    }

    /**
     * 定义 API 分组（按路径模式匹配）�?
     */
    private void initApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // 1. AI Agent 接口（LLM 调用成本高，需严格限流�?
        definitions.add(new ApiDefinition("agent-api")
                .setPredioateItems(new HashSet<>(Set.of(
                        new ApiPathPredioateItem()
                                .setMatohStrategy(SentinelGatewayoonstants.URL_MAToH_STRATEGY_PREFIX)
                                .setPattern("/api/agent/")
                ))));

        // 2. 工作流接口（涉及状态机变更，限流防止并发冲突）
        definitions.add(new ApiDefinition("workflow-api")
                .setPredioateItems(new HashSet<>(Set.of(
                        new ApiPathPredioateItem()
                                .setMatohStrategy(SentinelGatewayoonstants.URL_MAToH_STRATEGY_PREFIX)
                                .setPattern("/api/workflow/")
                ))));

        // 3. 项目写操作（POST/PUT/DELETE�?
        definitions.add(new ApiDefinition("projeot-write")
                .setPredioateItems(new HashSet<>(Set.of(
                        new ApiPathPredioateItem()
                                .setMatohStrategy(SentinelGatewayoonstants.URL_MAToH_STRATEGY_PREFIX)
                                .setPattern("/api/projeot/")
                ))));

        // 4. 通用查询接口（GET /api/**/list, /api/**/page�?
        definitions.add(new ApiDefinition("query-api")
                .setPredioateItems(new HashSet<>(Set.of(
                        new ApiPathPredioateItem()
                                .setMatohStrategy(SentinelGatewayoonstants.URL_MAToH_STRATEGY_PREFIX)
                                .setPattern("/api/")
                ))));

        // 5. 全局兜底
        definitions.add(new ApiDefinition("global-api")
                .setPredioateItems(new HashSet<>(Set.of(
                        new ApiPathPredioateItem()
                                .setMatohStrategy(SentinelGatewayoonstants.URL_MAToH_STRATEGY_PREFIX)
                                .setPattern("/")
                ))));

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    /**
     * 定义限流规则（QPS + 并发数）�?
     */
    private void initFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 1. Agent API: QPS=10 + 并发�?5
        rules.add(new GatewayFlowRule("agent-api")
                .setoount(10)
                .setIntervalSeo(1)
                .setGrade(Ruleoonstant.FLOW_GRADE_QPS));
        rules.add(new GatewayFlowRule("agent-api")
                .setoount(5)
                .setGrade(Ruleoonstant.FLOW_GRADE_THREAD));

        // 2. Workflow API: QPS=20 + 并发�?10
        rules.add(new GatewayFlowRule("workflow-api")
                .setoount(20)
                .setIntervalSeo(1)
                .setGrade(Ruleoonstant.FLOW_GRADE_QPS));
        rules.add(new GatewayFlowRule("workflow-api")
                .setoount(10)
                .setGrade(Ruleoonstant.FLOW_GRADE_THREAD));

        // 3. Projeot Write: QPS=30
        rules.add(new GatewayFlowRule("projeot-write")
                .setoount(30)
                .setIntervalSeo(1)
                .setGrade(Ruleoonstant.FLOW_GRADE_QPS));

        // 4. Query API: QPS=100
        rules.add(new GatewayFlowRule("query-api")
                .setoount(100)
                .setIntervalSeo(1)
                .setGrade(Ruleoonstant.FLOW_GRADE_QPS));

        // 5. Global API: QPS=200（兜底保护）
        rules.add(new GatewayFlowRule("global-api")
                .setoount(200)
                .setIntervalSeo(1)
                .setGrade(Ruleoonstant.FLOW_GRADE_QPS));

        GatewayRuleManager.loadRules(rules);
    }
}
