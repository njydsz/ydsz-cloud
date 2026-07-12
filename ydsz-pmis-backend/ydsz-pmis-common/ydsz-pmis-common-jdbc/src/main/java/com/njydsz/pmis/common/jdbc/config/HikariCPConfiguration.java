package com.njydsz.pmis.common.jdbc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * HikariCP 数据源配置类
 *
 * <p>配置 HikariCP 数据库连接池，支持两种配置方式：
 * <ol>
 *   <li>Spring Boot 标准配置：{@code spring.datasource.hikari.*}</li>
 *   <li>项目自定义配置：{@code ydsz.jdbc.hikari.*}（推荐）</li>
 * </ol>
 *
 * <p><b>配置优先级：</b> 若配置了 {@code ydsz.jdbc.hikari.*}，则使用项目配置；
 * 否则使用 Spring Boot 默认配置。
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     hikari:
 *       minimum-idle: 5
 *       maximum-pool-size: 20
 *       connection-timeout: 30000
 *       idle-timeout: 600000
 *       max-lifetime: 1800000
 *       leak-detection-threshold: 30000
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see HikariDataSource
 * @see <a href="https://github.com/brettwooldridge/HikariCP">HikariCP Official</a>
 */
@AutoConfiguration
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "ydsz.jdbc", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties({HikariCPProperties.class, JdbcProperties.class})
public class HikariCPConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HikariCPConfiguration.class);

    /**
     * 创建 HikariCP 配置
     *
     * <p>优先使用 ydsz.comm.jdbc.hikari 配置，若未配置则使用 Spring Boot 默认配置。
     *
     * @param properties 项目自定义的 HikariCP 配置属性
     * @return HikariConfig 实例
     */
    @Bean
    @ConditionalOnMissingBean(HikariConfig.class)
    public HikariConfig hikariConfig(HikariCPProperties properties) {
        HikariConfig config = new HikariConfig();

        config.setMinimumIdle(properties.getMinimumIdle());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setConnectionTimeout(properties.getConnectionTimeout());
        config.setIdleTimeout(properties.getIdleTimeout());
        config.setMaxLifetime(properties.getMaxLifetime());
        config.setLeakDetectionThreshold(properties.getEffectiveLeakDetectionThreshold());
        config.setPoolName(properties.getPoolName());
        config.setRegisterMbeans(properties.isRegisterMbeans());

        String testQuery = properties.getConnectionTestQuery();
        if (testQuery == null || testQuery.isEmpty()) {
            String jdbcUrl = config.getJdbcUrl() != null ? config.getJdbcUrl() : "";
            config.setConnectionTestQuery(properties.resolveConnectionTestQuery(jdbcUrl));
        } else {
            config.setConnectionTestQuery(testQuery);
        }

        log.info("HikariCP 连接池配置完成 | 池名: {} | 最小空闲: {} | 最大连接: {} | 连接超时: {}ms | 空闲超时: {}ms | 最大生命周期: {}ms | 泄漏检测: {}ms",
                config.getPoolName(),
                config.getMinimumIdle(),
                config.getMaximumPoolSize(),
                config.getConnectionTimeout(),
                config.getIdleTimeout(),
                config.getMaxLifetime(),
                config.getLeakDetectionThreshold());

        return config;
    }

    /**
     * 创建 HikariCP 数据源
     *
     * @param hikariConfig      HikariCP 配置
     * @param meterRegistryProvider Micrometer 注册表（可选）
     * @return HikariDataSource 实例
     */
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(HikariConfig hikariConfig,
                                 ObjectProvider<MeterRegistry> meterRegistryProvider) {
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        // 集成 HikariCP 指标到 Micrometer
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            registerHikariMetrics(dataSource, meterRegistry);
        }

        log.info("HikariCP 数据源已初始化，池名: {}", dataSource.getPoolName());
        return dataSource;
    }

    /**
     * 将 HikariCP 连接池指标注册到 Micrometer
     *
     * <p>注册的关键指标：
     * <ul>
     *   <li>{@code hikaricp.connections.active} - 活跃连接数</li>
     *   <li>{@code hikaricp.connections.idle} - 空闲连接数</li>
     *   <li>{@code hikaricp.connections.min} - 最小连接数</li>
     *   <li>{@code hikaricp.connections.max} - 最大连接数</li>
     *   <li>{@code hikaricp.connections.pending} - 等待获取连接的请求数</li>
     *   <li>{@code hikaricp.connections.creation.time} - 连接创建耗时</li>
     *   <li>{@code hikaricp.connections.usage} - 连接使用时长</li>
     *   <li>{@code hikaricp.connections.acquire} - 连接获取耗时</li>
     *   <li>{@code hikaricp.connections.timeout} - 连接超时次数</li>
     * </ul>
     *
     * @param dataSource    HikariCP 数据源
     * @param meterRegistry Micrometer 注册表
     */
    private void registerHikariMetrics(HikariDataSource dataSource, MeterRegistry meterRegistry) {
        String poolName = dataSource.getPoolName();
        dataSource.setMetricRegistry(meterRegistry);
        log.info("HikariCP 指标已注册到 Micrometer，池名: {}", poolName);
    }
}
