package com.njydsz.common.jdbc.config;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import com.njydsz.common.jdbc.health.DataSourceHealthIndicator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.MeterRegistry;

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
 *       initialization-fail-timeout: 0
 * }</pre>
 *
 * <p><b>惰性初始化：</b>
 * 默认 {@code initialization-fail-timeout=0} 表示启动时不等待连接池初始化完成，
 * 连接在首次请求时按需创建，避免同步预热阻塞启动。
 * 业务方如需预热行为，可设为 {@code -1}（阻塞直至就绪）或正值（等待指定毫秒）。
 *
 * @author ydsz-team
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

    private final DataSourceProperties dataSourceProperties;

    public HikariCPConfiguration(ObjectProvider<DataSourceProperties> dataSourcePropertiesProvider) {
        this.dataSourceProperties = dataSourcePropertiesProvider.getIfAvailable();
    }

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

        // 从 Spring Boot DataSourceProperties 注入连接信息（spring.datasource.*）
        if (dataSourceProperties != null) {
            if (dataSourceProperties.getUrl() != null) {
                config.setJdbcUrl(dataSourceProperties.getUrl());
            }
            if (dataSourceProperties.getUsername() != null) {
                config.setUsername(dataSourceProperties.getUsername());
            }
            if (dataSourceProperties.getPassword() != null) {
                config.setPassword(dataSourceProperties.getPassword());
            }
            if (dataSourceProperties.getDriverClassName() != null) {
                config.setDriverClassName(dataSourceProperties.getDriverClassName());
            }
        }

        config.setMinimumIdle(properties.getMinimumIdle());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setConnectionTimeout(properties.getConnectionTimeout());
        config.setIdleTimeout(properties.getIdleTimeout());
        config.setMaxLifetime(properties.getMaxLifetime());
        config.setLeakDetectionThreshold(properties.getEffectiveLeakDetectionThreshold());
        config.setKeepaliveTime(properties.getKeepaliveTime());
        config.setValidationTimeout(properties.getValidationTimeout());
        config.setPoolName(properties.getPoolName());
        config.setRegisterMbeans(properties.isRegisterMbeans());
        config.setInitializationFailTimeout(properties.getInitializationFailTimeout());

        String testQuery = properties.getConnectionTestQuery();
        if (testQuery == null || testQuery.isEmpty()) {
            String jdbcUrl = config.getJdbcUrl() != null ? config.getJdbcUrl() : "";
            config.setConnectionTestQuery(properties.resolveConnectionTestQuery(jdbcUrl));
        } else {
            config.setConnectionTestQuery(testQuery);
        }

        log.info("HikariCP 连接池配置完成 | 池名: {} | 最小空闲: {} | 最大连接: {} | 连接超时: {}ms | 空闲超时: {}ms | 最大生命周期: {}ms | 泄漏检测: {}ms | 初始化超时: {}ms",
                config.getPoolName(),
                config.getMinimumIdle(),
                config.getMaximumPoolSize(),
                config.getConnectionTimeout(),
                config.getIdleTimeout(),
                config.getMaxLifetime(),
                config.getLeakDetectionThreshold(),
                config.getInitializationFailTimeout());

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

        // 采用惰性初始化策略：不阻塞启动预热，首批请求按需创建连接
        // initializationFailTimeout=0 确保启动不等待连接池就绪
        if (hikariConfig.getInitializationFailTimeout() == 0) {
            log.info("HikariCP 启用惰性初始化模式，连接将在首次请求时按需创建");
        }

        return dataSource;
    }

    /**
     * 多数据源场景下的 HikariCP 连接池定制
     *
     * <p>当项目中使用了本模块的 {@link DynamicRoutingDataSource} 时，
     * 此 {@link SmartLifecycle} Bean 会在所有数据源初始化完成后，遍历
     * {@link DynamicRoutingDataSource} 中的所有目标数据源，对每个 {@link HikariDataSource}
     * 执行连接池参数定制。
     *
     * <p><b>定制流程：</b>
     * <ol>
     *   <li>从 {@link HikariDataSource#getHikariConfigMXBean()} 读取当前配置快照</li>
     *   <li>调用所有 {@link HikariCPPoolConfigurer} 实现（如有）</li>
     *   <li>将修改后的配置通过 {@link HikariConfigMXBean} 热更新到运行中的连接池</li>
     * </ol>
     *
     * <p>对于非 {@link HikariDataSource} 类型的数据源，仅记录警告日志，不做修改。
     *
     * <p>业务方可以通过实现 {@link HikariCPPoolConfigurer} 接口来为特定数据源定制连接池参数：
     * <pre>{@code
     * @Bean
     * public HikariCPPoolConfigurer hikariCPPoolConfigurer() {
     *     return (dsName, config) -> {
     *         if ("slave".equals(dsName)) {
     *             config.setMaximumPoolSize(10);
     *         }
     *     };
     * }
     * }</pre>
     *
     * @param dynamicRoutingDataSource 动态路由数据源
     * @param poolConfigurerProvider   连接池定制器列表（可选，无实现时不执行定制）
     * @return SmartLifecycle 实例
     */
    @Bean
    @ConditionalOnClass(DynamicRoutingDataSource.class)
    @ConditionalOnBean(DynamicRoutingDataSource.class)
    public SmartLifecycle multiDataSourceHikariCustomizer(
            DynamicRoutingDataSource dynamicRoutingDataSource,
            ObjectProvider<List<HikariCPPoolConfigurer>> poolConfigurerProvider) {

        return new SmartLifecycle() {

            private volatile boolean running = false;

            @Override
            public void start() {
                Map<Object, DataSource> targetDataSources = dynamicRoutingDataSource.getDataSources();
                if (targetDataSources == null || targetDataSources.isEmpty()) {
                    log.warn("多数据源路由中未找到目标数据源，跳过 HikariCP 连接池定制");
                    this.running = true;
                    return;
                }

                List<HikariCPPoolConfigurer> configurers = poolConfigurerProvider.getIfAvailable();
                if (configurers == null || configurers.isEmpty()) {
                    log.debug("未找到 HikariCPPoolConfigurer 实现，跳过多数据源连接池定制");
                    this.running = true;
                    return;
                }

                int customized = 0;
                int skipped = 0;

                for (Map.Entry<Object, DataSource> entry : targetDataSources.entrySet()) {
                    String dsName = entry.getKey().toString();
                    DataSource ds = entry.getValue();

                    if (ds instanceof HikariDataSource) {
                        applyPoolConfig(dsName, (HikariDataSource) ds, configurers);
                        customized++;
                    } else {
                        log.warn("数据源 [{}] 非 HikariDataSource 类型，跳过连接池定制: {}",
                                dsName, ds.getClass().getName());
                        skipped++;
                    }
                }

                log.info("多数据源 HikariCP 连接池定制完成: 已定制 {} 个，跳过 {} 个", customized, skipped);
                this.running = true;
            }

            @Override
            public void stop() {
                this.running = false;
            }

            @Override
            public boolean isRunning() {
                return this.running;
            }

            @Override
            public int getPhase() {
                // 在大多数生命周期 bean 之后执行，确保所有数据源已初始化
                return Integer.MAX_VALUE - 100;
            }
        };
    }

    /**
     * 为指定 HikariCP 数据源应用连接池配置
     *
     * <p>从 {@link HikariDataSource} 的 {@link HikariConfigMXBean} 读取当前配置快照，
     * 依次调用所有 {@link HikariCPPoolConfigurer}，最后将修改后的配置通过 MXBean 热更新到运行中的连接池。
     *
     * @param dsName       数据源名称
     * @param hikariDs     HikariCP 数据源
     * @param configurers 连接池定制器列表
     */
    private void applyPoolConfig(String dsName, HikariDataSource hikariDs,
                                  List<HikariCPPoolConfigurer> configurers) {
        HikariConfigMXBean mxBean = hikariDs.getHikariConfigMXBean();

        // 创建配置快照供业务定制
        HikariConfig snapshot = new HikariConfig();
        snapshot.setMinimumIdle(mxBean.getMinimumIdle());
        snapshot.setMaximumPoolSize(mxBean.getMaximumPoolSize());
        snapshot.setConnectionTimeout(mxBean.getConnectionTimeout());
        snapshot.setIdleTimeout(mxBean.getIdleTimeout());
        snapshot.setMaxLifetime(mxBean.getMaxLifetime());
        snapshot.setKeepaliveTime(mxBean.getKeepaliveTime());
        snapshot.setValidationTimeout(mxBean.getValidationTimeout());
        snapshot.setLeakDetectionThreshold(mxBean.getLeakDetectionThreshold());
        snapshot.setConnectionTestQuery(mxBean.getConnectionTestQuery());
        snapshot.setPoolName(mxBean.getPoolName());

        // 依次调用所有配置器
        for (HikariCPPoolConfigurer configurer : configurers) {
            try {
                configurer.configure(dsName, snapshot);
            } catch (Exception e) {
                log.warn("HikariCPPoolConfigurer 执行异常，数据源: {}, 错误: {}", dsName, e.getMessage());
            }
        }

        // 将修改后的配置通过 MXBean 热更新到运行中的连接池（仅应用有变化的值）
        if (snapshot.getMinimumIdle() != mxBean.getMinimumIdle()) {
            mxBean.setMinimumIdle(snapshot.getMinimumIdle());
        }
        if (snapshot.getMaximumPoolSize() != mxBean.getMaximumPoolSize()) {
            mxBean.setMaximumPoolSize(snapshot.getMaximumPoolSize());
        }
        if (snapshot.getConnectionTimeout() != mxBean.getConnectionTimeout()) {
            mxBean.setConnectionTimeout(snapshot.getConnectionTimeout());
        }
        if (snapshot.getIdleTimeout() != mxBean.getIdleTimeout()) {
            mxBean.setIdleTimeout(snapshot.getIdleTimeout());
        }
        if (snapshot.getMaxLifetime() != mxBean.getMaxLifetime()) {
            mxBean.setMaxLifetime(snapshot.getMaxLifetime());
        }
        if (snapshot.getKeepaliveTime() != mxBean.getKeepaliveTime()) {
            mxBean.setKeepaliveTime(snapshot.getKeepaliveTime());
        }
        if (snapshot.getValidationTimeout() != mxBean.getValidationTimeout()) {
            mxBean.setValidationTimeout(snapshot.getValidationTimeout());
        }
        if (snapshot.getLeakDetectionThreshold() != mxBean.getLeakDetectionThreshold()) {
            mxBean.setLeakDetectionThreshold(snapshot.getLeakDetectionThreshold());
        }

        log.info("数据源 [{}] HikariCP 连接池已定制: poolName={}, maxPoolSize={}, minIdle={}, connectionTimeout={}ms",
                dsName, mxBean.getPoolName(), mxBean.getMaximumPoolSize(),
                mxBean.getMinimumIdle(), mxBean.getConnectionTimeout());
    }

    /**
     * 注册数据源健康检查指示器
     *
     * <p>当 HikariDataSource 和 HealthIndicator 可用时，自动注册 {@link DataSourceHealthIndicator}。
     * 业务应用可通过覆盖该 Bean 自定义健康检查行为。
     *
     * @param dataSource HikariCP 数据源
     * @return DataSourceHealthIndicator 实例
     */
    @Bean
    @ConditionalOnMissingBean(DataSourceHealthIndicator.class)
    @ConditionalOnClass({HealthIndicator.class, HikariDataSource.class})
    public DataSourceHealthIndicator dataSourceHealthIndicator(DataSource dataSource) {
        return new DataSourceHealthIndicator(dataSource);
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
