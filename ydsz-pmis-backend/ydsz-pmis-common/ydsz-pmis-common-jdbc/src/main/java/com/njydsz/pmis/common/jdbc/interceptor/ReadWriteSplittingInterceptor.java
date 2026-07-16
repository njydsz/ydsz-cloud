package com.njydsz.pmis.common.jdbc.interceptor;

import java.util.List;

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

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.njydsz.pmis.common.jdbc.config.DataSourceLoadBalanceStrategy;
import com.njydsz.pmis.common.jdbc.config.RandomLoadBalanceStrategy;
import com.njydsz.pmis.common.jdbc.config.ReadWriteSplittingProperties;
import com.njydsz.pmis.common.jdbc.config.RoundRobinLoadBalanceStrategy;
import com.njydsz.pmis.common.jdbc.config.WeightedLoadBalanceStrategy;

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
 * <p>需配合 baomidou dynamic-datasource 使用，通过 {@link DynamicDataSourceContextHolder}
 * 设置当前线程的数据源。
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
 * @author ydsz-pmis-team
 * @since 1.2.0
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

    /**
     * 构造自动读写分离拦截器
     *
     * @param properties 读写分离配置
     */
    public ReadWriteSplittingInterceptor(ReadWriteSplittingProperties properties) {
        this.properties = properties;
        this.loadBalanceStrategy = createLoadBalanceStrategy(properties);
        log.info("ReadWriteSplitting interceptor enabled: master={}, slaves={}, strategy={}",
                properties.getMasterDs(), properties.getSlaveDsList(), properties.getLoadBalanceStrategy());
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        SqlCommandType commandType = ms.getSqlCommandType();

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
        // 事务感知：@Transactional 中 SELECT 必须读主库，避免读写不一致
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("ReadWriteSplitting: Transaction active, routing to master for {}", commandType);
            return properties.getMasterDs();
        }
        if (commandType == SqlCommandType.SELECT) {
            List<String> slaves = properties.getSlaveDsList();
            if (slaves == null || slaves.isEmpty()) {
                return properties.getMasterDs();
            }
            if (slaves.size() == 1) {
                return slaves.get(0);
            }
            return loadBalanceStrategy.select(slaves);
        }
        // INSERT/UPDATE/DELETE/OTHER → 主库
        return properties.getMasterDs();
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
