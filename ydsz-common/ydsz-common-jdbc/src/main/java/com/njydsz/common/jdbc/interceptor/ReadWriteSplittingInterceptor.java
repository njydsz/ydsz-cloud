package com.njydsz.common.jdbc.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import com.njydsz.common.jdbc.config.DataSourceLoadBalanceStrategy;
import com.njydsz.common.jdbc.datasource.DynamicDataSourceContextHolder;
import com.njydsz.common.jdbc.config.RandomLoadBalanceStrategy;
import com.njydsz.common.jdbc.config.ReadWriteSplittingProperties;
import com.njydsz.common.jdbc.config.RoundRobinLoadBalanceStrategy;
import com.njydsz.common.jdbc.config.WeightedLoadBalanceStrategy;
import com.njydsz.common.jdbc.monitor.ReadWriteSplittingMetrics;
import com.njydsz.common.jdbc.monitor.SlaveLatencyMonitor;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

/**
 * 自动读写分离拦截器（MyBatis 外层拦截器）
 *
 * <p>基于 MyBatis {@link Interceptor} 实现，根据 SQL 类型自动路由数据源：
 * <ul>
 *   <li>SELECT → 从库（使用负载均衡策略选择）</li>
 *   <li>INSERT/UPDATE/DELETE → 主库</li>
 * </ul>
 *
 * <p>通过 {@link DynamicDataSourceContextHolder} 设置当前线程数据源路由。
 *
 * <p><b>ThreadLocal 安全保障：</b>使用 try-finally 确保 {@link DynamicDataSourceContextHolder#poll()}
 * 在请求完成后被调用，避免线程池复用场景下的数据源路由状态泄漏。
 *
 * <p>支持的负载均衡策略：
 * <ul>
 *   <li>round-robin — 轮询（默认）</li>
 *   <li>random — 随机</li>
 *   <li>weighted — 权重（需配置 ydsz.jdbc.read-write-splitting.weights）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DynamicDataSourceContextHolder
 * @see DataSourceLoadBalanceStrategy
 */
