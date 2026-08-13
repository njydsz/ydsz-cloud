package com.njydsz.common.jdbc.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import com.njydsz.common.jdbc.monitor.CompositeLatencyDetector;
import com.njydsz.common.jdbc.monitor.SlaveLatencyDetector;
import com.njydsz.common.jdbc.monitor.SlaveLatencyMonitor;

import lombok.extern.slf4j.Slf4j;

/**
 * 从库延迟监控自动配置
 *
 * <p>当满足以下条件时启用延迟监控：
 * <ul>
 *   <li>{@code ydsz.jdbc.read-write-splitting.latency-check.enabled=true}</li>
 *   <li>Spring 容器中存在自研 {@link DynamicRoutingDataSource}</li>
 *   <li>从库列表非空</li>
 * </ul>
 *
 * <p>延迟监控器后台周期性检测各从库复制延迟，延迟超标时自动摘除，
 * 由 {@code ReadWriteSplittingInterceptor} 在路由时检查健康状态。
 *
 * <p>支持 SPI 扩展：发布自定义 {@link SlaveLatencyDetector} Bean 即可自动注册到检测链。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SlaveLatencyMonitor
 * @see SlaveLatencyDetector
 * @see ReadWriteSplittingInterceptor
 */
@Slf4j
@AutoConfiguration(after = ReadWriteSplittingAutoConfiguration.class)
@EnableConfigurationProperties(ReadWriteSplittingProperties.class)
@ConditionalOnClass(DynamicRoutingDataSource.class)
@ConditionalOnProperty(prefix = "ydsz.jdbc.read-write-splitting.latency-check",
        name = "enabled", havingValue = "true")
public class SlaveLatencyAutoConfiguration {

    /**
     * 注册内置 MySQL 延迟检测器
     */
    @Bean
    @ConditionalOnMissingBean(name = "mysqlLatencyDetector")
    public SlaveLatencyDetector mysqlLatencyDetector() {
        return new SlaveLatencyDetector.MysqlLatencyDetector();
    }

    /**
     * 注册内置 PostgreSQL 延迟检测器
     */
    @Bean
    @ConditionalOnMissingBean(name = "postgreSqlLatencyDetector")
    public SlaveLatencyDetector postgreSqlLatencyDetector() {
        return new SlaveLatencyDetector.PostgreSqlLatencyDetector();
    }

    /**
     * 注册内置 Oracle 延迟检测器（1.8.0 新增）
     */
    @Bean
    @ConditionalOnMissingBean(name = "oracleLatencyDetector")
    public SlaveLatencyDetector oracleLatencyDetector() {
        return new SlaveLatencyDetector.OracleLatencyDetector();
    }

    /**
     * 组合所有 SlaveLatencyDetector 实现（含 SPI 扩展）
     *
     * <p>通过 ObjectProvider 获取所有探测器并按优先级排序。
     *
     * @param detectorProvider 所有 SlaveLatencyDetector 实现的提供者
     * @return 组合探测器
     */
    @Bean
    @ConditionalOnMissingBean(CompositeLatencyDetector.class)
    public CompositeLatencyDetector compositeLatencyDetector(
            ObjectProvider<SlaveLatencyDetector> detectorProvider) {
        List<SlaveLatencyDetector> detectors = new ArrayList<>(detectorProvider.orderedStream().toList());
        detectors.sort(Comparator.comparingInt(SlaveLatencyDetector::getOrder));
        log.info("组合延迟检测器已构建，共 {} 个探测器", detectors.size());
        return new CompositeLatencyDetector(detectors);
    }

    /**
     * 创建从库延迟监控器
     *
     * @param detector           组合延迟探测器
     * @param properties         读写分离配置
     * @param routingDataSource  动态路由数据源
     * @return 延迟监控器（如果无法获取从库数据源则返回 null）
     */
    @Bean
    @ConditionalOnMissingBean
    public SlaveLatencyMonitor slaveLatencyMonitor(CompositeLatencyDetector detector,
                                                    ReadWriteSplittingProperties properties,
                                                    ObjectProvider<DynamicRoutingDataSource> routingDataSource) {
        Map<String, DataSource> slaveDataSources = resolveSlaveDataSources(properties,
                routingDataSource.getIfAvailable());
        if (slaveDataSources.isEmpty()) {
            return null;
        }
        SlaveLatencyMonitor monitor = new SlaveLatencyMonitor(detector, slaveDataSources,
                properties.getLatencyCheck());
        monitor.start();
        log.info("从库延迟监控器已启动: slaves={}, interval={}, threshold={}",
                slaveDataSources.keySet(),
                properties.getLatencyCheck().getInterval(),
                properties.getLatencyCheck().getThreshold());
        return monitor;
    }

    /**
     * 从动态路由数据源中提取从库数据源
     *
     * @param properties        读写分离配置
     * @param routingDataSource 动态路由数据源
     * @return 从库名称到数据源的映射
     */
    private Map<String, DataSource> resolveSlaveDataSources(ReadWriteSplittingProperties properties,
                                                             DynamicRoutingDataSource routingDataSource) {
        if (routingDataSource == null) {
            return Collections.emptyMap();
        }
        Map<String, DataSource> result = new HashMap<>(properties.getSlaveDsList().size());
        Map<Object, DataSource> targetDataSources = routingDataSource.getDataSources();
        for (String slaveName : properties.getSlaveDsList()) {
            DataSource dataSource = targetDataSources.get(slaveName);
            if (dataSource != null) {
                result.put(slaveName, dataSource);
            }
        }
        return result;
    }
}
