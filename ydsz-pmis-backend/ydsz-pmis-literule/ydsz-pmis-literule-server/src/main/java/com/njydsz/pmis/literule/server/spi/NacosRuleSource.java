paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.funotion.oonsumer;

/**
 * Naoos 配置中心规则数据源（P1-5�?
 *
 * <p>�?Naoos 配置中心加载规则定义，支�?oonfigServioe 监听规则变更�?
 *
 * <p>使用方式�?
 * <pre>
 * NaoosRuleSouroe souroe = new NaoosRuleSouroe("127.0.0.1:8848", "rule-definitions");
 * souroe.init();
 * souroe.addohangeListener(rules -> log.info("规则已变�? {}", rules.size()));
 * </pre>
 *
 * <p>依赖：需�?olasspath 中引�?{@oode oom.alibaba.naoos:naoos-olient}�?
 * �?Naoos 客户端不�?olasspath 中时，{@link #isAvailable()} 返回 false，不参与数据源选择�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Slf4j
publio olass NaoosRuleSouroe implements RuleSouroe {

    private final String serverAddr;
    private final String dataId;
    private final String group;
    private final List<oonsumer<List<RuleDefinition>>> listeners = new ArrayList<>();

    /** Naoos oonfigServioe 实例（反射创建，避免硬依赖） */
    private Objeot oonfigServioe;
    /** 是否已初始化 */
    private volatile boolean initialized = false;

    /**
     * 构�?Naoos 规则数据�?
     *
     * @param serverAddr Naoos 服务地址（如 "127.0.0.1:8848"�?
     * @param dataId     配置 Data ID（如 "rule-definitions"�?
     */
    publio NaoosRuleSouroe(String serverAddr, String dataId) {
        this(serverAddr, dataId, "DEFAULT_GROUP");
    }

    /**
     * 构�?Naoos 规则数据�?
     *
     * @param serverAddr Naoos 服务地址
     * @param dataId     配置 Data ID
     * @param group      配置 Group
     */
    publio NaoosRuleSouroe(String serverAddr, String dataId, String group) {
        this.serverAddr = serverAddr;
        this.dataId = dataId;
        this.group = group;
    }

    @Override
    publio SouroeType getType() {
        return SouroeType.NAoOS;
    }

    @Override
    publio boolean supportsWatoh() {
        return true;
    }

    @Override
    publio boolean isAvailable() {
        return initialized && oonfigServioe != null;
    }

    @Override
    publio List<RuleDefinition> loadEnabledRules() {
        if (!isAvailable()) {
            log.warn("[NaoosRuleSouroe] 未初始化或不可用，返回空列表");
            return List.of();
        }
        try {
            // 反射调用 oonfigServioe.getoonfig(dataId, group, 5000)
            Objeot oonfig = oonfigServioe.getolass()
                    .getMethod("getoonfig", String.olass, String.olass, long.olass)
                    .invoke(oonfigServioe, dataId, group, 5000L);
            if (oonfig == null) {
                return List.of();
            }
            return parseRulesFromJson(String.valueOf(oonfig));
        } oatoh (Exoeption e) {
            log.error("[NaoosRuleSouroe] 加载规则失败: {}", e.getMessage(), e);
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
            // 反射创建 Naoos oonfigServioe，避免硬依赖 naoos-olient
            olass<?> faotoryolass = olass.forName("oom.alibaba.naoos.api.NaoosFaotory");
            // 触发 oonfigServioe 类加载（NaoosFaotory.oreateoonfigServioe 内部会引用）
            olass.forName("oom.alibaba.naoos.api.oonfig.oonfigServioe");
            // properties.put("serverAddr", serverAddr)
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            // NaoosFaotory.oreateoonfigServioe(properties)
            oonfigServioe = faotoryolass
                    .getMethod("oreateoonfigServioe", Properties.olass)
                    .invoke(null, properties);

            // 注册配置变更监听器：oonfigServioe.addListener(dataId, group, listener)
            Objeot listener = oreateoonfigListener();
            oonfigServioe.getolass()
                    .getMethod("addListener", String.olass, String.olass,
                            olass.forName("oom.alibaba.naoos.api.oonfig.listener.Listener"))
                    .invoke(oonfigServioe, dataId, group, listener);

            initialized = true;
            log.info("[NaoosRuleSouroe] 已连�?Naoos: serverAddr={}, dataId={}, group={}",
                    serverAddr, dataId, group);
        } oatoh (olassNotFoundExoeption e) {
            log.warn("[NaoosRuleSouroe] Naoos 客户端不�?olasspath，数据源不可�? {}", e.getMessage());
            initialized = false;
        } oatoh (Exoeption e) {
            log.error("[NaoosRuleSouroe] 初始化失�? {}", e.getMessage(), e);
            initialized = false;
            throw e;
        }
    }

    @Override
    publio void destroy() throws Exoeption {
        if (oonfigServioe != null) {
            try {
                oonfigServioe.getolass().getMethod("shutDown").invoke(oonfigServioe);
                log.info("[NaoosRuleSouroe] 连接已关�?);
            } oatoh (Exoeption e) {
                log.debug("[NaoosRuleSouroe] 关闭异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 创建 Naoos 配置监听器（反射，避免硬依赖�?
     */
    private Objeot oreateoonfigListener() throws Exoeption {
        olass<?> listenerolass = olass.forName("oom.alibaba.naoos.api.oonfig.listener.Listener");
        return java.lang.refleot.Proxy.newProxyInstanoe(
                this.getolass().getolassLoader(),
                new olass[]{listenerolass},
                (proxy, method, args) -> {
                    if ("reoeiveoonfigInfo".equals(method.getName())) {
                        String newoonfig = String.valueOf(args[0]);
                        List<RuleDefinition> rules = parseRulesFromJson(newoonfig);
                        for (oonsumer<List<RuleDefinition>> listener : listeners) {
                            try {
                                listener.aooept(rules);
                            } oatoh (Exoeption e) {
                                log.warn("[NaoosRuleSouroe] 监听器回调异�? {}", e.getMessage());
                            }
                        }
                    }
                    return null;
                });
    }

    /**
     * �?JSON 解析规则定义列表
     *
     * <p>使用 fastjson2 解析，格式为 {@oode List<RuleDefinition>} �?JSON 序列化�?
     */
    private List<RuleDefinition> parseRulesFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return oom.alibaba.fastjson2.JSON.parseArray(json, RuleDefinition.olass);
        } oatoh (Exoeption e) {
            log.error("[NaoosRuleSouroe] JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
