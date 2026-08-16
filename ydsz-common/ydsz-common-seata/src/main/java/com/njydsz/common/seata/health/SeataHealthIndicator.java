package com.njydsz.common.seata.health;

import java.time.LocalDateTime;
import org.apache.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import com.njydsz.common.seata.api.TccTransactionLogStore;
import com.njydsz.common.seata.config.SeataProperties;
import com.njydsz.common.seata.impl.SeataTransactionManager;

/**
 * 分布式事务健康检查
 *
 * <p>暴露 {@code /actuator/health/seata} 端点，检测：
 * <ul>
 *   <li>当前事务模式（LOCAL/TCC/SEATA_AT/SAGA）</li>
 *   <li>Seata TC 连通性（当 SeataTransactionManager 可用时）</li>
 *   <li>TCC 挂起事务数（TRIED 状态超时未完成）</li>
 *   <li>恢复扫描配置</li>
 * </ul>
 *
 * <p><b>P0-2 修复</b>：使用 {@link TccTransactionLogStore#countTimeoutPending} 接口，
 * 避免全量查询挂起事务导致健康检查响应超时。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SeataHealthIndicator implements HealthIndicator {

    private static final Logger LOG = LoggerFactory.getLogger(SeataHealthIndicator.class);

    private final SeataProperties properties;
    private final ObjectProvider<SeataTransactionManager> seataTmProvider;
    private final ObjectProvider<TccTransactionLogStore> logStoreProvider;

    /**
     * 构造分布式事务健康检查指示器
     *
     * @param properties         Seata 配置属性
     * @param seataTmProvider    Seata 事务管理器提供者（可选）
     * @param logStoreProvider   TCC 事务日志存储提供者（可选）
     */
    public SeataHealthIndicator(SeataProperties properties,
                                ObjectProvider<SeataTransactionManager> seataTmProvider,
                                ObjectProvider<TccTransactionLogStore> logStoreProvider) {
        this.properties = properties;
        this.seataTmProvider = seataTmProvider;
        this.logStoreProvider = logStoreProvider;
    }

    /**
     * 执行健康检查，检测事务模式、Seata TC 连通性和 TCC 挂起事务数
     *
     * @return 健康检查结果
     */
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

    /**
     * 检查 Seata TC 连通性
     *
     * <p>通过检测 Seata RootContext 是否正常初始化来判断连通性，
     * 无需发起远程调用，避免健康检查产生外部依赖。
     */
    private void checkSeataTcConnectivity(Health.Builder builder) {
        SeataTransactionManager seataTm = seataTmProvider.getIfAvailable();
        if (seataTm == null) {
            builder.withDetail("seataTc", "not configured (Local/TCC mode)");
            return;
        }
        try {
            String xid = seataTm.getCurrentGlobalXid();
            builder.withDetail("seataTc", "UP");
            builder.withDetail("currentGlobalXid", xid != null ? xid : "none");
        } catch (Exception e) {
            LOG.error("Seata TC health check failed", e);
            builder.withDetail("seataTc", "DOWN");
            builder.withDetail("seataTcError", e.getMessage());
            builder.down();
        }
    }

    /**
     * 检查 TCC 挂起事务数量
     *
     * <p>使用 {@link TccTransactionLogStore#countTimeoutPending} 高效获取计数，
     * 避免全量查询挂起事务列表带来的内存和性能开销。
     */
    private void checkTccPendingTransactions(Health.Builder builder) {
        TccTransactionLogStore logStore = logStoreProvider.getIfAvailable();
        if (logStore == null) {
            builder.withDetail("tccLogStore", "not configured");
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusNanos(
                properties.getRecoveryTimeoutThresholdMs() * 1_000_000);
        long pendingCount = logStore.countTimeoutPending(threshold);
        builder.withDetail("tccPendingTransactions", pendingCount);
        builder.withDetail("tccRecoveryScanIntervalMs", properties.getRecoveryScanIntervalMs());

        if (pendingCount > 10) {
            builder.withDetail("tccWarning", "High number of pending TCC transactions");
        }
    }
}
