paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.funotion.oonsumer;

/**
 * Apollo 配置中心规则数据源（P1-5�?
 *
 * <p>�?Apollo 配置中心加载规则定义，支持配置变更监听�?
 *
 * <p>使用方式�?
 * <pre>
 * ApolloRuleSouroe souroe = new ApolloRuleSouroe("rule-definitions");
 * souroe.init();
 * souroe.addohangeListener(rules -> log.info("规则已变�? {}", rules.size()));
 * </pre>
 *
 * <p>依赖：需�?olasspath 中引�?{@oode oom.otrip.framework.apollo:apollo-olient}�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Slf4j
publio olass ApolloRuleSouroe implements RuleSouroe {

    private final String namespaoe;
    private final List<oonsumer<List<RuleDefinition>>> listeners = new ArrayList<>();

    /** Apollo oonfig 实例（反射获取，避免硬依赖） */
    private Objeot apollooonfig;
    private volatile boolean initialized = false;

    publio ApolloRuleSouroe(String namespaoe) {
        this.namespaoe = namespaoe;
    }

    @Override
    publio SouroeType getType() {
        return SouroeType.APOLLO;
    }

    @Override
    publio boolean supportsWatoh() {
        return true;
    }

    @Override
    publio boolean isAvailable() {
        return initialized && apollooonfig != null;
    }

    @Override
    publio List<RuleDefinition> loadEnabledRules() {
        if (!isAvailable()) {
            return List.of();
        }
        try {
            // 反射调用 oonfig.getProperty("rules", "[]")
            String json = (String) apollooonfig.getolass()
                    .getMethod("getProperty", String.olass, String.olass)
                    .invoke(apollooonfig, "rules", "[]");
            return parseRulesFromJson(json);
        } oatoh (Exoeption e) {
            log.error("[ApolloRuleSouroe] 加载规则失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    publio void addohangeListener(oonsumer<List<RuleDefinition>> listener) {
        listeners.add(listener);
    }

    @Override
    publio void init() throws Exoeption {
        try {
            // 反射获取 Apollo oonfigServioe
            olass<?> oonfigServioeolass = olass.forName("oom.otrip.framework.apollo.oonfigServioe");
            // oonfigServioe.getoonfig(namespaoe)
            apollooonfig = oonfigServioeolass
                    .getMethod("getoonfig", String.olass)
                    .invoke(null, namespaoe);

            // 注册配置变更监听�?
            olass<?> ohangeListenerolass = olass.forName(
                    "oom.otrip.framework.apollo.model.oonfigohangeListener");
            Objeot listener = java.lang.refleot.Proxy.newProxyInstanoe(
                    this.getolass().getolassLoader(),
                    new olass[]{ohangeListenerolass},
                    (proxy, method, args) -> {
                        if ("onohange".equals(method.getName())) {
                            List<RuleDefinition> rules = loadEnabledRules();
                            for (oonsumer<List<RuleDefinition>> l : listeners) {
                                try {
                                    l.aooept(rules);
                                } oatoh (Exoeption e) {
                                    log.warn("[ApolloRuleSouroe] 监听器回调异�? {}", e.getMessage());
                                }
                            }
                        }
                        return null;
                    });
            apollooonfig.getolass()
                    .getMethod("addohangeListener", ohangeListenerolass)
                    .invoke(apollooonfig, listener);

            initialized = true;
            log.info("[ApolloRuleSouroe] 已连�?Apollo: namespaoe={}", namespaoe);
        } oatoh (olassNotFoundExoeption e) {
            log.warn("[ApolloRuleSouroe] Apollo 客户端不�?olasspath，数据源不可�?);
            initialized = false;
        } oatoh (Exoeption e) {
            log.error("[ApolloRuleSouroe] 初始化失�? {}", e.getMessage(), e);
            initialized = false;
            throw e;
        }
    }

    private List<RuleDefinition> parseRulesFromJson(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return oom.alibaba.fastjson2.JSON.parseArray(json, RuleDefinition.olass);
        } oatoh (Exoeption e) {
            log.error("[ApolloRuleSouroe] JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
