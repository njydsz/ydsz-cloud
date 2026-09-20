package com.njydsz.common.seata.config;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * Seata 属性桥接后置处理器。
 *
 * <p>将 {@code ydzs.seata.*} 属性自动桥接到 Seata 原生 {@code seata.*} 命名空间，
 * 使业务方只需维护 {@code ydzs.seata.*} 前缀即可，无需关心 Seata 原生属性名。
 *
 * <p><b>桥接策略（Relaxed / 宽松）：</b>
 * <ul>
 *   <li>原生 {@code seata.*} 属性存在时 → 不覆盖（原生优先）</li>
 *   <li>原生属性不存在且 {@code ydzs.seata.*} 有值 → 自动桥接（ydzs 补充）</li>
 * </ul>
 *
 * <p>此 PostProcessor 优先级低于系统属性和环境变量（通过 {@code addLast} 添加），
 * 确保业务方显式配置的 {@code seata.*} 始终优先。
 *
 * <p>需配合 {@code src/main/resources/META-INF/spring.factories}（Spring Boot 2.7+ 为
 * {@code org.springframework.boot.env.EnvironmentPostProcessor.imports}）注册。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public class SeataPropertyBridgePostProcessor implements EnvironmentPostProcessor {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(SeataPropertyBridgePostProcessor.class);

    /** YDSZ 配置前缀 */
    private static final String YDSZ_PREFIX = "ydsz.seata.";

    /** Seata 原生配置前缀 */
    private static final String SEATA_PREFIX = "seata.";

    /** 桥接属性源名称 */
    private static final String BRIDGE_SOURCE_NAME = "ydsz-seata-property-bridge";

    /**
     * 处理环境属性，桥接 ydzs.seata → seata 命名空间。
     *
     * @param environment 可配置环境
     * @param application Spring 应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> bridgedProperties = new HashMap<>();

        // application-id → seata.client.application-id（已弃用）
        // 注：Seata 1.8+ 不再使用 application-id，转而使用 applicatonId

        // tx-service-group → seata.service.vgroup-mapping 动态注册
        String txGroup = environment.getProperty("ydsz.seata.tx-service-group");
        if (txGroup != null && !txGroup.isEmpty()) {
            // vgroup-mapping.分组名=Seata Server 集群名（通常 default 表示默认集群）
            String mappingKey = "seata.service.vgroup-mapping." + txGroup;
            if (environment.getProperty(mappingKey) == null) {
                bridgedProperties.put(mappingKey, "default");
            }
        }

        // data-source-proxy-mode → seata.data-source-proxy-mode
        copyIfMissing(environment, "ydsz.seata.data-source-proxy-mode",
            "seata.data-source-proxy-mode", bridgedProperties);

        // enable-auto-data-source-proxy → seata.enable-auto-data-source-proxy
        copyIfMissing(environment, "ydsz.seata.enable-auto-data-source-proxy",
            "seata.enable-auto-data-source-proxy", bridgedProperties);

        // disable-global-transaction → seata.disable-global-transaction
        copyIfMissing(environment, "ydzs.seata.disable-global-transaction",
            "seata.disable-global-transaction", bridgedProperties);

        // undo-log.serialization → seata.client.undo.log-serialization
        copyIfMissing(environment, "ydsz.seata.undo-log.serialization",
            "seata.client.undo.log-serialization", bridgedProperties);

        // undo-log.table-name → seata.client.undo.log-table
        copyIfMissing(environment, "ydsz.seata.undo-log.table-name",
            "seata.client.undo.log-table", bridgedProperties);

        // undo-log.only-care-update-columns → seata.client.undo.only-care-update-columns
        copyIfMissing(environment, "ydsz.seata.undo-log.only-care-update-columns",
            "seata.client.undo.only-care-update-columns", bridgedProperties);

        // tm.global-transaction-timeout → seata.client.tm.default-global-transaction-timeout
        copyIfMissing(environment, "ydsz.seata.tm.global-transaction-timeout",
            "seata.client.tm.default-global-transaction-timeout", bridgedProperties);

        // tm.commit-retry-count → seata.client.tm.commit-retry-count
        copyIfMissing(environment, "ydsz.seata.tm.commit-retry-count",
            "seata.client.tm.commit-retry-count", bridgedProperties);

        // tm.rollback-retry-count → seata.client.tm.rollback-retry-count
        copyIfMissing(environment, "ydsz.seata.tm.rollback-retry-count",
            "seata.client.tm.rollback-retry-count", bridgedProperties);

        // rm.async-commit-buffer-limit → seata.client.rm.async-commit-buffer-limit
        copyIfMissing(environment, "ydsz.seata.rm.async-commit-buffer-limit",
            "seata.client.rm.async-commit-buffer-limit", bridgedProperties);

        // rm.report-retry-count → seata.client.rm.report-retry-count
        copyIfMissing(environment, "ydsz.seata.rm.report-retry-count",
            "seata.client.rm.report-retry-count", bridgedProperties);

        // metrics-enabled → seata.metrics.enabled（规范 §25.7 强制要求）
        copyIfMissing(environment, "ydsz.seata.metrics-enabled",
            "seata.metrics.enabled", bridgedProperties);

        if (!bridgedProperties.isEmpty()) {
            MutablePropertySources propertySources = environment.getPropertySources();
            // 移除已存在的桥接源（防止重复添加）
            if (propertySources.contains(BRIDGE_SOURCE_NAME)) {
                propertySources.remove(BRIDGE_SOURCE_NAME);
            }
            // 添加到末尾（优先级最低，原生配置优先）
            propertySources.addLast(new MapPropertySource(BRIDGE_SOURCE_NAME, bridgedProperties));
            logBridgedProperties(bridgedProperties);
        }
    }

    /**
     * 仅当目标属性不存在时，将源属性复制到桥接 Map。
     *
     * @param environment Spring 环境
     * @param srcKey 源属性键（ydsz.seata.*）
     * @param targetKey 目标属性键（seata.*）
     * @param bridged 桥接属性 Map
     */
    private void copyIfMissing(ConfigurableEnvironment environment, String srcKey,
        String targetKey, Map<String, Object> bridged) {
        String value = environment.getProperty(srcKey);
        if (value != null && !value.isEmpty()
                && environment.getProperty(targetKey) == null) {
            bridged.put(targetKey, value);
        }
    }

    /**
     * 输出桥接日志（DEBUG 级别，避免生产日志过多）。
     */
    private void logBridgedProperties(Map<String, Object> bridged) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Seata property bridge: {} properties bridged from ydzs.seata → seata",
                bridged.size());
            bridged.forEach((k, v) -> LOG.debug("  bridged: {} = {}", k, v));
        }
    }
}
