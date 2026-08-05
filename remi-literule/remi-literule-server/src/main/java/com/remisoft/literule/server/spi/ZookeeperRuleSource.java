package com.remisoft.literule.server.spi;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.remisoft.common.json.RemiJson;

import com.remisoft.literule.api.RuleDefinition;

import lombok.extern.slf4j.Slf4j;

/**
 * ZooKeeper 规则数据源（P1-5）
 *
 * <p>从 ZooKeeper 节点加载规则定义，支持 NodeCache 监听节点变更。
 *
 * <p>使用方式：
 * <pre>
 * ZookeeperRuleSource source = new ZookeeperRuleSource("127.0.0.1:2181", "/literule/definitions");
 * source.init();
 * source.addChangeListener(rules -> log.info("规则已变更: {}", rules.size()));
 * </pre>
 *
 * <p>依赖：需在 classpath 中引入 {@code org.apache.curator:curator-recipes}。
 *
 * @since 1.0.0
 * @author remi-team
 */
@Slf4j
public class ZookeeperRuleSource implements RuleSource {

    private final String connectString;
    private final String path;
    private final List<Consumer<List<RuleDefinition>>> listeners = new ArrayList<>();

    /** CuratorFramework 实例（反射创建，避免硬依赖） */
    private Object client;
    private volatile boolean initialized = false;

    public ZookeeperRuleSource(String connectString, String path) {
        this.connectString = connectString;
        this.path = path;
    }

    @Override
    public SourceType getType() {
        return SourceType.ZOOKEEPER;
    }

    @Override
    public boolean supportsWatch() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return initialized && client != null;
    }

    @Override
    public List<RuleDefinition> loadEnabledRules() {
        if (!isAvailable()) {
            return List.of();
        }
        try {
            // 反射调用 client.getData().forPath(path)
            Object dataBuilder = client.getClass().getMethod("getData").invoke(client);
            byte[] data = (byte[]) dataBuilder.getClass()
                    .getMethod("forPath", String.class)
                    .invoke(dataBuilder, path);
            if (data == null || data.length == 0) {
                return List.of();
            }
            String json = new String(data, StandardCharsets.UTF_8);
            return parseRulesFromJson(json);
        } catch (Exception e) {
            log.error("[ZookeeperRuleSource] 加载规则失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public void addChangeListener(Consumer<List<RuleDefinition>> listener) {
        listeners.add(listener);
    }

    @Override
    public void init() throws Exception {
        try {
            // 反射创建 CuratorFramework
            Class<?> builderClass = Class.forName("org.apache.curator.framework.CuratorFrameworkFactory");
            // CuratorFrameworkFactory.builder()
            Object builder = builderClass.getMethod("builder").invoke(null);
            // .connectString(connectString)
            builder = builder.getClass().getMethod("connectString", String.class).invoke(builder, connectString);
            // .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            Class<?> retryPolicyClass = Class.forName("org.apache.curator.retry.ExponentialBackoffRetry");
            Object retryPolicy = retryPolicyClass
                    .getConstructor(int.class, int.class).newInstance(1000, 3);
            builder = builder.getClass().getMethod("retryPolicy",
                    Class.forName("org.apache.curator.RetryPolicy")).invoke(builder, retryPolicy);
            // .build()
            client = builder.getClass().getMethod("build").invoke(builder);
            // client.start()
            client.getClass().getMethod("start").invoke(client);

            // 确保路径存在
            try {
                Object createBuilder = client.getClass().getMethod("create").invoke(client);
                createBuilder.getClass()
                        .getMethod("creatingParentsIfNeeded")
                        .invoke(createBuilder);
                createBuilder.getClass()
                        .getMethod("forPath", String.class)
                        .invoke(createBuilder, path);
            } catch (Exception ignored) {
                // 节点已存在
            }

            // 注册 NodeCache 监听器
            registerNodeCache();

            initialized = true;
            log.info("[ZookeeperRuleSource] 已连接 ZooKeeper: connectString={}, path={}", connectString, path);
        } catch (ClassNotFoundException e) {
            log.warn("[ZookeeperRuleSource] Curator 不在 classpath，数据源不可用");
            initialized = false;
        } catch (Exception e) {
            log.error("[ZookeeperRuleSource] 初始化失败: {}", e.getMessage(), e);
            initialized = false;
            throw e;
        }
    }

    /**
     * 注册 NodeCache 监听节点变更
     */
    private void registerNodeCache() throws Exception {
        try {
            Class<?> nodeCacheClass = Class.forName("org.apache.curator.framework.recipes.cache.NodeCache");
            Object nodeCache = nodeCacheClass
                    .getConstructor(Class.forName("org.apache.curator.framework.CuratorFramework"),
                            String.class)
                    .newInstance(client, path);
            // nodeCache.getListenable().addListener(listener)
            Object listenable = nodeCacheClass.getMethod("getListenable").invoke(nodeCache);
            Class<?> listenerClass = Class.forName(
                    "org.apache.curator.framework.recipes.cache.NodeCacheListener");

            Object listener = Proxy.newProxyInstance(
                    this.getClass().getClassLoader(),
                    new Class[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("nodeChanged".equals(method.getName())) {
                            List<RuleDefinition> rules = loadEnabledRules();
                            for (Consumer<List<RuleDefinition>> l : listeners) {
                                try {
                                    l.accept(rules);
                                } catch (Exception e) {
                                    log.warn("[ZookeeperRuleSource] 监听器回调异常: {}", e.getMessage());
                                }
                            }
                        }
                        return null;
                    });
            listenable.getClass()
                    .getMethod("addListener", listenerClass)
                    .invoke(listenable, listener);
            // nodeCache.start(true)
            nodeCacheClass.getMethod("start", boolean.class).invoke(nodeCache, true);
        } catch (Exception e) {
            log.warn("[ZookeeperRuleSource] NodeCache 注册失败: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() throws Exception {
        if (client != null) {
            try {
                client.getClass().getMethod("close").invoke(client);
                log.info("[ZookeeperRuleSource] 连接已关闭");
            } catch (Exception e) {
                log.debug("[ZookeeperRuleSource] 关闭异常: {}", e.getMessage());
            }
        }
    }

    private List<RuleDefinition> parseRulesFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return RemiJson.parseArray(json, RuleDefinition.class);
        } catch (Exception e) {
            log.error("[ZookeeperRuleSource] JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
