package com.njydsz.common.jdbc.config;

import com.zaxxer.hikari.HikariConfig;

/**
 * HikariCP 连接池参数定制器（函数式接口）
 *
 * <p>业务方可实现此接口，为特定数据源名称定制 HikariCP 连接池参数。
 * 当项目使用 baomidou 多数据源（dynamic-datasource-spring-boot3-starter）时，
 * 此配置器会在每个数据源初始化完成后被 {@link HikariCPConfiguration} 调用。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * @Configuration
 * public class DataSourcePoolConfig {
 *
 *     @Bean
 *     public HikariCPPoolConfigurer hikariCPPoolConfigurer() {
 *         return (dsName, config) -> {
 *             if ("slave".equals(dsName)) {
 *                 // 从库使用更小的连接池
 *                 config.setMaximumPoolSize(10);
 *                 config.setMinimumIdle(2);
 *                 config.setKeepaliveTime(180000);
 *             }
 *             if ("report".equals(dsName)) {
 *                 // 报表库单独设置超时
 *                 config.setConnectionTimeout(60000);
 *                 config.setMaxLifetime(3600000);
 *             }
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>此配置器在数据源已创建后调用，修改的参数通过 {@code HikariConfigMXBean} 热更新到运行中的连接池</li>
 *   <li>{@code jdbcUrl}、{@code username}、{@code password} 等连接参数在数据源创建时已固定，修改无效</li>
 *   <li>建议仅修改连接池行为参数：{@code maximumPoolSize}、{@code minimumIdle}、{@code connectionTimeout} 等</li>
 *   <li>支持多个实现，Spring 容器中所有 {@link HikariCPPoolConfigurer} Bean 会按顺序依次调用</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see HikariCPConfiguration
 * @see com.baomidou.dynamic.datasource.DynamicRoutingDataSource
 */
@FunctionalInterface
public interface HikariCPPoolConfigurer {

    /**
     * 定制指定数据源的 HikariCP 连接池配置
     *
     * @param dataSourceName 数据源名称（如 "master"、"slave1"、"report"）
     * @param config         HikariCP 配置对象，包含当前数据源的连接池参数快照；
     *                       修改此对象的属性即可定制连接池行为
     */
    void configure(String dataSourceName, HikariConfig config);
}
