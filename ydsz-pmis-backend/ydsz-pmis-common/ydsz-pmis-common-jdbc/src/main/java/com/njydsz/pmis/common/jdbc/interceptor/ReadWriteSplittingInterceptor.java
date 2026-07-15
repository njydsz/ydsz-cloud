package com.njydsz.pmis.common.jdbc.interceptor;

import java.sql.Connection;
import java.util.List;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.pmis.common.jdbc.config.DataSourceLoadBalanceStrategy;
import com.njydsz.pmis.common.jdbc.config.RandomLoadBalanceStrategy;
import com.njydsz.pmis.common.jdbc.config.ReadWriteSplittingProperties;
import com.njydsz.pmis.common.jdbc.config.RoundRobinLoadBalanceStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * 自动读写分离拦截器
 *
 * <p>基于 MyBatis-Plus {@link InnerInterceptor} 实现，根据 SQL 类型自动路由数据源：
 * <ul>
 *   <li>SELECT → 从库（使用负载均衡策略选择）</li>
 *   <li>INSERT/UPDATE/DELETE → 主库</li>
 * </ul>
 *
 * <p>需配合 baomidou dynamic-datasource 使用，通过 {@link DynamicDataSourceContextHolder}
 * 设置当前线程的数据源。
 *
 * <p>当从库列表为空或只有一个时，直接使用该从库，不做负载均衡。
 * 当未配置从库时，所有请求路由到主库。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 * @see DynamicDataSourceContextHolder
 * @see DataSourceLoadBalanceStrategy
 */
@Slf4j
public class ReadWriteSplittingInterceptor implements InnerInterceptor {

    private final ReadWriteSplittingProperties properties;
    private final DataSourceLoadBalanceStrategy loadBalanceStrategy;

    /**
     * 构造自动读写分离拦截器
     *
     * @param properties 读写分离配置
     */
    public ReadWriteSplittingInterceptor(ReadWriteSplittingProperties properties) {
        this.properties = properties;
        this.loadBalanceStrategy = createLoadBalanceStrategy(properties.getLoadBalanceStrategy());
        log.info("ReadWriteSplitting interceptor enabled: master={}, slaves={}, strategy={}",
                properties.getMasterDs(), properties.getSlaveDsList(), properties.getLoadBalanceStrategy());
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        SqlCommandType commandType = ms.getSqlCommandType();

        String targetDs = resolveTargetDataSource(commandType);
        if (targetDs != null) {
            DynamicDataSourceContextHolder.push(targetDs);
            log.debug("ReadWriteSplitting: SQL type={}, routed to datasource={}", commandType, targetDs);
        }
    }

    /**
     * 根据 SQL 类型解析目标数据源
     *
     * @param commandType SQL 命令类型
     * @return 数据源名称
     */
    private String resolveTargetDataSource(SqlCommandType commandType) {
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
     * 根据策略名称创建负载均衡策略实例
     */
    private DataSourceLoadBalanceStrategy createLoadBalanceStrategy(String strategyName) {
        if (strategyName == null) {
            return new RoundRobinLoadBalanceStrategy();
        }
        return switch (strategyName.toLowerCase()) {
            case "random" -> new RandomLoadBalanceStrategy();
            default -> new RoundRobinLoadBalanceStrategy();
        };
    }
}
