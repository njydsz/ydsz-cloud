package com.njydsz.common.jdbc.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.njydsz.common.jdbc.monitor.SlaveLatencyMonitor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 从库延迟监控自动配置
 *
 * <p>当满足以下条件时启用延迟监控：
 * <ul>
 *   <li>{@code ydsz.jdbc.read-write-splitting.latency-check.enabled=true}</li>
 *   <li>Spring 容器中存在 baomidou {@code DynamicRoutingDataSource}</li>
 *   <li>从库列表非空</li>
 * </ul>
 *
 * <p>延迟监控器后台周期性检测各从库复制延迟，延迟超标时自动摘除，
 * 由 {@code ReadWriteSplittingInterceptor} 在路由时检查健康状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SlaveLatencyMonitor
 * @see ReadWriteSplittingInterceptor
 */
@AutoConfiguration(after = ReadWriteSplittingAutoConfiguration.class)
@EnableConfigurationProperties(ReadWriteSplittingProperties.class)
@ConditionalOnClass(name = "com.baomidou.dynamic.datasource.DynamicRoutingDataSource")
@ConditionalOnProperty(prefix = "ydsz.jdbc.read-write-splitting.latency-check",
        name = "enabled", havingValue = "true")
public class SlaveLatencyAutoConfiguration {

    /**
     * 创建从库延迟监控器
     *
     * @param properties    读写分离配置
     * @param routingDataSource baomidou 动态路由数据源
     * @return 延迟监控器（如果无法获取从库数据源则返回 null）
     */
    @Bean
    @ConditionalOnMissingBean
    public SlaveLatencyMonitor slaveLatencyMonitor(ReadWriteSplittingProperties properties,
                                                    ObjectProvider<com.baomidou.dynamic.datasource.DynamicRoutingDataSource> routingDataSource) {
        Map<String, DataSource> slaveDataSources = resolveSlaveDataSources(properties, routingDataSource.getIfAvailable());
        if (slaveDataSources.isEmpty()) {
            return null;
        }
        SlaveLatencyMonitor monitor = new SlaveLatencyMonitor(slaveDataSources, properties.getLatencyCheck());
        monitor.start();
        return monitor;
    }

    /**
     * 从 DynamicRoutingDataSource 中提取从库数据源
     */
    private Map<String, DataSource> resolveSlaveDataSources(ReadWriteSplittingProperties properties,
                                                             com.baomidou.dynamic.datasource.DynamicRoutingDataSource routingDataSource) {
        if (routingDataSource == null) {
            return Collections.emptyMap();
        }
        Map<String, DataSource> result = new HashMap<>();
        Map<Object, ?> targetDataSources = routingDataSource.getTargetDataSources();
        for (String slaveName : properties.getSlaveDsList()) {
            Object ds = targetDataSources.get(slaveName);
            if (ds instanceof DataSource dataSource) {
                result.put(slaveName, dataSource);
            }
        }
        return result;
    }
}
