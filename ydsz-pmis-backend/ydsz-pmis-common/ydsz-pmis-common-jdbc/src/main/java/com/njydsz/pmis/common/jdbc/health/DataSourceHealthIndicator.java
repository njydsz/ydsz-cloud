package com.njydsz.pmis.common.jdbc.health;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 数据源健康检查指示器
 *
 * <p>暴露 HikariCP 连接池的健康指标，包括：
 * <ul>
 *   <li>活跃连接数（active）</li>
 *   <li>空闲连接数（idle）</li>
 *   <li>等待连接线程数（waiting）</li>
 *   <li>最大连接池大小（max）</li>
 *   <li>最小空闲连接数（min）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Component
@ConditionalOnClass({HealthIndicator.class, HikariDataSource.class})
@ConditionalOnProperty(prefix = "remi.jdbc", name = "enabled", matchIfMissing = true)
@SuppressWarnings("resource")
public class DataSourceHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DataSourceHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            return Health.unknown().build();
        }
        try {
            HikariDataSource pool = hikariDataSource;
            int active = pool.getHikariPoolMXBean().getActiveConnections();
            int idle = pool.getHikariPoolMXBean().getIdleConnections();
            int threadsAwaitingConnection = pool.getHikariPoolMXBean().getThreadsAwaitingConnection();
            int max = pool.getMaximumPoolSize();
            int min = pool.getMinimumIdle();

            Health.Builder builder = Health.up()
                    .withDetail("active", active)
                    .withDetail("idle", idle)
                    .withDetail("waiting", threadsAwaitingConnection)
                    .withDetail("max", max)
                    .withDetail("min", min);

            // 如果等待线程数过多，标记为降级
            if (threadsAwaitingConnection > max / 2) {
                builder.withDetail("status", "DEGRADED")
                        .withDetail("reason", "High connection wait queue");
                return builder.build();
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