@Slf4j
@Intercepts({
    @Signature(type = Executor.class, method = "query",
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query",
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
    @Signature(type = Executor.class, method = "update",
               args = {MappedStatement.class, Object.class})
})
public class ReadWriteSplittingInterceptor implements Interceptor {

    private final ReadWriteSplittingProperties properties;
    private final DataSourceLoadBalanceStrategy loadBalanceStrategy;
    private final ReadWriteSplittingMetrics metrics;
    private final SlaveLatencyMonitor latencyMonitor;

    /**
     * 构造自动读写分离拦截器
     *
     * @param properties     读写分离配置
     * @param metrics        读写分离监控指标（可为 null，Micrometer 不可用时降级）
     * @param latencyMonitor 从库延迟监控（可为 null，未配置延迟检测时降级）
     */
    public ReadWriteSplittingInterceptor(ReadWriteSplittingProperties properties,
                                          ReadWriteSplittingMetrics metrics,
                                          SlaveLatencyMonitor latencyMonitor) {
        this.properties = properties;
        this.loadBalanceStrategy = createLoadBalanceStrategy(properties);
        this.metrics = metrics;
        this.latencyMonitor = latencyMonitor;
        log.info("ReadWriteSplitting interceptor enabled: master={}, {}, strategy={}, latencyCheck={}",
                properties.getMasterDs(), properties.getSlaveDsList(),
                properties.getLoadBalanceStrategy(),
                properties.getLatencyCheck().isEnabled());
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        SqlCommandType commandType = ms.getSqlCommandType();

        long startTime = System.nanoTime();
        String targetDs = resolveTargetDataSource(commandType);
        if (targetDs != null) {
            DynamicDataSourceContextHolder.push(targetDs);
            log.debug("ReadWriteSplitting: SQL type={}, routed to datasource={}", commandType, targetDs);
        }
        try {
            return invocation.proceed();
        } finally {
            if (targetDs != null) {
                DynamicDataSourceContextHolder.poll();
            }
            if (metrics != null) {
                metrics.recordSqlExecutionTime(targetDs, commandType.name(), System.nanoTime() - startTime);
            }
        }
    }

    /**
     * 根据 SQL 类型解析目标数据源
     * <p>事务激活时强制路由到主库，保证读写一致性。
     *
     * @param commandType SQL 命令类型
     * @return 数据源名称
     */
    private String resolveTargetDataSource(SqlCommandType commandType) {
        String sqlTypeName = commandType.name();
        // 事务感知：@Transactional 中 SELECT 必须读主库，避免读写不一致
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("ReadWriteSplitting: Transaction active, routing to master for {}", commandType);
            if (metrics != null) {
                metrics.recordRoute(properties.getMasterDs(), sqlTypeName, "transaction_forced");
                metrics.recordTransactionForced(sqlTypeName);
            }
            return properties.getMasterDs();
        }
        if (commandType == SqlCommandType.SELECT) {
            List<String> slaves = properties.getSlaveDsList();
            if (slaves == null || slaves.isEmpty()) {
                if (metrics != null) {
                    metrics.recordRoute(properties.getMasterDs(), sqlTypeName, "master_no_slaves");
                }
                return properties.getMasterDs();
            }
            // 过滤掉延迟超标的从库（如果启用了延迟检测）
            List<String> healthySlaves = filterHealthySlaves(slaves);
            if (healthySlaves.isEmpty()) {
                // 所有从库都延迟超标，降级走主库
                if (metrics != null) {
                    metrics.recordRoute(properties.getMasterDs(), sqlTypeName, "master_latency_fallback");
                }
                log.debug("ReadWriteSplitting: all slaves latency exceeded, fallback to master");
                return properties.getMasterDs();
            }
            if (healthySlaves.size() == 1) {
                if (metrics != null) {
                    metrics.recordRoute(healthySlaves.get(0), sqlTypeName, "slave_single");
                }
                return healthySlaves.get(0);
            }
            String selectedSlave = loadBalanceStrategy.select(healthySlaves);
            if (metrics != null) {
                metrics.recordRoute(selectedSlave, sqlTypeName, "slave");
            }
            return selectedSlave;
        }
        // INSERT/UPDATE/DELETE/UNKNOWN → 主库
        if (metrics != null) {
            metrics.recordRoute(properties.getMasterDs(), sqlTypeName, "master");
        }
        return properties.getMasterDs();
    }

    /**
     * 获取健康的从库列表，用于路由决策。
     *
     * <p>如果未配置延迟检测或检测器不可用，返回原始列表（向后兼容）。
     * 如果启用了延迟检测，返回 {@link SlaveLatencyMonitor#getHealthySlaves()} 缓存快照，
     * 避免每次请求重复遍历和过滤。
     *
     * @param slaves 原始从库列表
     * @return 健康的从库列表（可能为空）
     */
    private List<String> filterHealthySlaves(List<String> slaves) {
        if (latencyMonitor == null || !properties.getLatencyCheck().isEnabled()) {
            return slaves;
        }
        Set<String> healthySet = latencyMonitor.getHealthySlaves();
        if (healthySet == null || healthySet.isEmpty()) {
            return Collections.emptyList();
        }
        // 转换为 List 以支持 loadBalanceStrategy.select(List)
        return new ArrayList<>(healthySet);
    }

    /**
     * 根据配置创建负载均衡策略实例
     */
    private DataSourceLoadBalanceStrategy createLoadBalanceStrategy(ReadWriteSplittingProperties props) {
        String strategyName = props.getLoadBalanceStrategy();
        if (strategyName == null) {
            return new RoundRobinLoadBalanceStrategy();
        }
        return switch (strategyName.toLowerCase()) {
            case "random" -> new RandomLoadBalanceStrategy();
            case "weighted" -> new WeightedLoadBalanceStrategy(props.getWeights());
            default -> new RoundRobinLoadBalanceStrategy();
        };
    }
}
