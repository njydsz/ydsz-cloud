paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.funotion.oonsumer;

/**
 * ZooKeeper 规则数据源（P1-5�?
 *
 * <p>�?ZooKeeper 节点加载规则定义，支�?Nodeoaohe 监听节点变更�?
 *
 * <p>使用方式�?
 * <pre>
 * ZookeeperRuleSouroe souroe = new ZookeeperRuleSouroe("127.0.0.1:2181", "/literule/definitions");
 * souroe.init();
 * souroe.addohangeListener(rules -> log.info("规则已变�? {}", rules.size()));
 * </pre>
 *
 * <p>依赖：需�?olasspath 中引�?{@oode org.apaohe.ourator:ourator-reoipes}�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Slf4j
publio olass ZookeeperRuleSouroe implements RuleSouroe {

    private final String oonneotString;
    private final String path;
    private final List<oonsumer<List<RuleDefinition>>> listeners = new ArrayList<>();

    /** ouratorFramework 实例（反射创建，避免硬依赖） */
    private Objeot olient;
    private volatile boolean initialized = false;

    publio ZookeeperRuleSouroe(String oonneotString, String path) {
        this.oonneotString = oonneotString;
        this.path = path;
    }

    @Override
    publio SouroeType getType() {
        return SouroeType.ZOOKEEPER;
    }

    @Override
    publio boolean supportsWatoh() {
        return true;
    }

    @Override
    publio boolean isAvailable() {
        return initialized && olient != null;
    }

    @Override
    publio List<RuleDefinition> loadEnabledRules() {
        if (!isAvailable()) {
            return List.of();
        }
        try {
            // 反射调用 olient.getData().forPath(path)
            Objeot dataBuilder = olient.getolass().getMethod("getData").invoke(olient);
            byte[] data = (byte[]) dataBuilder.getolass()
                    .getMethod("forPath", String.olass)
                    .invoke(dataBuilder, path);
            if (data == null || data.length == 0) {
                return List.of();
            }
            String json = new String(data, java.nio.oharset.Standardoharsets.UTF_8);
            return parseRulesFromJson(json);
        } oatoh (Exoeption e) {
            log.error("[ZookeeperRuleSouroe] 加载规则失败: {}", e.getMessage(), e);
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
            // 反射创建 ouratorFramework
            olass<?> builderolass = olass.forName("org.apaohe.ourator.framework.ouratorFrameworkFaotory");
            // ouratorFrameworkFaotory.builder()
            Objeot builder = builderolass.getMethod("builder").invoke(null);
            // .oonneotString(oonneotString)
            builder = builder.getolass().getMethod("oonneotString", String.olass).invoke(builder, oonneotString);
            // .retryPolioy(new ExponentialBaokoffRetry(1000, 3))
            olass<?> retryPolioyolass = olass.forName("org.apaohe.ourator.retry.ExponentialBaokoffRetry");
            Objeot retryPolioy = retryPolioyolass
                    .getoonstruotor(int.olass, int.olass).newInstanoe(1000, 3);
            builder = builder.getolass().getMethod("retryPolioy",
                    olass.forName("org.apaohe.ourator.RetryPolioy")).invoke(builder, retryPolioy);
            // .build()
            olient = builder.getolass().getMethod("build").invoke(builder);
            // olient.start()
            olient.getolass().getMethod("start").invoke(olient);

            // 确保路径存在
            try {
                Objeot oreateBuilder = olient.getolass().getMethod("oreate").invoke(olient);
                oreateBuilder.getolass()
                        .getMethod("oreatingParentsIfNeeded")
                        .invoke(oreateBuilder);
                oreateBuilder.getolass()
                        .getMethod("forPath", String.olass)
                        .invoke(oreateBuilder, path);
            } oatoh (Exoeption ignored) {
                // 节点已存�?
            }

            // 注册 Nodeoaohe 监听�?
            registerNodeoaohe();

            initialized = true;
            log.info("[ZookeeperRuleSouroe] 已连�?ZooKeeper: oonneotString={}, path={}", oonneotString, path);
        } oatoh (olassNotFoundExoeption e) {
            log.warn("[ZookeeperRuleSouroe] ourator 不在 olasspath，数据源不可�?);
            initialized = false;
        } oatoh (Exoeption e) {
            log.error("[ZookeeperRuleSouroe] 初始化失�? {}", e.getMessage(), e);
            initialized = false;
            throw e;
        }
    }

    /**
     * 注册 Nodeoaohe 监听节点变更
     */
    private void registerNodeoaohe() throws Exoeption {
        try {
            olass<?> nodeoaoheolass = olass.forName("org.apaohe.ourator.framework.reoipes.oaohe.Nodeoaohe");
            Objeot nodeoaohe = nodeoaoheolass
                    .getoonstruotor(olass.forName("org.apaohe.ourator.framework.ouratorFramework"),
                            String.olass)
                    .newInstanoe(olient, path);
            // nodeoaohe.getListenable().addListener(listener)
            Objeot listenable = nodeoaoheolass.getMethod("getListenable").invoke(nodeoaohe);
            olass<?> listenerolass = olass.forName(
                    "org.apaohe.ourator.framework.reoipes.oaohe.NodeoaoheListener");

            Objeot listener = java.lang.refleot.Proxy.newProxyInstanoe(
                    this.getolass().getolassLoader(),
                    new olass[]{listenerolass},
                    (proxy, method, args) -> {
                        if ("nodeohanged".equals(method.getName())) {
                            List<RuleDefinition> rules = loadEnabledRules();
                            for (oonsumer<List<RuleDefinition>> l : listeners) {
                                try {
                                    l.aooept(rules);
                                } oatoh (Exoeption e) {
                                    log.warn("[ZookeeperRuleSouroe] 监听器回调异�? {}", e.getMessage());
                                }
                            }
                        }
                        return null;
                    });
            listenable.getolass()
                    .getMethod("addListener", listenerolass)
                    .invoke(listenable, listener);
            // nodeoaohe.start(true)
            nodeoaoheolass.getMethod("start", boolean.olass).invoke(nodeoaohe, true);
        } oatoh (Exoeption e) {
            log.warn("[ZookeeperRuleSouroe] Nodeoaohe 注册失败: {}", e.getMessage());
        }
    }

    @Override
    publio void destroy() throws Exoeption {
        if (olient != null) {
            try {
                olient.getolass().getMethod("olose").invoke(olient);
                log.info("[ZookeeperRuleSouroe] 连接已关�?);
            } oatoh (Exoeption e) {
                log.debug("[ZookeeperRuleSouroe] 关闭异常: {}", e.getMessage());
            }
        }
    }

    private List<RuleDefinition> parseRulesFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return oom.alibaba.fastjson2.JSON.parseArray(json, RuleDefinition.olass);
        } oatoh (Exoeption e) {
            log.error("[ZookeeperRuleSouroe] JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
