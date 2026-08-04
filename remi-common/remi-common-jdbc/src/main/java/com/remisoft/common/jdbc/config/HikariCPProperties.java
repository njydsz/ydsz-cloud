package com.remisoft.common.jdbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * HikariCP 连接池配置属性类
 *
 * <p>用于外部化配置 HikariCP 连接池参数，绑定 application.yml 中的
 * {@code remi.jdbc.hikari} 配置前缀。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * remi:
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
 * <p><b>默认值说明：</b> 所有默认值均参考互联网大厂连接池最佳实践。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.jdbc.hikari")
public class HikariCPProperties {

    /**
     * 最小空闲连接数
     * 默认值：5
     */
    private int minimumIdle = 5;

    /**
     * 最大连接池大小
     * 默认值：20
     */
    private int maximumPoolSize = 20;

    /**
     * 连接超时时间（毫秒）
     * 默认值：30000（30 秒）
     */
    private long connectionTimeout = 30000L;

    /**
     * 空闲连接超时时间（毫秒）
     * 默认值：600000（10 分钟）
     */
    private long idleTimeout = 600000L;

    /**
     * 连接最大生命周期（毫秒）
     * 默认值：1800000（30 分钟）
     */
    private long maxLifetime = 1800000L;

    /**
     * 连接泄漏检测阈值（毫秒）
     * 设为 0 表示禁用。
     * 默认值：30000（30 秒）
     */
    private long leakDetectionThreshold = 30000L;

    /**
     * 连接保活时间（毫秒）
     * HikariCP 会定期验证空闲连接，防止被数据库端超时断开。
     * 应小于 max-lifetime。
     * 默认值：120000（2 分钟）
     */
    private long keepaliveTime = 120000L;

    /**
     * 连接校验超时时间（毫秒）
     * 获取连接时校验的最大等待时间。
     * 默认值：5000（5 秒）
     */
    private long validationTimeout = 5000L;

    /**
     * 连接测试查询语句（可选）
     * 如果未设置，会根据 JDBC URL 自动推断。
     */
    private String connectionTestQuery;

    /**
     * 连接池名称（用于日志和监控）
     */
    private String poolName = "YdszHikariCP-Pool";

    /**
     * 是否注册 MBeans 用于 JMX 监控
     * 默认值：false
     */
    private boolean registerMbeans = false;

    /**
     * 获取有效的连接泄漏检测阈值
     *
     * @return 如果未配置或为 0，返回默认值 30000ms
     */
    public long getEffectiveLeakDetectionThreshold() {
        return leakDetectionThreshold > 0 ? leakDetectionThreshold : 30000L;
    }

    /**
     * 根据 JDBC URL 获取默认的连接测试查询语句
     *
     * @param jdbcUrl JDBC 连接 URL
     * @return 测试查询语句
     */
    public String resolveConnectionTestQuery(String jdbcUrl) {
        if (connectionTestQuery != null && !connectionTestQuery.isEmpty()) {
            return connectionTestQuery;
        }
        if (jdbcUrl == null) {
            return "SELECT 1";
        }
        String url = jdbcUrl.toLowerCase();
        if (url.contains("oracle")) {
            return "SELECT 1 FROM DUAL";
        }
        return "SELECT 1";
    }
}
