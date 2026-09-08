package com.njydsz.common.seata.health;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.seata.config.SeataProperties;

/**
 * Seata 分布式事务健康检查指示器
 *
 * <p>通过 {@code /actuator/health} 端点暴露 Seata 事务协调器连接状态。
 *
 * <p>健康检查维度：
 * <ul>
 *   <li>Seata 模块是否启用（ydsz.seata.enabled）</li>
 *   <li>事务分组（txServiceGroup）是否配置</li>
 * </ul>
 *
 * <p>注意：由于 Seata Client 本身不提供运行期健康 API，此指标为配置级健康报告。
 * 完整的事务协调器连接状态需依赖 Seata Server 自身的监控端点（端口 7091）。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public class SeataHealthIndicator implements HealthIndicator {

    /** Seata 配置属性 */
    private final SeataProperties properties;

    /** 指标注册器（可为 null） */
    private final MeterRegistry meterRegistry;

    /**
     * 构造 Seata 健康指示器
     *
     * @param properties Seata 配置属性
     * @param meterRegistry Micrometer 注册器（可为 null）
     */
    public SeataHealthIndicator(SeataProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 健康检查逻辑
     *
     * @return 健康状态
     */
    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.unknown().withDetail("seata", "disabled").build();
        }

        return Health.up()
            .withDetail("seata", "enabled")
            .withDetail("applicationId", properties.getApplicationId())
            .withDetail("txServiceGroup", properties.getTxServiceGroup())
            .withDetail("dataSourceProxyMode", properties.getDataSourceProxyMode())
            .withDetail("globalTransactionTimeout", properties.getTm().getGlobalTransactionTimeout() + "ms")
            .build();
    }
}
