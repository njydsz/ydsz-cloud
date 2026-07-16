package com.njydsz.pmis.common.seata.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.pmis.common.seata.api.TccTransactionLogStore;
import com.njydsz.pmis.common.seata.config.SeataProperties;
import com.njydsz.pmis.common.seata.impl.GlobalTransactionExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * 分布式事务健康检查
 *
 * <p>暴露 {@code /actuator/health/seata} 端点，检测：
 * <ul>
 *   <li>当前事务模式（LOCAL/TCC/SEATA_AT/SAGA）</li>
 *   <li>Seata TC 连通性（当 GlobalTransactionExecutor 可用时）</li>
 *   <li>TCC 挂起事务数（TRIED 状态超时未完成）</li>
 *   <li>恢复扫描配置</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class SeataHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SeataHealthIndicator.class);

    private final SeataProperties properties;
    private final ObjectProvider<GlobalTransactionExecutor> globalExecutorProvider;
    private final ObjectProvider<TccTransactionLogStore> logStoreProvider;

    public SeataHealthIndicator(SeataProperties properties,
                                ObjectProvider<GlobalTransactionExecutor> globalExecutorProvider,
                                ObjectProvider<TccTransactionLogStore> logStoreProvider) {
        this.properties = properties;
        this.globalExecutorProvider = globalExecutorProvider;
        this.logStoreProvider = logStoreProvider;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        builder.withDetail("defaultType", properties.getDefaultType().name());
        builder.withDetail("tccEnabled", properties.isTccEnabled());
        builder.withDetail("sagaEnabled", properties.isSagaEnabled());
        builder.withDetail("seataAtEnabled", properties.isSeataAtEnabled());

        checkSeataTcConnectivity(builder);
        checkTccPendingTransactions(builder);

        return builder.build();
    }

    private void checkSeataTcConnectivity(Health.Builder builder) {
        GlobalTransactionExecutor executor = globalExecutorProvider.getIfAvailable();
        if (executor == null) {
            builder.withDetail("seataTc", "not configured (Local mode)");
            return;
        }
        try {
            String xid = executor.getCurrentGlobalXid();
            builder.withDetail("seataTc", "UP");
            builder.withDetail("currentGlobalXid", xid != null ? xid : "none");
        } catch (Exception e) {
            log.error("Seata TC health check failed", e);
            builder.withDetail("seataTc", "DOWN");
            builder.withDetail("seataTcError", e.getMessage());
            builder.down();
        }
    }

    private void checkTccPendingTransactions(Health.Builder builder) {
        TccTransactionLogStore logStore = logStoreProvider.getIfAvailable();
        if (logStore == null) {
            builder.withDetail("tccLogStore", "not configured");
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusNanos(
                properties.getRecoveryTimeoutThresholdMs() * 1_000_000);
        int pendingCount = logStore.findTimeoutPending(threshold).size();
        builder.withDetail("tccPendingTransactions", pendingCount);
        builder.withDetail("tccRecoveryScanIntervalMs", properties.getRecoveryScanIntervalMs());

        if (pendingCount > 10) {
            builder.withDetail("tccWarning", "High number of pending TCC transactions");
        }
    }
}
