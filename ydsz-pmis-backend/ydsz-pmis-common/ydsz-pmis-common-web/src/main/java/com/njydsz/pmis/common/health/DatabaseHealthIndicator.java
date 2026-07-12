package com.njydsz.pmis.common.health;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 数据库健康检查指示器（P1-8 增强）
 *
 * <p>暴露数据库连接池（HikariCP）的健康指标，包括：
 * <ul>
 *   <li>连接可用性检测（SELECT 1）</li>
 *   <li>数据库产品名与版本信息</li>
 *   <li>HikariCP 连接池指标：active / idle / waiting / max / min</li>
 *   <li>等待线程数超过最大连接数的一半时标记为 DEGRADED</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")
public class DatabaseHealthIndicator implements HealthIndicator {

    /** 数据源，用于获取数据库连接执行健康检查 SQL */
    private final DataSource dataSource;

    /**
     * 构造器注入数据源
     *
     * @param dataSource Spring 管理的数据源
     */
    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行数据库健康检查
     *
     * <p>检查步骤：
     * <ol>
     *   <li>执行 {@code SELECT 1} 验证连接可用性</li>
     *   <li>收集数据库产品名与版本信息</li>
     *   <li>若数据源为 HikariDataSource，收集连接池指标</li>
     *   <li>等待线程数过多时标记为 DEGRADED</li>
     * </ol>
     *
     * @return 健康状态对象
     */
    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");

            Health.Builder builder = Health.up()
                    .withDetail("database", conn.getMetaData().getDatabaseProductName())
                    .withDetail("version", conn.getMetaData().getDatabaseProductVersion());

            // 收集 HikariCP 连接池指标
            appendHikariPoolMetrics(builder);

            return builder.build();
        } catch (Exception e) {
            log.warn("[HealthCheck] 数据库健康检查失败: {}", e.getMessage());
            return Health.down(e).build();
        }
    }

    /**
     * 追加 HikariCP 连接池指标
     *
     * @param builder 健康状态构建器
     */
    private void appendHikariPoolMetrics(Health.Builder builder) {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return;
        }
        try {
            var pool = hikari.getHikariPoolMXBean();
            if (pool == null) {
                return;
            }
            int active = pool.getActiveConnections();
            int idle = pool.getIdleConnections();
            int waiting = pool.getThreadsAwaitingConnection();
            int max = hikari.getMaximumPoolSize();
            int min = hikari.getMinimumIdle();

            builder.withDetail("pool_active", active)
                    .withDetail("pool_idle", idle)
                    .withDetail("pool_waiting", waiting)
                    .withDetail("pool_max", max)
                    .withDetail("pool_min", min);

            // 等待线程数超过最大连接数的一半时标记降级
            if (waiting > max / 2) {
                builder.withDetail("status", "DEGRADED");
                builder.withDetail("reason", "high connection wait queue: " + waiting + " threads waiting");
            }
        } catch (Exception e) {
            builder.withDetail("pool_info", "unavailable");
        }
    }
}
