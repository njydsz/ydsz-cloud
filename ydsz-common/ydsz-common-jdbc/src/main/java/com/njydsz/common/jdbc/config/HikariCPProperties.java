package com.njydsz.common.jdbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * HikariCP 连接池配置属性类
 *
 * <p>用于外部化配置 HikariCP 连接池参数，绑定 application.yml 中的
 * {@code ydsz.jdbc.hikari} 配置前缀。
 *
 * <p><b>配置示例：</b>
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
 * <p><b>默认值说明：</b> 所有默认值均参考互联网大厂连接池最佳实践。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.jdbc.hikari")
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
     * 连接池初始化失败超时时间（毫秒）
     *
     * <p>控制 HikariCP 启动时等待连接池初始化的行为：
     * <ul>
     *   <li>{@code 0} - 不等待连接池初始化完成，立即返回（惰性初始化，推荐）</li>
     *   <li>{@code -1} (默认) - 若初始化失败则抛出异常，阻塞启动直至超时</li>
     *   <li>{@code >0} - 等待指定毫秒数后若仍未就绪则抛出异常</li>
     * </ul>
     *
     * <p>设为 {@code 0} 可实现惰性初始化，首批请求按需创建连接，避免启动阻塞。
     * 默认值：0（惰性初始化，不阻塞启动）
     */
    private long initializationFailTimeout = 0L;

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
     * <p>MySQL / PostgreSQL 等主流数据库均支持 {@code SELECT 1}。
     *
     * @param jdbcUrl JDBC 连接 URL
     * @return 测试查询语句
     */
    public String resolveConnectionTestQuery(String jdbcUrl) {
        if (connectionTestQuery != null && !connectionTestQuery.isEmpty()) {
            return connectionTestQuery;
        }
        // MySQL / PostgreSQL / H2 等均支持 SELECT 1
        return "SELECT 1";
    }
}
