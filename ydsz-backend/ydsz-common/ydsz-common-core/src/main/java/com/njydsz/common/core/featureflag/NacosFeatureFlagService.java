package com.njydsz.common.core.featureflag;

import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.object.YdszJsonObject;

/**
 * Nacos 动态配置源特性开关服务
 *
 * <p>在 {@link DefaultFeatureFlagService} 基础上增加 Nacos 配置中心监听能力，
 * 实现特性开关的运行时动态推送。当 Nacos 配置变更时，自动解析 JSON 内容并
 * 调用 {@link #setEnabled} / {@link #setRolloutPercentage} 更新内存状态。
 *
 * <h3>配置格式</h3>
 * <p>Nacos 下发的 JSON 格式：
 * <pre>{@code
 * {
 *   "NEW_DASHBOARD": { "enabled": true, "rollout": 50 },
 *   "BATCH_EXPORT":  { "enabled": false }
 * }
 * }</pre>
 *
 * <p>未知 key 会被忽略（向前兼容新增开关），值缺省时保留原有状态不变。
 *
 * <h3>依赖策略</h3>
 * <p>通过反射调用 {@code com.alibaba.nacos.api.config.ConfigService}，避免
 * 对 {@code nacos-client} 的硬依赖。当 classpath 缺失 Nacos 客户端时，
 * {@link #isAvailable()} 返回 false，{@link #init()} 不抛异常仅记录告警，
 * 服务降级为 {@link DefaultFeatureFlagService} 的内存模式。
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>{@link #init()} — 由 {@link FeatureFlagAutoConfiguration} 在 Bean 初始化后调用</li>
 *   <li>{@link #destroy()} — 由 Spring 容器关闭时调用，释放 Nacos 连接</li>
 *   <li>{@link #refresh()} — 重新拉取 Nacos 配置并覆盖内存状态</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NacosFeatureFlagService extends DefaultFeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(NacosFeatureFlagService.class);

    /** Nacos ConfigService 实例（反射创建，避免硬依赖） */
    private volatile Object configService;

    /** Nacos 监听器代理（用于注销） */
    private volatile Object listenerProxy;

    /** 是否已成功初始化 */
    private volatile boolean initialized = false;

    public NacosFeatureFlagService(FeatureFlagProperties properties, Environment environment) {
        super(properties, environment);
    }

    /**
     * 初始化 Nacos 连接并注册配置监听器
     *
     * <p>当 Nacos 客户端不在 classpath、或配置未启用时，本方法安全降级，
     * 不抛异常，服务回退到 {@link DefaultFeatureFlagService} 的内存模式。
     */
    public void init() {
        FeatureFlagProperties.NacosConfig cfg = properties.getNacos();
        if (cfg == null || !cfg.isEnabled()) {
            log.info("[FeatureFlag-Nacos] 未启用 Nacos 动态配置源，使用内存模式");
            return;
        }
        String serverAddr = resolveServerAddr(cfg);
        if (serverAddr == null || serverAddr.isBlank()) {
            log.warn("[FeatureFlag-Nacos] server-addr 未配置，使用内存模式");
            return;
        }
        try {
            // 反射创建 Nacos ConfigService，避免硬依赖 nacos-client
            Class<?> factoryClass = Class.forName("com.alibaba.nacos.api.NacosFactory");
            Class.forName("com.alibaba.nacos.api.config.ConfigService");
            Properties nacosProps = new Properties();
            nacosProps.put("serverAddr", serverAddr);
            configService = factoryClass
                    .getMethod("createConfigService", Properties.class)
                    .invoke(null, nacosProps);

            // 首次拉取配置
            String initialConfig = fetchConfig(cfg);
            applyRemoteConfig(initialConfig, "init");

            // 注册配置变更监听器
            registerListener(cfg);

            initialized = true;
            log.info("[FeatureFlag-Nacos] 已连接 Nacos: serverAddr={}, dataId={}, group={}",
                    serverAddr, cfg.getDataId(), cfg.getGroup());
        } catch (ClassNotFoundException e) {
            log.warn("[FeatureFlag-Nacos] Nacos 客户端不在 classpath，降级为内存模式: {}",
                    e.getMessage());
        } catch (Exception e) {
            log.error("[FeatureFlag-Nacos] 初始化失败，降级为内存模式: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析 Nacos server-addr，未配置时回退到 Spring Cloud Nacos 配置
     */
    private String resolveServerAddr(FeatureFlagProperties.NacosConfig cfg) {
        if (cfg.getServerAddr() != null && !cfg.getServerAddr().isBlank()) {
            return cfg.getServerAddr();
        }
        if (environment != null) {
            String fallback = environment.getProperty("spring.cloud.nacos.config.server-addr");
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
        }
        return null;
    }

    /**
     * 反射调用 configService.getConfig(dataId, group, timeout)
     */
    private String fetchConfig(FeatureFlagProperties.NacosConfig cfg) throws Exception {
        Object config = configService.getClass()
                .getMethod("getConfig", String.class, String.class, long.class)
                .invoke(configService, cfg.getDataId(), cfg.getGroup(), cfg.getTimeoutMs());
        return config == null ? null : String.valueOf(config);
    }

    /**
     * 注册 Nacos 配置变更监听器
     */
    private void registerListener(FeatureFlagProperties.NacosConfig cfg) throws Exception {
        Class<?> listenerClass = Class.forName("com.alibaba.nacos.api.config.listener.Listener");
        listenerProxy = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{listenerClass},
                (proxy, method, args) -> {
                    if ("receiveConfigInfo".equals(method.getName())) {
                        String newConfig = args[0] == null ? null : String.valueOf(args[0]);
                        applyRemoteConfig(newConfig, "push");
                    }
                    return null;
                });
        configService.getClass()
                .getMethod("addListener", String.class, String.class, listenerClass)
                .invoke(configService, cfg.getDataId(), cfg.getGroup(), listenerProxy);
    }

    /**
     * 应用 Nacos 下发的 JSON 配置到内存状态
     *
     * <p>格式：{@code { "FLAG_KEY": { "enabled": true, "rollout": 50 } }}
     * 未知 key 静默忽略；值缺省保留原状态。
     */
    protected void applyRemoteConfig(String json, String source) {
        if (json == null || json.isBlank()) {
            log.debug("[FeatureFlag-Nacos] 配置为空 (source={})，跳过", source);
            return;
        }
        try {
            YdszJsonObject root = YdszJson.parseObjectToJsonObject(json);
            if (root == null) {
                return;
            }
            int updated = 0;
            for (String key : root.keySet()) {
                FeatureFlag flag;
                try {
                    flag = FeatureFlag.valueOf(key);
                } catch (IllegalArgumentException ex) {
                    log.debug("[FeatureFlag-Nacos] 忽略未知开关 key: {}", key);
                    continue;
                }
                YdszJsonObject obj = root.getJSONObject(key);
                if (obj == null) {
                    continue;
                }
                Boolean enabled = obj.getBoolean("enabled");
                Integer rollout = obj.getInteger("rollout");
                if (enabled != null) {
                    setEnabled(flag, enabled);
                    updated++;
                }
                if (rollout != null) {
                    setRolloutPercentage(flag, rollout);
                    updated++;
                }
            }
            log.info("[FeatureFlag-Nacos] 配置已应用 (source={}, updates={})", source, updated);
        } catch (Exception e) {
            log.error("[FeatureFlag-Nacos] 解析配置失败 (source={}): {}", source, e.getMessage(), e);
        }
    }

    /**
     * 判断 Nacos 配置源是否可用
     */
    public boolean isAvailable() {
        return initialized && configService != null;
    }

    /**
     * 销毁时关闭 Nacos 连接
     */
    public void destroy() {
        if (configService != null) {
            try {
                configService.getClass().getMethod("shutDown").invoke(configService);
                log.info("[FeatureFlag-Nacos] 连接已关闭");
            } catch (Exception e) {
                log.debug("[FeatureFlag-Nacos] 关闭异常: {}", e.getMessage());
            }
        }
    }

    @Override
    public void refresh() {
        // 先重置内存状态为 properties 中的初始值
        super.refresh();
        // 再从 Nacos 拉取最新配置覆盖
        if (isAvailable()) {
            try {
                String config = fetchConfig(properties.getNacos());
                applyRemoteConfig(config, "refresh");
            } catch (Exception e) {
                log.warn("[FeatureFlag-Nacos] refresh 拉取失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 用于测试注入 Mock ConfigService
     */
    void setConfigServiceForTest(Object configService) {
        this.configService = Objects.requireNonNull(configService);
        this.initialized = true;
    }
}
