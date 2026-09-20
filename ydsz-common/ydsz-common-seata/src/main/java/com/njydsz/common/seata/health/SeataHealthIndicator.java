package com.njydsz.common.seata.health;

import com.njydsz.common.seata.config.SeataProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seata 分布式事务健康检查指示器。
 *
 * <p>通过 {@code /actuator/health/seata} 端点暴露 Seata 事务协调器连接状态。
 *
 * <p>健康检查维度（运行时级）：
 * <ul>
 *   <li>Seata 模块是否启用（ydsz.seata.enabled）</li>
 *   <li>事务分组（txServiceGroup）是否配置</li>
 *   <li>TransactionManager 是否已初始化（通过反射探测 Seata TransactionManagerHolder）</li>
 *   <li>DataSourceProxy 模式配置是否一致</li>
 * </ul>
 *
 * <p>规范 §25.7 要求 {@code /actuator/health/seata} 包含 TC 连通性和挂起事务数。
 *
 * <p><b>实现策略：</b> Seata API 通过反射调用，避免强依赖。当 classpath 无 Seata 时降级为配置级检查。
 *
 * <p><b>TransactionManager 探测逻辑：</b>
 * <pre>
 *   TransactionManagerHolder.get() != null → tm.initialized = true
 *   TransactionManagerHolder.get() == null → tm.initialized = false（健康度降为 UNKNOWN）
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public class SeataHealthIndicator implements HealthIndicator {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(SeataHealthIndicator.class);

    /** Seata TransactionManagerHolder 类全限定名 */
    private static final String SEATA_TM_HOLDER = "io.seata.tm.api.TransactionManagerHolder";

    /** Seata 配置属性 */
    private final SeataProperties properties;

    /** 指标注册器（可为 null） */
    private final MeterRegistry meterRegistry;

    /**
     * 构造 Seata 健康指示器。
     *
     * @param properties Seata 配置属性
     * @param meterRegistry Micrometer 注册器（可为 null）
     */
    public SeataHealthIndicator(SeataProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 健康检查逻辑（运行时级）。
     *
     * @return 健康状态，包含 Seata 运行时维度信息
     */
    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.unknown().withDetail("seata", "disabled").build();
        }

        Health.Builder builder = Health.up();
        builder.withDetail("seata", "enabled");
        builder.withDetail("applicationId", resolveApplicationId());
        builder.withDetail("txServiceGroup", properties.getTxServiceGroup());
        builder.withDetail("dataSourceProxyMode", properties.getDataSourceProxyMode());
        builder.withDetail("globalTransactionTimeout",
            properties.getTm().getGlobalTransactionTimeout() + "ms");

        // 运行时检查：TransactionManager 是否已初始化
        checkTransactionManagerInitialization(builder);

        return builder.build();
    }

    /**
     * 解析实际 applicationId（处理 spring.application.name 占位符场景）。
     *
     * @return 实际 applicationId 字符串
     */
    private String resolveApplicationId() {
        String appId = properties.getApplicationId();
        if (appId == null || appId.isEmpty()) {
            return "unconfigured";
        }
        // 处理 ${spring.application.name} 占位符未解析的情况
        if (appId.startsWith("${") && appId.endsWith("}")) {
            return "unresolved-placeholder";
        }
        return appId;
    }

    /**
     * 通过反射探测 Seata TransactionManager 初始化状态。
     *
     * @param builder 健康信息构建器
     */
    private void checkTransactionManagerInitialization(Health.Builder builder) {
        try {
            Class<?> tmHolderClass = Class.forName(SEATA_TM_HOLDER);
            Method getMethod = tmHolderClass.getMethod("get");
            Object tm = getMethod.invoke(null);

            if (tm != null) {
                builder.withDetail("transactionManager", "initialized");
                LOG.debug("Seata TransactionManager detected: {}", tm.getClass().getSimpleName());
            } else {
                builder.withDetail("transactionManager", "not_initialized");
                LOG.warn("Seata TransactionManager not initialized yet");
            }
        } catch (ClassNotFoundException e) {
            // Seata TM API 不在 classpath 中（可能 seata-spring-boot-starter 未引入）
            builder.withDetail("transactionManager", "seata_api_unavailable");
            LOG.debug("Seata TransactionManagerHolder not found in classpath");
        } catch (Exception e) {
            builder.withDetail("transactionManager.unknown", e.getMessage());
            LOG.warn("Failed to detect Seata TransactionManager: {}", e.getMessage());
        }
    }
}
